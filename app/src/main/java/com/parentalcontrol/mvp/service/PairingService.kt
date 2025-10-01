package com.parentalcontrol.mvp.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.parentalcontrol.mvp.model.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

class PairingService(private val context: Context) {
    
    companion object {
        private const val TAG = "PairingService"
        private const val PREFS_NAME = "pairing_prefs"
        private const val KEY_PAIRING_DATA = "pairing_data"
        private const val KEY_PAIRING_STATUS = "pairing_status"
        private const val PAIRING_TIMEOUT = 10000L // 10 seconds (reduced from 30s)
        private const val HEARTBEAT_INTERVAL = 5000L // 5 seconds (reduced from 10s) 
        private const val CONNECTION_TIMEOUT = 3000L // 3 seconds (reduced from 5s)
        private const val READ_TIMEOUT = 2000L // 2 seconds for faster reads
        private const val WRITE_TIMEOUT = 2000L // 2 seconds for faster writes
        
        // Connection pooling constants
        private const val MAX_CONNECTIONS = 5
        private const val KEEP_ALIVE_DURATION = 30000L // 30 seconds
        private const val MAX_IDLE_CONNECTIONS = 3
        
        // Retry logic constants
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_BASE = 1000L // 1 second
        private const val RETRY_DELAY_MULTIPLIER = 2.0 // Exponential backoff
        private const val RECONNECTION_DELAY = 5000L // 5 seconds
        private const val MAX_RECONNECTION_ATTEMPTS = 5
        
        // Circuit breaker constants
        private const val CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5
        private const val CIRCUIT_BREAKER_RECOVERY_TIMEOUT = 30000L // 30 seconds
        private const val CIRCUIT_BREAKER_SUCCESS_THRESHOLD = 3
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var currentPairingData: PairingData? = null
    private var pairingStatus = PairingStatus()
    
    // Enhanced OkHttpClient with connection pooling and optimized timeouts
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
        .connectionPool(okhttp3.ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION, TimeUnit.MILLISECONDS))
        .retryOnConnectionFailure(true) // Enable automatic retry on connection failures
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    private var heartbeatJob: Job? = null
    private var serverJob: Job? = null
    
    // Retry logic and reconnection state
    private val retryAttempts = AtomicInteger(0)
    private val reconnectionAttempts = AtomicInteger(0)
    private val isReconnecting = AtomicBoolean(false)
    private val isConnectionHealthy = AtomicBoolean(false)
    private var reconnectionJob: Job? = null
    
    // Circuit breaker state for enhanced error handling
    private val circuitBreakerFailures = AtomicInteger(0)
    private val circuitBreakerSuccesses = AtomicInteger(0)
    private val isCircuitBreakerOpen = AtomicBoolean(false)
    private var circuitBreakerOpenTime = 0L
    private val lastKnownErrors = mutableListOf<Pair<Long, String>>() // timestamp + error message
    
    init {
        loadPairingData()
    }
    
    /**
     * Rozpoczyna proces parowania z wykrytym urządzeniem
     */
    fun startPairing(
        remotePairingData: PairingData, 
        callback: (success: Boolean, message: String?) -> Unit
    ) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting pairing with device: ${remotePairingData.deviceName}")
                
                // Rozpocznij nasłuchiwanie na własnym porcie
                startServer()
                
                // Spróbuj połączyć się z urządzeniem zdalnym
                val success = connectToRemoteDevice(remotePairingData)
                
