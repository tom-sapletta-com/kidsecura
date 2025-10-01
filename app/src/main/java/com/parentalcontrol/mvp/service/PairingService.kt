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
                            handleIncomingConnection(socket)
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
     * Łączy się z urządzeniem zdalnym
     */
    private suspend fun connectToRemoteDevice(pairingData: PairingData): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://${pairingData.ipAddress}:${pairingData.port}/pair"
            
            // Stwórz żądanie parowania
            val pairingRequest = RemoteMessage(
                senderId = getCurrentDeviceId(),
                recipientId = pairingData.deviceId,
                messageType = MessageType.PAIRING_REQUEST,
                payload = gson.toJson(createCurrentDevicePairingData()),
                requiresAck = true
            )
            
            val requestBody = gson.toJson(pairingRequest)
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val responseMessage = gson.fromJson(responseBody, RemoteMessage::class.java)
                    return@withContext responseMessage.messageType == MessageType.PAIRING_RESPONSE
                }
            }
            
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to remote device", e)
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
        // TODO: Zaimplementuj tworzenie danych parowania
        return PairingData(
            deviceName = "Current Device",
            deviceType = DeviceType.CHILD, // lub PARENT
            ipAddress = "192.168.1.100",
            port = 8080,
            securityKey = "dummy_key",
            pairingCode = "123456"
        )
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
