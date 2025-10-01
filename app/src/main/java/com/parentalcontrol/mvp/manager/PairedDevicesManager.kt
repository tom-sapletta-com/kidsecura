package com.parentalcontrol.mvp.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.parentalcontrol.mvp.model.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager do zarządzania listą sparowanych urządzeń i ich statusem
 */
class PairedDevicesManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PairedDevicesManager"
        private const val PREFS_NAME = "paired_devices_prefs"
        private const val KEY_PAIRED_DEVICES = "paired_devices"
        private const val DEVICE_TIMEOUT = 5 * 60 * 1000L // 5 minut timeout
        private const val PING_INTERVAL = 30 * 1000L // 30 sekund ping
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Cache sparowanych urządzeń
    private val pairedDevicesCache = ConcurrentHashMap<String, PairedDevice>()
    private var statusMonitoringJob: Job? = null
    
    init {
        loadPairedDevicesFromStorage()
        startStatusMonitoring()
    }
    
    /**
     * Dodaje nowe sparowane urządzenie
     */
    fun addPairedDevice(pairingData: PairingData): PairedDevice {
        val device = PairedDevice(
            deviceId = pairingData.deviceId,
            deviceName = pairingData.deviceName,
            deviceType = pairingData.deviceType,
            ipAddress = pairingData.ipAddress,
            port = pairingData.port,
            lastSeen = System.currentTimeMillis(),
            connectionStatus = ConnectionStatus.CONNECTED,
            pairingDate = System.currentTimeMillis(),
            isActive = true
        )
        
        pairedDevicesCache[device.deviceId] = device
        savePairedDevicesToStorage()
        
        Log.d(TAG, "Added paired device: ${device.deviceName} (${device.deviceType})")
        return device
    }
    
    /**
     * Aktualizuje status połączenia urządzenia
     */
    fun updateDeviceConnectionStatus(deviceId: String, status: ConnectionStatus) {
        pairedDevicesCache[deviceId]?.let { device ->
            val updated = device.copy(
                connectionStatus = status,
                lastSeen = if (status == ConnectionStatus.CONNECTED) System.currentTimeMillis() else device.lastSeen
            )
            pairedDevicesCache[deviceId] = updated
            savePairedDevicesToStorage()
            
            Log.d(TAG, "Updated device ${device.deviceName} status to: $status")
        }
    }
    
    /**
     * Usuwa sparowane urządzenie
     */
    fun removePairedDevice(deviceId: String): Boolean {
        val removed = pairedDevicesCache.remove(deviceId)
        if (removed != null) {
            savePairedDevicesToStorage()
            Log.d(TAG, "Removed paired device: ${removed.deviceName}")
            return true
        }
        return false
    }
    
    /**
     * Pobiera wszystkie sparowane urządzenia
     */
    fun getAllPairedDevices(): List<PairedDevice> {
        return pairedDevicesCache.values.toList().sortedByDescending { it.lastSeen }
    }
    
    /**
     * Pobiera aktywne urządzenia (połączone w ostatnich 5 minutach)
     */
    fun getActiveDevices(): List<PairedDevice> {
        val currentTime = System.currentTimeMillis()
        return pairedDevicesCache.values.filter { device ->
            device.isActive && (currentTime - device.lastSeen) <= DEVICE_TIMEOUT
        }.sortedByDescending { it.lastSeen }
    }
    
    /**
     * Pobiera urządzenia rodziców
     */
    fun getParentDevices(): List<PairedDevice> {
        return pairedDevicesCache.values.filter { 
            it.deviceType == DeviceType.PARENT 
        }.sortedByDescending { it.lastSeen }
    }
    
    /**
     * Pobiera urządzenia dzieci
     */
    fun getChildDevices(): List<PairedDevice> {
        return pairedDevicesCache.values.filter { 
            it.deviceType == DeviceType.CHILD 
        }.sortedByDescending { it.lastSeen }
    }
    
    /**
     * Pobiera urządzenie po ID
     */
    fun getDeviceById(deviceId: String): PairedDevice? {
        return pairedDevicesCache[deviceId]
    }
    
    /**
     * Aktualizuje nickname urządzenia
     */
    fun updateDeviceNickname(deviceId: String, nickname: String?) {
        pairedDevicesCache[deviceId]?.let { device ->
            val updated = device.copy(nickname = nickname)
            pairedDevicesCache[deviceId] = updated
            savePairedDevicesToStorage()
            Log.d(TAG, "Updated device nickname: ${device.deviceName} -> $nickname")
        }
    }
    
    /**
     * Sprawdza czy urządzenie jest sparowane
     */
    fun isDevicePaired(deviceId: String): Boolean {
        return pairedDevicesCache.containsKey(deviceId)
    }
    
    /**
     * Pobiera statystyki zarządzania urządzeniami
     */
    fun getDeviceManagementStatus(): DeviceManagementStatus {
        val allDevices = pairedDevicesCache.values.toList()
        val activeDevices = getActiveDevices()
        val parentDevices = getParentDevices()
        val childDevices = getChildDevices()
        
        return DeviceManagementStatus(
            totalPairedDevices = allDevices.size,
            activeDevices = activeDevices.size,
            parentDevices = parentDevices.size,
            childDevices = childDevices.size,
            lastSyncTime = allDevices.maxOfOrNull { it.lastSeen },
            pendingIncidents = 0 // TODO: Integrate with IncidentManager
        )
    }
    
    /**
     * Wysyła wiadomość do konkretnego urządzenia
     */
    suspend fun sendMessageToDevice(deviceId: String, message: RemoteMessage): Boolean = withContext(Dispatchers.IO) {
        val device = pairedDevicesCache[deviceId] ?: return@withContext false
        
        return@withContext try {
            // TODO: Implement P2P message sending via HTTP
            Log.d(TAG, "Sending message to ${device.deviceName}: ${message.messageType}")
            
            // Placeholder for actual HTTP call
            updateDeviceConnectionStatus(deviceId, ConnectionStatus.CONNECTED)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to device $deviceId", e)
            updateDeviceConnectionStatus(deviceId, ConnectionStatus.DISCONNECTED)
            false
        }
    }
    
    /**
     * Broadcast wiadomości do wszystkich aktywnych rodziców
     */
    suspend fun broadcastToParents(message: RemoteMessage) {
        val parentDevices = getParentDevices().filter { it.isActive }
        
        Log.d(TAG, "Broadcasting to ${parentDevices.size} parent devices")
        
        parentDevices.forEach { device ->
            managerScope.launch {
                sendMessageToDevice(device.deviceId, message)
            }
        }
    }
    
    /**
     * Broadcast wiadomości do wszystkich aktywnych dzieci
     */
    suspend fun broadcastToChildren(message: RemoteMessage) {
        val childDevices = getChildDevices().filter { it.isActive }
        
        Log.d(TAG, "Broadcasting to ${childDevices.size} child devices")
        
        childDevices.forEach { device ->
            managerScope.launch {
                sendMessageToDevice(device.deviceId, message)
            }
        }
    }
    
    /**
     * Rozpoczyna monitorowanie statusu urządzeń
     */
    private fun startStatusMonitoring() {
        statusMonitoringJob?.cancel()
        statusMonitoringJob = managerScope.launch {
            while (isActive) {
                checkDevicesStatus()
                delay(PING_INTERVAL)
            }
        }
        Log.d(TAG, "Started status monitoring")
    }
    
    /**
     * Sprawdza status wszystkich urządzeń
     */
    private suspend fun checkDevicesStatus() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        
        pairedDevicesCache.values.forEach { device ->
            val timeSinceLastSeen = currentTime - device.lastSeen
            
            val newStatus = when {
                timeSinceLastSeen > DEVICE_TIMEOUT -> ConnectionStatus.DISCONNECTED
                device.connectionStatus == ConnectionStatus.DISCONNECTED && timeSinceLastSeen <= DEVICE_TIMEOUT -> ConnectionStatus.CONNECTED
                else -> device.connectionStatus
            }
            
            if (newStatus != device.connectionStatus) {
                updateDeviceConnectionStatus(device.deviceId, newStatus)
            }
        }
    }
    
    /**
     * Ładuje sparowane urządzenia z pamięci
     */
    private fun loadPairedDevicesFromStorage() {
        try {
            val json = prefs.getString(KEY_PAIRED_DEVICES, null)
            if (json != null) {
                val type = object : TypeToken<List<PairedDevice>>() {}.type
                val devices: List<PairedDevice> = gson.fromJson(json, type)
                devices.forEach { device ->
                    pairedDevicesCache[device.deviceId] = device
                }
                Log.d(TAG, "Loaded ${devices.size} paired devices from storage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading paired devices from storage", e)
        }
    }
    
    /**
     * Zapisuje sparowane urządzenia do pamięci
     */
    private fun savePairedDevicesToStorage() {
        managerScope.launch {
            try {
                val devices = pairedDevicesCache.values.toList()
                val json = gson.toJson(devices)
                prefs.edit().putString(KEY_PAIRED_DEVICES, json).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving paired devices to storage", e)
            }
        }
    }
    
    /**
     * Zamyka manager i czyści zasoby
     */
    fun shutdown() {
        statusMonitoringJob?.cancel()
        managerScope.cancel()
        savePairedDevicesToStorage()
        Log.d(TAG, "PairedDevicesManager shutdown")
    }
}