                if (success) {
                    // Zapisz dane parowania
                    currentPairingData = remotePairingData
                    savePairingData(remotePairingData)
                    updatePairingStatus(true, remotePairingData)
                    
                    // Rozpocznij heartbeat
                    startHeartbeat()
                    
                    callback(true, "Pairing successful")
                    Log.d(TAG, "Pairing completed successfully")
                } else {
                    callback(false, "Failed to connect to remote device")
                    Log.e(TAG, "Failed to connect to remote device")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during pairing", e)
                callback(false, "Pairing error: ${e.message}")
            }
        }
    }
    
    /**
     * Rozpoczyna nasłuchiwanie na serwerze dla połączeń przychodzących
     */
    private suspend fun startServer() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== STARTING HTTP SERVER ===")
            
            // Zamknij poprzedni serwer jeśli istnieje
            serverSocket?.let { socket ->
                if (!socket.isClosed) {
                    Log.d(TAG, "Closing previous server socket")
                    socket.close()
                }
            }
            
            // Sprawdź dostępność portu
            val port = 8080
            Log.d(TAG, "Attempting to bind to port: $port")
            
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "✅ Successfully bound to port $port")
                Log.d(TAG, "Server socket info:")
                Log.d(TAG, "  - Local address: ${serverSocket?.localSocketAddress}")
                Log.d(TAG, "  - Local port: ${serverSocket?.localPort}")
                Log.d(TAG, "  - SO_TIMEOUT: ${serverSocket?.soTimeout}")
                Log.d(TAG, "  - Reuse address: ${serverSocket?.reuseAddress}")
            } catch (e: java.net.BindException) {
                Log.e(TAG, "❌ Port $port is already in use!")
                Log.e(TAG, "Possible causes:")
                Log.e(TAG, "- Another instance of PairingService is running")
                Log.e(TAG, "- Another app is using port $port")
                Log.e(TAG, "- Previous server socket not properly closed")
                throw e
            }
            
            serverJob = serviceScope.launch {
                Log.d(TAG, "🚀 HTTP Server accept loop started")
                var connectionCount = 0
                
                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        Log.d(TAG, "⏳ Waiting for incoming connections...")
                        val socket = serverSocket?.accept()
                        
                        if (socket != null) {
                            connectionCount++
                            Log.d(TAG, "📞 New connection #$connectionCount accepted from: ${socket.remoteSocketAddress}")
                            Log.d(TAG, "Connection details:")
                            Log.d(TAG, "  - Remote IP: ${socket.inetAddress.hostAddress}")
                            Log.d(TAG, "  - Remote port: ${socket.port}")
                            Log.d(TAG, "  - Local port: ${socket.localPort}")
                            Log.d(TAG, "  - Keep alive: ${socket.keepAlive}")
                            Log.d(TAG, "  - TCP no delay: ${socket.tcpNoDelay}")
                            
                            // Obsługuj połączenie w osobnej coroutine
                            launch { 
                                Log.d(TAG, "🔄 Launching handler for connection #$connectionCount")
                                handleHttpConnection(socket, connectionCount)
                            }
                        }
                    } catch (e: java.net.SocketException) {
                        if (isActive && serverSocket?.isClosed == false) {
                            Log.e(TAG, "❌ Socket error in accept loop", e)
                        } else {
                            Log.d(TAG, "Server socket closed - stopping accept loop")
                        }
                        break
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "❌ Unexpected error accepting connection", e)
                            Log.e(TAG, "Error type: ${e::class.simpleName}")
                            Log.e(TAG, "Error message: ${e.message}")
                        }
                    }
                }
                
                Log.d(TAG, "🛑 HTTP Server accept loop ended (handled $connectionCount connections)")
            }
            
            Log.d(TAG, "✅ HTTP SERVER STARTED SUCCESSFULLY")
            Log.d(TAG, "Listening on: 0.0.0.0:$port")
            Log.d(TAG, "Ready to accept pairing connections...")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR STARTING SERVER")
            Log.e(TAG, "Error type: ${e::class.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            throw e
        }
    }
    
    /**
     * Obsługuje przychodzące połączenia
     */
    private suspend fun handleIncomingConnection(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Incoming connection from: ${socket.remoteSocketAddress}")
            
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            
            // Odczytaj wiadomość
            val buffer = ByteArray(4096)
            val bytesRead = inputStream.read(buffer)
            val message = String(buffer, 0, bytesRead)
            
            Log.d(TAG, "Received message: $message")
            
            // Parsuj wiadomość
            val remoteMessage = gson.fromJson(message, RemoteMessage::class.java)
            
            when (remoteMessage.messageType) {
                MessageType.PAIRING_REQUEST -> {
                    handlePairingRequest(remoteMessage, outputStream)
                }
                MessageType.HEARTBEAT -> {
                    handleHeartbeat(remoteMessage, outputStream)
                }
                MessageType.LOG_DATA -> {
                    handleLogData(remoteMessage)
                }
                else -> {
                    Log.d(TAG, "Unhandled message type: ${remoteMessage.messageType}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming connection", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing socket", e)
            }
        }
    }
    
    /**
     * Obsługuje przychodzące połączenia HTTP
     */
    private suspend fun handleHttpConnection(socket: Socket, connectionId: Int) = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Log.d(TAG, "=== HANDLING HTTP CONNECTION #$connectionId ===")
            Log.d(TAG, "Connection from: ${socket.remoteSocketAddress}")
            Log.d(TAG, "Connection established at: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())}")
            
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
            
            Log.d(TAG, "📖 Reading HTTP request...")
            
            // Odczytaj HTTP request z timeoutem
            socket.soTimeout = 5000 // 5 sekund timeout
            val requestLine = reader.readLine()
            
            if (requestLine == null) {
                Log.e(TAG, "❌ Connection #$connectionId: Request line is null - client disconnected?")
                return@withContext
            }
            
            Log.d(TAG, "📨 HTTP Request Line: '$requestLine'")
            
            // Parsuj metodę i path
            val parts = requestLine.split(" ")
            if (parts.size < 3) {
                Log.e(TAG, "❌ Connection #$connectionId: Invalid HTTP request format")
                Log.e(TAG, "Expected: 'METHOD PATH VERSION', got: '$requestLine'")
                return@withContext
            }
            
            val method = parts[0]
            val path = parts[1] 
            val httpVersion = parts[2]
            
            Log.d(TAG, "🔍 Parsed request:")
            Log.d(TAG, "  - Method: $method")
            Log.d(TAG, "  - Path: $path")
            Log.d(TAG, "  - HTTP Version: $httpVersion")
            
            // Odczytaj headers
            Log.d(TAG, "📋 Reading headers...")
            val headers = mutableMapOf<String, String>()
            var headerCount = 0
            var line: String?
            
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                headerCount++
                val colonIndex = line!!.indexOf(':')
                if (colonIndex > 0) {
                    val key = line!!.substring(0, colonIndex).trim()
                    val value = line!!.substring(colonIndex + 1).trim()
                    headers[key.lowercase()] = value
                    Log.d(TAG, "  Header #$headerCount: '$key' = '$value'")
                } else {
                    Log.w(TAG, "  Malformed header #$headerCount: '$line'")
                }
            }
            
            Log.d(TAG, "📋 Total headers received: $headerCount")
            
            // Odczytaj body dla POST żądań
            var requestBody = ""
            if (method == "POST") {
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                Log.d(TAG, "📦 POST request - Content-Length: $contentLength bytes")
                
                if (contentLength > 0) {
                    if (contentLength > 10240) { // 10KB limit
                        Log.w(TAG, "⚠️ Large request body: $contentLength bytes (limit 10KB)")
                    }
                    
                    try {
                        val bodyBuffer = CharArray(contentLength)
                        val bytesRead = reader.read(bodyBuffer, 0, contentLength)
                        requestBody = String(bodyBuffer, 0, bytesRead)
                        
                        Log.d(TAG, "📦 Request body read: $bytesRead/$contentLength bytes")
                        Log.d(TAG, "📦 Body preview: ${requestBody.take(200)}${if (requestBody.length > 200) "..." else ""}")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error reading request body", e)
                        return@withContext
                    }
                } else {
                    Log.d(TAG, "📦 No request body (Content-Length: 0)")
                }
            }
            
            // Obsłuż endpointy
            Log.d(TAG, "🎯 Routing request...")
            var responseCode = 200
            var responseMessage = "OK"
            
            when {
                path == "/pair" && method == "POST" -> {
                    Log.d(TAG, "🤝 Routing to /pair endpoint")
                    handlePairEndpoint(requestBody, writer, connectionId)
                    responseCode = 200
                    responseMessage = "Pairing handled"
                }
                path == "/message" && method == "POST" -> {
                    Log.d(TAG, "💬 Routing to /message endpoint")
                    handleMessageEndpoint(requestBody, writer, connectionId)
                    responseCode = 200
                    responseMessage = "Message handled"
                }
                path == "/health" && method == "GET" -> {
                    Log.d(TAG, "🏥 Health check endpoint")
                    sendHttpResponse(writer, 200, "OK", "{\"status\": \"healthy\", \"service\": \"PairingService\"}")
                    responseCode = 200
                    responseMessage = "Health check"
                }
                else -> {
                    Log.w(TAG, "❌ Unknown endpoint: $method $path")
                    Log.w(TAG, "Available endpoints:")
                    Log.w(TAG, "  - POST /pair (pairing requests)")
                    Log.w(TAG, "  - POST /message (messages)")
                    Log.w(TAG, "  - GET /health (health check)")
                    sendHttpResponse(writer, 404, "Not Found", "{\"error\": \"Endpoint not found\", \"path\": \"$path\", \"method\": \"$method\"}")
                    responseCode = 404
                    responseMessage = "Not Found"
                }
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Connection #$connectionId completed:")
            Log.d(TAG, "  - Processing time: ${processingTime}ms")
            Log.d(TAG, "  - Response: $responseCode $responseMessage")
            Log.d(TAG, "=== END HTTP CONNECTION #$connectionId ===")
            
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "⏰ Connection #$connectionId timed out after 5 seconds")
            Log.e(TAG, "Client may have disconnected or is not responding")
        } catch (e: java.net.SocketException) {
            Log.e(TAG, "🔌 Connection #$connectionId socket error: ${e.message}")
            Log.e(TAG, "Possible causes: client disconnected, network issue")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "📡 Connection #$connectionId I/O error: ${e.message}")
            Log.e(TAG, "Data transmission issue")
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Unexpected error in connection #$connectionId after ${processingTime}ms")
            Log.e(TAG, "Error type: ${e::class.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
        } finally {
            try {
                Log.d(TAG, "🔒 Closing connection #$connectionId socket...")
                socket.close()
                Log.d(TAG, "✅ Connection #$connectionId socket closed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error closing socket for connection #$connectionId", e)
            }
        }
    }
    
    /**
     * Obsługuje endpoint /pair
     */
    private suspend fun handlePairEndpoint(requestBody: String, writer: PrintWriter, connectionId: Int) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Handling /pair endpoint")
            
            // Parsuj żądanie parowania
            val pairingRequest = gson.fromJson(requestBody, RemoteMessage::class.java)
            val remotePairingData = gson.fromJson(pairingRequest.payload, PairingData::class.java)
            
            Log.d(TAG, "Pairing request from device: ${remotePairingData.deviceName} (${remotePairingData.deviceType})")
            
            // Sprawdź czy parowanie jest dozwolone
            val isAllowed = validatePairingRequest(remotePairingData)
            
            val response = RemoteMessage(
                senderId = getCurrentDeviceId(),
                recipientId = pairingRequest.senderId,
                messageType = MessageType.PAIRING_RESPONSE,
                payload = if (isAllowed) "ACCEPTED" else "REJECTED"
            )
            
            if (isAllowed) {
                // Zapisz dane parowania
                currentPairingData = remotePairingData
                savePairingData(remotePairingData)
                updatePairingStatus(true, remotePairingData)
                startHeartbeat()
                
                Log.d(TAG, "Pairing accepted from ${remotePairingData.deviceName}")
            } else {
                Log.d(TAG, "Pairing rejected from ${remotePairingData.deviceName}")
            }
            
            sendHttpResponse(writer, 200, "OK", gson.toJson(response))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling /pair endpoint", e)
            sendHttpResponse(writer, 500, "Internal Server Error", "{\"error\": \"Failed to process pairing request\"}")
        }
    }
    
    /**
     * Obsługuje endpoint /message
     */
    private suspend fun handleMessageEndpoint(requestBody: String, writer: PrintWriter, connectionId: Int) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Handling /message endpoint")
            
            // Parsuj wiadomość
            val remoteMessage = gson.fromJson(requestBody, RemoteMessage::class.java)
            
            when (remoteMessage.messageType) {
                MessageType.HEARTBEAT -> {
                    Log.d(TAG, "Received heartbeat from: ${remoteMessage.senderId}")
                    
                    // Zaktualizuj status połączenia
                    pairingStatus = pairingStatus.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        lastHeartbeat = System.currentTimeMillis()
                    )
                    savePairingStatus()
                    
                    // Odpowiedz na heartbeat
                    val response = RemoteMessage(
                        senderId = getCurrentDeviceId(),
                        recipientId = remoteMessage.senderId,
                        messageType = MessageType.ACKNOWLEDGMENT,
                        payload = "HEARTBEAT_ACK"
                    )
                    
                    sendHttpResponse(writer, 200, "OK", gson.toJson(response))
                }
                MessageType.LOG_DATA -> {
                    Log.d(TAG, "Received log data from: ${remoteMessage.senderId}")
                    
                    val logData = gson.fromJson(remoteMessage.payload, RemoteLogData::class.java)
                    processRemoteLogData(logData)
                    
                    sendHttpResponse(writer, 200, "OK", "{\"status\": \"received\"}")
                }
                MessageType.INCIDENT_ALERT -> {
                    Log.d(TAG, "🚨 Received incident alert from: ${remoteMessage.senderId}")
                    
                    val incidentNotification = gson.fromJson(remoteMessage.payload, IncidentNotification::class.java)
                    processIncidentAlert(incidentNotification, remoteMessage.senderId)
                    
                    // Wysyłaj ACK jeśli wymagane
                    val response = RemoteMessage(
                        senderId = getCurrentDeviceId(),
                        recipientId = remoteMessage.senderId,
                        messageType = MessageType.ACKNOWLEDGMENT,
                        payload = "INCIDENT_ALERT_RECEIVED"
                    )
                    
                    sendHttpResponse(writer, 200, "OK", gson.toJson(response))
                }
                else -> {
                    Log.d(TAG, "Unhandled message type: ${remoteMessage.messageType}")
                    sendHttpResponse(writer, 400, "Bad Request", "{\"error\": \"Unhandled message type\"}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling /message endpoint", e)
            sendHttpResponse(writer, 500, "Internal Server Error", "{\"error\": \"Failed to process message\"}")
        }
    }
    
    /**
     * Wysyła odpowiedź HTTP
     */
    private fun sendHttpResponse(writer: PrintWriter, statusCode: Int, statusText: String, body: String) {
        writer.println("HTTP/1.1 $statusCode $statusText")
        writer.println("Content-Type: application/json")
        writer.println("Content-Length: ${body.length}")
        writer.println("Connection: close")
        writer.println()
        writer.println(body)
        writer.flush()
        
        Log.d(TAG, "HTTP Response: $statusCode $statusText")
    }
    
    /**
     * Łączy się z urządzeniem zdalnym - z szczegółowym debugowaniem
     */
    private suspend fun connectToRemoteDevice(pairingData: PairingData): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://${pairingData.ipAddress}:${pairingData.port}/pair"
            Log.d(TAG, "=== ROZPOCZYNAM POŁĄCZENIE P2P ===")
            Log.d(TAG, "Target URL: $url")
            Log.d(TAG, "Target Device: ${pairingData.deviceName} (${pairingData.deviceType})")
            Log.d(TAG, "Target IP: ${pairingData.ipAddress}:${pairingData.port}")
            
            // Sprawdź dostępność sieci
            val isReachable = isHostReachable(pairingData.ipAddress, pairingData.port)
            Log.d(TAG, "Host reachability test: $isReachable")
            
            if (!isReachable) {
                Log.e(TAG, "❌ Host ${pairingData.ipAddress}:${pairingData.port} is NOT reachable!")
                Log.e(TAG, "Possible issues:")
                Log.e(TAG, "- Target device is not on the same network")
                Log.e(TAG, "- Target device firewall blocking port ${pairingData.port}")
                Log.e(TAG, "- Target device PairingService not running")
                Log.e(TAG, "- Wrong IP address in QR code")
                return@withContext false
            }
            
            // Stwórz dane bieżącego urządzenia
            val currentDeviceData = createCurrentDevicePairingData()
            Log.d(TAG, "Current device data:")
            Log.d(TAG, "  - Name: ${currentDeviceData.deviceName}")
            Log.d(TAG, "  - Type: ${currentDeviceData.deviceType}")
            Log.d(TAG, "  - IP: ${currentDeviceData.ipAddress}")
            Log.d(TAG, "  - ID: ${currentDeviceData.deviceId}")
            
            // Stwórz żądanie parowania
            val pairingRequest = RemoteMessage(
                senderId = getCurrentDeviceId(),
                recipientId = pairingData.deviceId,
                messageType = MessageType.PAIRING_REQUEST,
                payload = gson.toJson(currentDeviceData),
                requiresAck = true
            )
            
            val requestBody = gson.toJson(pairingRequest)
                .toRequestBody("application/json".toMediaType())
                
            Log.d(TAG, "Request body size: ${requestBody.contentLength()} bytes")
            Log.d(TAG, "Sending pairing request...")
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "KidSecura-Pairing/1.0")
                .build()
            
            Log.d(TAG, "Making HTTP call to: $url")
            val response = okHttpClient.newCall(request).execute()
            
            Log.d(TAG, "Response received:")
            Log.d(TAG, "  - Status: ${response.code}")
            Log.d(TAG, "  - Message: ${response.message}")
            Log.d(TAG, "  - Headers: ${response.headers}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "  - Body: $responseBody")
                
                if (responseBody != null) {
                    try {
                        val responseMessage = gson.fromJson(responseBody, RemoteMessage::class.java)
                        Log.d(TAG, "Parsed response message:")
                        Log.d(TAG, "  - Type: ${responseMessage.messageType}")
                        Log.d(TAG, "  - Payload: ${responseMessage.payload}")
                        Log.d(TAG, "  - Sender: ${responseMessage.senderId}")
                        
                        val success = responseMessage.messageType == MessageType.PAIRING_RESPONSE
                        if (success) {
                            Log.d(TAG, "✅ PAIRING SUCCESS! Response type matches expected.")
                            Log.d(TAG, "✅ P2P CONNECTION ESTABLISHED")
                        } else {
                            Log.e(TAG, "❌ PAIRING FAILED! Unexpected response type: ${responseMessage.messageType}")
                        }
                        return@withContext success
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing response JSON: ${e.message}")
                        Log.e(TAG, "Raw response body: $responseBody")
                        return@withContext false
                    }
                } else {
                    Log.e(TAG, "❌ Response body is null!")
                }
            } else {
                Log.e(TAG, "❌ HTTP Error: ${response.code} ${response.message}")
                val errorBody = response.body?.string()
                Log.e(TAG, "Error response body: $errorBody")
            }
            
            Log.e(TAG, "❌ CONNECTION FAILED")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION in connectToRemoteDevice: ${e::class.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            
            // Dodaj szczegóły błędu
            when (e) {
                is java.net.ConnectException -> {
                    Log.e(TAG, "🔗 CONNECTION REFUSED - Target device may not be listening on port")
                }
                is java.net.SocketTimeoutException -> {
                    Log.e(TAG, "⏰ TIMEOUT - Target device not responding within ${CONNECTION_TIMEOUT}ms")
                }
                is java.net.UnknownHostException -> {
                    Log.e(TAG, "🌐 UNKNOWN HOST - Invalid IP address or DNS issue")
                }
                is java.net.NoRouteToHostException -> {
                    Log.e(TAG, "🛣️ NO ROUTE - Network routing issue, check WiFi connection")
                }
                else -> {
                    Log.e(TAG, "❓ OTHER ERROR: ${e.message}")
                }
            }
            false
        }
    }
    
    /**
     * Sprawdza czy host jest dostępny na danym porcie
     */
    private suspend fun isHostReachable(ipAddress: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing connectivity to $ipAddress:$port...")
            val socket = Socket()
            socket.connect(java.net.InetSocketAddress(ipAddress, port), 3000) // 3 second timeout
            socket.close()
            Log.d(TAG, "✅ Connectivity test PASSED")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Connectivity test FAILED: ${e.message}")
            false
        }
    }
    
    /**
     * Obsługuje żądanie parowania
     */
    private suspend fun handlePairingRequest(message: RemoteMessage, outputStream: java.io.OutputStream) {
        try {
            Log.d(TAG, "Handling pairing request from: ${message.senderId}")
            
            val remotePairingData = gson.fromJson(message.payload, PairingData::class.java)
            
            // Sprawdź czy parowanie jest dozwolone
            val isAllowed = validatePairingRequest(remotePairingData)
            
            val response = RemoteMessage(
                senderId = getCurrentDeviceId(),
                recipientId = message.senderId,
                messageType = MessageType.PAIRING_RESPONSE,
                payload = if (isAllowed) "ACCEPTED" else "REJECTED"
            )
            
            val responseJson = gson.toJson(response)
            outputStream.write(responseJson.toByteArray())
            outputStream.flush()
            
            if (isAllowed) {
                // Zapisz dane parowania
                currentPairingData = remotePairingData
                savePairingData(remotePairingData)
                updatePairingStatus(true, remotePairingData)
                startHeartbeat()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling pairing request", e)
        }
    }
    
    /**
     * Obsługuje heartbeat
     */
    private suspend fun handleHeartbeat(message: RemoteMessage, outputStream: java.io.OutputStream) {
        try {
            Log.d(TAG, "Received heartbeat from: ${message.senderId}")
            
            // Zaktualizuj status połączenia
            pairingStatus = pairingStatus.copy(
                connectionStatus = ConnectionStatus.CONNECTED,
                lastHeartbeat = System.currentTimeMillis()
            )
            savePairingStatus()
            
            // Odpowiedz na heartbeat
            val response = RemoteMessage(
                senderId = getCurrentDeviceId(),
                recipientId = message.senderId,
                messageType = MessageType.ACKNOWLEDGMENT,
                payload = "HEARTBEAT_ACK"
            )
            
            val responseJson = gson.toJson(response)
            outputStream.write(responseJson.toByteArray())
            outputStream.flush()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling heartbeat", e)
        }
    }
    
    /**
     * Obsługuje dane logów
     */
    private suspend fun handleLogData(message: RemoteMessage) {
        try {
            Log.d(TAG, "Received log data from: ${message.senderId}")
            
            val logData = gson.fromJson(message.payload, RemoteLogData::class.java)
            
            // Przetwórz otrzymane dane logów
            processRemoteLogData(logData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling log data", e)
        }
    }
    
    /**
     * Przetwarza otrzymane dane logów
     */
    private fun processRemoteLogData(logData: RemoteLogData) {
        // TODO: Zaimplementuj przetwarzanie danych logów
        // Można zapisać do bazy danych, wysłać powiadomienia itp.
        Log.d(TAG, "Processing log data for device: ${logData.deviceId}")
        Log.d(TAG, "Current app: ${logData.currentApp.appName}")
        Log.d(TAG, "Alerts count: ${logData.alerts.size}")
    }
    
    /**
     * Rozpoczyna wysyłanie heartbeat
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive && pairingStatus.isPaired) {
                try {
                    sendHeartbeat()
                    delay(HEARTBEAT_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in heartbeat", e)
                    break
                }
            }
        }
    }
    
    /**
     * Wysyła heartbeat do sparowanego urządzenia
     */
    private suspend fun sendHeartbeat() {
        currentPairingData?.let { pairingData ->
            try {
                val heartbeat = RemoteMessage(
                    senderId = getCurrentDeviceId(),
                    recipientId = pairingData.deviceId,
                    messageType = MessageType.HEARTBEAT,
                    payload = "PING"
                )
                
                sendMessage(heartbeat, pairingData)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending heartbeat", e)
                updateConnectionStatus(ConnectionStatus.ERROR)
            }
        }
    }
    
    /**
     * Wysyła wiadomość do urządzenia
     */
    private suspend fun sendMessage(message: RemoteMessage, pairingData: PairingData) = withContext(Dispatchers.IO) {
        try {
            val url = "http://${pairingData.ipAddress}:${pairingData.port}/message"
            
            val requestBody = gson.toJson(message)
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }
    
    /**
     * Sprawdza czy żądanie parowania jest dozwolone
     */
    private fun validatePairingRequest(pairingData: PairingData): Boolean {
        // TODO: Dodaj dodatkową walidację
        return true
    }
    
    /**
     * Tworzy dane parowania dla bieżącego urządzenia
     */
    private fun createCurrentDevicePairingData(): PairingData {
        try {
            // Pobierz nazwę urządzenia
            val deviceName = android.os.Build.MODEL ?: "Unknown Device"
            
            // Pobierz typ urządzenia z preferencji (domyślnie CHILD)
            val deviceTypeString = prefs.getString("device_type", DeviceType.CHILD.name)
            val deviceType = try {
                DeviceType.valueOf(deviceTypeString ?: DeviceType.CHILD.name)
            } catch (e: Exception) {
                DeviceType.CHILD
            }
            
            // Pobierz adres IP urządzenia w sieci lokalnej
            val ipAddress = getLocalIPAddress() ?: "127.0.0.1"
            
            // Port serwera
            val port = 8080
            
            // Wygeneruj lub pobierz klucz bezpieczeństwa
            val securityKey = getOrGenerateSecurityKey()
            
            // Wygeneruj lub pobierz kod parowania
            val pairingCode = getOrGeneratePairingCode()
            
            // Uzyskaj ID urządzenia
            val deviceId = getCurrentDeviceId()
            
            Log.d(TAG, "Created pairing data - Device: $deviceName, Type: $deviceType, IP: $ipAddress")
            
            return PairingData(
                deviceId = deviceId,
                deviceName = deviceName,
                deviceType = deviceType,
                ipAddress = ipAddress,
                port = port,
                securityKey = securityKey,
                pairingCode = pairingCode
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating pairing data", e)
            // Fallback do podstawowych danych
            return PairingData(
                deviceId = getCurrentDeviceId(),
                deviceName = android.os.Build.MODEL ?: "Unknown Device",
                deviceType = DeviceType.CHILD,
                ipAddress = "127.0.0.1",
                port = 8080,
                securityKey = "fallback_key",
                pairingCode = "000000"
            )
        }
    }
    
    /**
     * Pobiera adres IP urządzenia w sieci lokalnej
     */
    private fun getLocalIPAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }
    
    /**
     * Pobiera lub generuje klucz bezpieczeństwa
     */
    private fun getOrGenerateSecurityKey(): String {
        return prefs.getString("security_key", null) ?: run {
            val newKey = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("security_key", newKey).apply()
            newKey
        }
    }
    
    /**
     * Pobiera lub generuje kod parowania
     */
    private fun getOrGeneratePairingCode(): String {
        return prefs.getString("pairing_code", null) ?: run {
            val newCode = (100000..999999).random().toString()
            prefs.edit().putString("pairing_code", newCode).apply()
            newCode
        }
    }
    
    /**
     * Pobiera ID bieżącego urządzenia
     */
    private fun getCurrentDeviceId(): String {
        return prefs.getString("device_id", null) ?: run {
            val newId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", newId).apply()
            newId
        }
    }
    
    /**
     * Aktualizuje status połączenia
     */
    private fun updateConnectionStatus(status: ConnectionStatus) {
        pairingStatus = pairingStatus.copy(connectionStatus = status)
        savePairingStatus()
    }
    
    /**
     * Aktualizuje status parowania
     */
    private fun updatePairingStatus(isPaired: Boolean, pairingData: PairingData) {
        pairingStatus = PairingStatus(
            isPaired = isPaired,
            pairedDeviceId = pairingData.deviceId,
            pairedDeviceName = pairingData.deviceName,
            pairedDeviceType = pairingData.deviceType,
            connectionStatus = if (isPaired) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
            lastHeartbeat = System.currentTimeMillis()
        )
        savePairingStatus()
    }
    
    /**
     * Zapisuje dane parowania
     */
    private fun savePairingData(pairingData: PairingData) {
        prefs.edit()
            .putString(KEY_PAIRING_DATA, gson.toJson(pairingData))
            .apply()
    }
    
    /**
     * Zapisuje status parowania
     */
    private fun savePairingStatus() {
        prefs.edit()
            .putString(KEY_PAIRING_STATUS, gson.toJson(pairingStatus))
            .apply()
    }
    
    /**
     * Ładuje dane parowania
     */
    private fun loadPairingData() {
        try {
            prefs.getString(KEY_PAIRING_DATA, null)?.let { json ->
                currentPairingData = gson.fromJson(json, PairingData::class.java)
            }
            
            prefs.getString(KEY_PAIRING_STATUS, null)?.let { json ->
                pairingStatus = gson.fromJson(json, PairingStatus::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pairing data", e)
        }
    }
    
    /**
     * Pobiera aktualny status parowania
     */
    fun getPairingStatus(): PairingStatus = pairingStatus
    
    /**
     * Rozłącza sparowane urządzenie
     */
    fun unpair() {
        heartbeatJob?.cancel()
        serverJob?.cancel()
        
        currentPairingData = null
        pairingStatus = PairingStatus()
        
        prefs.edit()
            .remove(KEY_PAIRING_DATA)
            .remove(KEY_PAIRING_STATUS)
            .apply()
            
        Log.d(TAG, "Device unpaired")
    }
    
    /**
     * Przetwarza powiadomienie o incydencie od urządzenia dziecka
     */
    private suspend fun processIncidentAlert(incidentNotification: IncidentNotification, senderId: String) = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "=== PRZETWARZANIE ALERTU INCYDENTU ===")
            Log.d(TAG, "Incident ID: ${incidentNotification.incidentId}")
            Log.d(TAG, "Device: ${incidentNotification.deviceName}")
            Log.d(TAG, "Severity: ${incidentNotification.severity.displayName}")
            Log.d(TAG, "Description: ${incidentNotification.description}")
            Log.d(TAG, "Confidence: ${(incidentNotification.confidence * 100).toInt()}%")
            Log.d(TAG, "Keywords: ${incidentNotification.detectedKeywords.joinToString(", ")}")
            
            // Tworzenie lokalnego powiadomienia dla rodzica
            val title = "🚨 ALERT: ${incidentNotification.severity.displayName.uppercase()} incydent!"
            val message = "${incidentNotification.deviceName}: ${incidentNotification.description}"
            
            // Wysyłanie powiadomienia przez NotificationHelper
            val notificationHelper = com.parentalcontrol.mvp.utils.NotificationHelper(context)
            notificationHelper.showAlert(
                title = title,
                message = message,
                confidence = (incidentNotification.confidence * 100).toInt()
            )
            
            // Opcjonalnie: zapisz incydent w lokalnym IncidentManager
            try {
                val localIncident = Incident(
                    id = incidentNotification.incidentId,
                    deviceId = incidentNotification.deviceId,
                    deviceName = incidentNotification.deviceName,
                    timestamp = incidentNotification.timestamp,
                    description = incidentNotification.description,
                    severity = incidentNotification.severity,
                    confidence = incidentNotification.confidence,
                    detectedKeywords = incidentNotification.detectedKeywords,
                    extractedText = incidentNotification.extractedText,
                    isReviewed = false
                )
                
                val incidentManager = com.parentalcontrol.mvp.manager.IncidentManager(context)
                withContext(Dispatchers.IO) {
                    incidentManager.addIncident(
                        deviceId = localIncident.deviceId,
                        deviceName = localIncident.deviceName,
                        detectedKeywords = localIncident.detectedKeywords,
                        description = localIncident.description,
                        confidence = localIncident.confidence
                    )
                }
                
                Log.d(TAG, "✅ Incident saved locally for parent review")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving incident locally", e)
            }
            
            Log.d(TAG, "✅ INCIDENT ALERT PROCESSED SUCCESSFULLY")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR processing incident alert", e)
        }
    }
    
    /**
     * Enhanced cleanup z proper resource management
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Starting comprehensive cleanup...")
        
        try {
            // Stop all background jobs
            stopAllJobs()
            
            // Clean up network resources
            cleanupNetworkResources()
            
            // Reset connection state
            resetConnectionState()
            
            // Cancel service scope last
            serviceScope.cancel()
            
            Log.d(TAG, "✅ Cleanup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during comprehensive cleanup", e)
        }
    }
    
    
    
    
    /**
     * Graceful shutdown z timeout
     */
    fun gracefulShutdown(timeoutMs: Long = 5000L) {
        Log.d(TAG, "🔄 Starting graceful shutdown with timeout: ${timeoutMs}ms")
        
        serviceScope.launch {
            try {
                // Stop accepting new connections
                stopAutomaticReconnection()
                
                // Send disconnect message to paired device if connected
                currentPairingData?.let { pairingData ->
                    try {
                        val disconnectMessage = RemoteMessage(
                            senderId = pairingData.deviceId,
                            recipientId = pairingData.deviceId,
                            messageType = MessageType.PAIRING_REQUEST, // Use existing enum value
                            payload = gson.toJson(mapOf("action" to "disconnect")),
                            timestamp = System.currentTimeMillis()
                        )
                        
                        executeWithRetry("graceful_disconnect", maxAttempts = 1) {
                            sendMessage(disconnectMessage, pairingData)
                        }
                        
                        Log.d(TAG, "📤 Disconnect notification sent")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Could not send disconnect notification: ${e.message}")
                    }
                }
                
                // Wait a moment for pending operations
                delay(1000L)
                
                // Perform cleanup
                cleanup()
                
                Log.d(TAG, "✅ Graceful shutdown completed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during graceful shutdown", e)
                // Force cleanup anyway
                cleanup()
            }
        }
    }
    
    /**
     * Circuit breaker - sprawdza czy można wykonać operację
     */
    private fun canExecuteOperation(operationName: String): Boolean {
        if (!isCircuitBreakerOpen.get()) {
            return true
        }
        
        val timeSinceOpen = System.currentTimeMillis() - circuitBreakerOpenTime
        if (timeSinceOpen >= CIRCUIT_BREAKER_RECOVERY_TIMEOUT) {
            Log.d(TAG, "🔄 Circuit breaker recovery timeout reached for '$operationName', attempting to close")
            return true
        }
        
        Log.w(TAG, "⚡ Circuit breaker OPEN for '$operationName' - operation blocked (${timeSinceOpen}ms since open)")
        return false
    }
    
    /**
     * Rejestruje sukces operacji w circuit breaker
     */
    private fun recordOperationSuccess(operationName: String) {
        val successes = circuitBreakerSuccesses.incrementAndGet()
        
        if (isCircuitBreakerOpen.get() && successes >= CIRCUIT_BREAKER_SUCCESS_THRESHOLD) {
            Log.d(TAG, "✅ Circuit breaker CLOSED for '$operationName' after $successes consecutive successes")
            isCircuitBreakerOpen.set(false)
            circuitBreakerFailures.set(0)
            circuitBreakerSuccesses.set(0)
        }
    }
    
    /**
     * Rejestruje błąd operacji w circuit breaker
     */
    private fun recordOperationFailure(operationName: String, error: String) {
        val failures = circuitBreakerFailures.incrementAndGet()
        circuitBreakerSuccesses.set(0) // Reset successes on any failure
        
        // Store error for analysis
        synchronized(lastKnownErrors) {
            lastKnownErrors.add(Pair(System.currentTimeMillis(), error))
            // Keep only last 10 errors
            if (lastKnownErrors.size > 10) {
                lastKnownErrors.removeAt(0)
            }
        }
        
        if (failures >= CIRCUIT_BREAKER_FAILURE_THRESHOLD && !isCircuitBreakerOpen.get()) {
            Log.e(TAG, "⚡ Circuit breaker OPENED for '$operationName' after $failures consecutive failures")
            isCircuitBreakerOpen.set(true)
            circuitBreakerOpenTime = System.currentTimeMillis()
            
            // Log error pattern analysis
            analyzeErrorPatterns(operationName)
        }
    }
    
    /**
     * Analizuje wzorce błędów dla lepszego recovery
     */
    private fun analyzeErrorPatterns(operationName: String) {
        synchronized(lastKnownErrors) {
            if (lastKnownErrors.isEmpty()) return
            
            Log.w(TAG, "🔍 Analyzing error patterns for '$operationName':")
            
            val errorTypes = mutableMapOf<String, Int>()
            val recentErrors = lastKnownErrors.takeLast(5)
            
            recentErrors.forEach { (timestamp, error) ->
                val errorType = when {
                    error.contains("timeout", ignoreCase = true) -> "TIMEOUT"
                    error.contains("connection", ignoreCase = true) -> "CONNECTION"
                    error.contains("socket", ignoreCase = true) -> "SOCKET"
                    error.contains("network", ignoreCase = true) -> "NETWORK"
                    else -> "OTHER"
                }
                errorTypes[errorType] = errorTypes.getOrDefault(errorType, 0) + 1
                
                val timeAgo = System.currentTimeMillis() - timestamp
                Log.w(TAG, "  - ${timeAgo}ms ago: [$errorType] $error")
            }
            
            // Suggest recovery action based on error patterns
            val dominantErrorType = errorTypes.maxByOrNull { it.value }?.key
            when (dominantErrorType) {
                "TIMEOUT" -> Log.i(TAG, "💡 Recovery suggestion: Consider increasing timeout values")
                "CONNECTION" -> Log.i(TAG, "💡 Recovery suggestion: Check network connectivity and restart connection")
                "SOCKET" -> Log.i(TAG, "💡 Recovery suggestion: Close and recreate sockets")
                "NETWORK" -> Log.i(TAG, "💡 Recovery suggestion: Check network configuration")
                else -> Log.i(TAG, "💡 Recovery suggestion: General error recovery needed")
            }
        }
    }
    
    /**
     * Enhanced error recovery based on error type
     */
    private suspend fun attemptErrorRecovery(error: Exception, operationName: String): Boolean {
        Log.d(TAG, "🛠️ Attempting error recovery for '$operationName': ${error.message}")
        
        return try {
            when {
                error is java.net.SocketTimeoutException -> {
                    Log.d(TAG, "🔄 Socket timeout recovery: resetting connection")
                    resetConnectionState()
                    delay(2000)
                    true
                }
                error is java.net.ConnectException -> {
                    Log.d(TAG, "🔄 Connection error recovery: checking network and retrying")
                    cleanupNetworkResources()
                    delay(3000)
                    true
                }
                error is java.io.IOException -> {
                    Log.d(TAG, "🔄 IO error recovery: full resource cleanup and reset")
                    stopAllJobs()
                    cleanupNetworkResources()
                    resetConnectionState()
                    delay(5000)
                    true
                }
                error.message?.contains("failed to connect", ignoreCase = true) == true -> {
                    Log.d(TAG, "🔄 Connection failure recovery: network diagnostics and reset")
                    performNetworkDiagnostics()
                    delay(4000)
                    true
                }
                else -> {
                    Log.w(TAG, "⚠️ Unknown error type, performing general recovery")
                    delay(1000)
                    false
                }
            }
        } catch (recoveryError: Exception) {
            Log.e(TAG, "❌ Error recovery failed: ${recoveryError.message}")
            false
        }
    }
    
    /**
     * Wykonuje diagnostykę sieci dla lepszego troubleshooting
     */
    private suspend fun performNetworkDiagnostics() {
        Log.d(TAG, "🔍 Performing network diagnostics...")
        
        try {
            // Check if device has network connectivity
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            
            if (networkInfo?.isConnected == true) {
                Log.d(TAG, "✅ Device has active network connection: ${networkInfo.typeName}")
            } else {
                Log.w(TAG, "⚠️ Device has no active network connection")
                return
            }
            
            // Check if current pairing data is still valid
            currentPairingData?.let { pairingData ->
                Log.d(TAG, "🔍 Testing connection to paired device: ${pairingData.ipAddress}:${pairingData.port}")
                
                try {
                    // Simple ping test
                    val testSocket = java.net.Socket()
                    testSocket.connect(java.net.InetSocketAddress(pairingData.ipAddress, pairingData.port), 3000)
                    testSocket.close()
                    Log.d(TAG, "✅ Paired device is reachable")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Paired device unreachable: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Network diagnostics failed: ${e.message}")
        }
    }
    
    /**
     * Wykonuje operację z retry logic i exponential backoff
     */
    private suspend fun <T> executeWithRetry(
        operation: String,
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
        block: suspend () -> T
    ): T? {
        // Check circuit breaker before attempting operation
        if (!canExecuteOperation(operation)) {
            Log.w(TAG, "⚡ Operation '$operation' blocked by circuit breaker")
            return null
        }
        
        var lastException: Exception? = null
        
        repeat(maxAttempts) { attempt ->
            try {
                Log.d(TAG, "🔄 Attempt ${attempt + 1}/$maxAttempts for operation: $operation")
                val result = block()
                
                // Record success in circuit breaker
                recordOperationSuccess(operation)
                
                if (attempt > 0) {
                    Log.d(TAG, "✅ Operation '$operation' succeeded on attempt ${attempt + 1}")
                }
                
                return result
            } catch (e: Exception) {
                lastException = e
                val errorMessage = e.message ?: "Unknown error"
                
                // Record failure in circuit breaker
                recordOperationFailure(operation, errorMessage)
                
                Log.w(TAG, "⚠️ Attempt ${attempt + 1}/$maxAttempts failed for '$operation': $errorMessage")
                
                if (attempt < maxAttempts - 1) {
                    // Attempt error recovery before retry
                    val recoverySuccessful = attemptErrorRecovery(e, operation)
                    if (recoverySuccessful) {
                        Log.d(TAG, "🛠️ Error recovery successful for '$operation', continuing with retry")
                    }
                    
                    val delayMs = (RETRY_DELAY_BASE * Math.pow(RETRY_DELAY_MULTIPLIER, attempt.toDouble())).toLong()
                    Log.d(TAG, "⏳ Waiting ${delayMs}ms before retry...")
                    delay(delayMs)
                }
            }
        }
        
        Log.e(TAG, "❌ All $maxAttempts attempts failed for operation '$operation'", lastException)
        return null
    }
    
    /**
     * Reset connection state variables
     */
    private fun resetConnectionState() {
        Log.d(TAG, "🔄 Resetting connection state")
        isConnectionHealthy.set(false)
        retryAttempts.set(0)
        reconnectionAttempts.set(0)
        isReconnecting.set(false)
    }
    
    /**
     * Cleanup network resources specifically
     */
    private fun cleanupNetworkResources() {
        Log.d(TAG, "🧹 Cleaning up network resources")
        try {
            clientSocket?.close()
            clientSocket = null
            
            serverSocket?.close()
            serverSocket = null
            
            // Evict idle connections from HTTP client
            okHttpClient.connectionPool.evictAll()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up network resources", e)
        }
    }
    
    /**
     * Stop all background jobs
     */
    private fun stopAllJobs() {
        Log.d(TAG, "⏹️ Stopping all background jobs")
        try {
            heartbeatJob?.cancel()
            heartbeatJob = null
            
            serverJob?.cancel()
            serverJob = null
            
            reconnectionJob?.cancel()
            reconnectionJob = null
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping background jobs", e)
        }
    }
    
    /**
     * Sprawdza czy połączenie jest aktywne
     */
    private suspend fun checkConnectionHealth(): Boolean {
        return try {
            currentPairingData?.let { pairingData ->
                val testMessage = RemoteMessage(
                    senderId = "health_check",
                    recipientId = pairingData.deviceId,
                    messageType = MessageType.HEARTBEAT,
                    payload = gson.toJson(mapOf("type" to "health_check")),
                    timestamp = System.currentTimeMillis()
                )
                
                executeWithRetry("connection_health_check") {
                    sendMessage(testMessage, pairingData)
                    true
                } ?: false
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "❤️ Connection health check failed: ${e.message}")
            false
        }
    }
    
    /**
     * Rozpoczyna automatyczne reconnection w przypadku utraty połączenia
     */
    private fun startAutomaticReconnection() {
        if (isReconnecting.getAndSet(true)) {
            Log.d(TAG, "🔄 Reconnection already in progress, skipping...")
            return
        }
        
        reconnectionJob?.cancel()
        reconnectionJob = serviceScope.launch {
            Log.d(TAG, "🔄 Starting automatic reconnection process...")
            reconnectionAttempts.set(0)
            
            while (reconnectionAttempts.get() < MAX_RECONNECTION_ATTEMPTS && isActive) {
                val attemptNumber = reconnectionAttempts.incrementAndGet()
                Log.d(TAG, "🔄 Reconnection attempt $attemptNumber/$MAX_RECONNECTION_ATTEMPTS")
                
                try {
                    val connectionRestored = checkConnectionHealth()
                    
                    if (connectionRestored) {
                        Log.d(TAG, "✅ Connection restored successfully!")
                        isConnectionHealthy.set(true)
                        isReconnecting.set(false)
                        reconnectionAttempts.set(0)
                        
                        // Restart heartbeat if it was stopped
                        if (heartbeatJob?.isActive != true) {
                            startHeartbeat()
                        }
                        
                        return@launch
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Reconnection attempt $attemptNumber failed: ${e.message}")
                }
                
                if (attemptNumber < MAX_RECONNECTION_ATTEMPTS) {
                    Log.d(TAG, "⏳ Waiting ${RECONNECTION_DELAY}ms before next reconnection attempt...")
                    delay(RECONNECTION_DELAY)
                }
            }
            
            Log.e(TAG, "❌ All reconnection attempts failed. Connection lost.")
            isConnectionHealthy.set(false)
            isReconnecting.set(false)
            
            // Notify about connection loss
            currentPairingData?.let { pairingData ->
                updatePairingStatus(false, pairingData)
            }
        }
    }
    
    /**
     * Zatrzymuje proces automatycznego reconnection
     */
    private fun stopAutomaticReconnection() {
        Log.d(TAG, "🛑 Stopping automatic reconnection...")
        isReconnecting.set(false)
        reconnectionJob?.cancel()
        reconnectionJob = null
    }
    
    /**
     * Enhanced heartbeat z connection monitoring
     */
    private fun startEnhancedHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            Log.d(TAG, "💓 Enhanced heartbeat started with interval: ${HEARTBEAT_INTERVAL}ms")
            
            while (isActive && currentPairingData != null) {
                try {
                    val isHealthy = checkConnectionHealth()
                    
                    if (!isHealthy && isConnectionHealthy.get()) {
                        Log.w(TAG, "⚠️ Connection health deteriorated, starting reconnection...")
                        isConnectionHealthy.set(false)
                        startAutomaticReconnection()
                    } else if (isHealthy && !isConnectionHealthy.get()) {
                        Log.d(TAG, "✅ Connection health restored")
                        isConnectionHealthy.set(true)
                        stopAutomaticReconnection()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "💓 Heartbeat error: ${e.message}")
                    if (isConnectionHealthy.get()) {
                        startAutomaticReconnection()
                    }
                }
                
                delay(HEARTBEAT_INTERVAL)
            }
            
            Log.d(TAG, "💓 Enhanced heartbeat stopped")
        }
    }
}
