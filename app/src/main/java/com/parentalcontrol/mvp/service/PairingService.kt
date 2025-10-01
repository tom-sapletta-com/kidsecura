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

class PairingService(private val context: Context) {
    
    companion object {
        private const val TAG = "PairingService"
        private const val PREFS_NAME = "pairing_prefs"
        private const val KEY_PAIRING_DATA = "pairing_data"
        private const val KEY_PAIRING_STATUS = "pairing_status"
        private const val PAIRING_TIMEOUT = 30000L // 30 seconds
        private const val HEARTBEAT_INTERVAL = 10000L // 10 seconds
        private const val CONNECTION_TIMEOUT = 5000L // 5 seconds
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var currentPairingData: PairingData? = null
    private var pairingStatus = PairingStatus()
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        .build()
    
    private var heartbeatJob: Job? = null
    private var serverJob: Job? = null
    
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
            serverSocket?.close()
            serverSocket = ServerSocket(8080) // Default port
            
            serverJob = serviceScope.launch {
                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        val socket = serverSocket?.accept()
                        if (socket != null) {
                            launch { handleHttpConnection(socket) }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error accepting connection", e)
                        }
                    }
                }
            }
            
            Log.d(TAG, "Server started on port 8080")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server", e)
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
    private suspend fun handleHttpConnection(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "HTTP connection from: ${socket.remoteSocketAddress}")
            
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
            
            // Odczytaj HTTP request
            val requestLine = reader.readLine() ?: return@withContext
            Log.d(TAG, "HTTP Request: $requestLine")
            
            // Parsuj metodę i path
            val parts = requestLine.split(" ")
            if (parts.size < 3) return@withContext
            
            val method = parts[0]
            val path = parts[1]
            
            // Odczytaj headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val colonIndex = line!!.indexOf(':')
                if (colonIndex > 0) {
                    val key = line!!.substring(0, colonIndex).trim()
                    val value = line!!.substring(colonIndex + 1).trim()
                    headers[key.lowercase()] = value
                }
            }
            
            // Odczytaj body dla POST żądań
            var requestBody = ""
            if (method == "POST") {
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength > 0) {
                    val bodyBuffer = CharArray(contentLength)
                    reader.read(bodyBuffer, 0, contentLength)
                    requestBody = String(bodyBuffer)
                }
            }
            
            Log.d(TAG, "HTTP Body: $requestBody")
            
            // Obsłuż endpointy
            when {
                path == "/pair" && method == "POST" -> {
                    handlePairEndpoint(requestBody, writer)
                }
                path == "/message" && method == "POST" -> {
                    handleMessageEndpoint(requestBody, writer)
                }
                else -> {
                    sendHttpResponse(writer, 404, "Not Found", "{\"error\": \"Endpoint not found\"}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling HTTP connection", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing HTTP socket", e)
            }
        }
    }
    
    /**
     * Obsługuje endpoint /pair
     */
    private suspend fun handlePairEndpoint(requestBody: String, writer: PrintWriter) = withContext(Dispatchers.IO) {
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
    private suspend fun handleMessageEndpoint(requestBody: String, writer: PrintWriter) = withContext(Dispatchers.IO) {
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
     * Czyści zasoby
     */
    fun cleanup() {
        heartbeatJob?.cancel()
        serverJob?.cancel()
        
        try {
            serverSocket?.close()
            clientSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
        
        serviceScope.cancel()
    }
}
