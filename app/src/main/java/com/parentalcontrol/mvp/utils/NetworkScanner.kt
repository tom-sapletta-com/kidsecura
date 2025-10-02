package com.parentalcontrol.mvp.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Szybkie skanowanie sieci WiFi w celu wykrycia urządzeń
 * Używane podczas parowania aby pokazać dostępne urządzenia
 */
class NetworkScanner(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkScanner"
        private const val SCAN_TIMEOUT = 2000 // ms - zwiększone z 500ms na 2s
        private const val PAIRING_PORT = 8080 // Port używany do parowania
        private const val MAX_PARALLEL_SCANS = 50 // Maksymalna liczba równoczesnych skanów
    }
    
    private val systemLogger = SystemLogger.getInstance(context)
    
    /**
     * Wykryte urządzenie w sieci
     */
    data class NetworkDevice(
        val ip: String,
        val hostname: String?,
        val isReachable: Boolean,
        val hasPairingPort: Boolean,
        val responseTime: Long // ms
    ) {
        fun getDisplayName(): String {
            return if (!hostname.isNullOrEmpty() && hostname != ip) {
                "$hostname ($ip)"
            } else {
                ip
            }
        }
    }
    
    /**
     * Pobiera lokalny subnet na podstawie IP urządzenia
     */
    fun getLocalSubnet(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            
            // Konwertuj IP na format czytelny
            val ipAddress = dhcpInfo.ipAddress
            val subnet = String.format(
                "%d.%d.%d",
                ipAddress and 0xFF,
                ipAddress shr 8 and 0xFF,
                ipAddress shr 16 and 0xFF
            )
            
            systemLogger.d(TAG, "🌐 Local subnet: $subnet.0/24")
            subnet
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error getting subnet", e)
            null
        }
    }
    
    /**
     * Szybkie skanowanie sieci - wykrywa tylko aktywne urządzenia
     * Używa ping + sprawdzanie portu parowania
     */
    suspend fun quickScan(onDeviceFound: (NetworkDevice) -> Unit): List<NetworkDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<NetworkDevice>()
        val subnet = getLocalSubnet()
        
        if (subnet == null) {
            Log.e(TAG, "❌ Cannot get local subnet - WiFi not connected?")
            systemLogger.e(TAG, "❌ Nie można pobrać lokalnej podsieci - brak WiFi?")
            return@withContext emptyList()
        }
        
        Log.d(TAG, "🔍 Starting network scan on $subnet.0/24 (timeout: ${SCAN_TIMEOUT}ms)")
        systemLogger.i(TAG, "🔍 Rozpoczęcie szybkiego skanowania sieci $subnet.0/24")
        val startTime = System.currentTimeMillis()
        
        try {
            // Skanuj 1-254 (pomijamy .0 i .255) w batch'ach żeby nie przeciążyć sieci
            coroutineScope {
                val allHosts = (1..254).map { "$subnet.$it" }
                
                // Podziel na chunki po MAX_PARALLEL_SCANS
                allHosts.chunked(MAX_PARALLEL_SCANS).forEach { hostBatch ->
                    Log.d(TAG, "🔄 Scanning batch of ${hostBatch.size} hosts...")
                    
                    val jobs = hostBatch.map { ip ->
                        async {
                            try {
                                val device = checkHost(ip)
                                if (device != null) {
                                    Log.d(TAG, "✅ Found device: ${device.getDisplayName()} at $ip")
                                    systemLogger.i(TAG, "✅ Znaleziono urządzenie: ${device.getDisplayName()}")
                                    onDeviceFound(device)
                                    device
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                Log.v(TAG, "No response from $ip: ${e.message}")
                                null
                            }
                        }
                    }
                    
                    // Czekaj na wyniki tego batch'a
                    jobs.forEach { job ->
                        val device = job.await()
                        if (device != null) {
                            devices.add(device)
                        }
                    }
                }
            }
            
            val scanTime = System.currentTimeMillis() - startTime
            systemLogger.i(TAG, "✅ Skanowanie zakończone: znaleziono ${devices.size} urządzeń w ${scanTime}ms")
            
            // Sortuj po czasie odpowiedzi (najszybsze pierwsze)
            devices.sortedBy { it.responseTime }
            
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Błąd podczas skanowania", e)
            emptyList()
        }
    }
    
    /**
     * Sprawdza konkretny host
     */
    private suspend fun checkHost(ip: String): NetworkDevice? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val address = InetAddress.getByName(ip)
            
            // Szybki ping test
            if (!address.isReachable(SCAN_TIMEOUT)) {
                return@withContext null
            }
            
            val pingTime = System.currentTimeMillis() - startTime
            
            // Spróbuj pobrać hostname
            val hostname = try {
                address.canonicalHostName
            } catch (e: Exception) {
                null
            }
            
            // Sprawdź czy ma otwarty port parowania
            val hasPairingPort = checkPort(ip, PAIRING_PORT)
            
            val responseTime = System.currentTimeMillis() - startTime
            
            NetworkDevice(
                ip = ip,
                hostname = if (hostname != ip) hostname else null,
                isReachable = true,
                hasPairingPort = hasPairingPort,
                responseTime = responseTime
            )
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Sprawdza czy port jest otwarty (dla wykrywania urządzenia dziecka)
     */
    private fun checkPort(ip: String, port: Int): Boolean {
        return try {
            Log.v(TAG, "Checking port $port on $ip...")
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), SCAN_TIMEOUT)
            socket.close()
            Log.d(TAG, "✅ Port $port is OPEN on $ip")
            true
        } catch (e: Exception) {
            Log.v(TAG, "Port $port closed on $ip: ${e.javaClass.simpleName}")
            false
        }
    }
    
    /**
     * Szybkie sprawdzenie tylko urządzeń z otwartym portem parowania
     * Używane gdy szukamy konkretnie urządzenia dziecka
     */
    suspend fun scanForPairingDevices(onDeviceFound: (NetworkDevice) -> Unit): List<NetworkDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<NetworkDevice>()
        val subnet = getLocalSubnet()
        
        if (subnet == null) {
            Log.e(TAG, "❌ Cannot get local subnet for pairing scan")
            systemLogger.e(TAG, "❌ Nie można pobrać podsieci dla skanowania parowania")
            return@withContext emptyList()
        }
        
        Log.d(TAG, "🔍 Scanning for pairing devices on port $PAIRING_PORT in $subnet.0/24")
        systemLogger.i(TAG, "🔍 Skanowanie urządzeń z portem parowania ($PAIRING_PORT)")
        val startTime = System.currentTimeMillis()
        
        try {
            coroutineScope {
                val allHosts = (1..254).map { "$subnet.$it" }
                var scannedCount = 0
                
                // Skanuj w mniejszych batch'ach dla port checking
                allHosts.chunked(30).forEach { hostBatch ->
                    Log.d(TAG, "🔄 Checking ports on batch ${scannedCount + 1}-${scannedCount + hostBatch.size}/254...")
                    
                    val jobs = hostBatch.map { ip ->
                        async {
                            try {
                                // Najpierw sprawdź port (szybsze)
                                if (checkPort(ip, PAIRING_PORT)) {
                                    Log.d(TAG, "🎯 Found open pairing port on $ip, getting device info...")
                                    val device = checkHost(ip)
                                    if (device != null) {
                                        Log.i(TAG, "✅ Pairing device found: ${device.getDisplayName()}")
                                        systemLogger.i(TAG, "✅ Znaleziono urządzenie z portem parowania: ${device.getDisplayName()}")
                                        onDeviceFound(device)
                                        device
                                    } else {
                                        null
                                    }
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                Log.v(TAG, "Error checking $ip: ${e.message}")
                                null
                            }
                        }
                    }
                    
                    // Czekaj na wyniki tego batch'a
                    jobs.forEach { job ->
                        val device = job.await()
                        if (device != null) {
                            devices.add(device)
                        }
                    }
                    
                    scannedCount += hostBatch.size
                }
            }
            
            val scanTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Pairing scan completed: ${devices.size} devices found in ${scanTime}ms")
            systemLogger.i(TAG, "✅ Znaleziono ${devices.size} urządzeń z portem parowania w ${scanTime}ms")
            return@withContext devices.sortedBy { it.responseTime }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scanning pairing devices", e)
            systemLogger.e(TAG, "❌ Błąd skanowania urządzeń parowania", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Pobiera informacje o bieżącej sieci WiFi
     */
    fun getWifiInfo(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            
            val ssid = wifiInfo.ssid?.replace("\"", "") ?: "Unknown"
            val ip = formatIpAddress(wifiInfo.ipAddress)
            
            "SSID: $ssid, IP: $ip"
        } catch (e: Exception) {
            systemLogger.e(TAG, "Error getting WiFi info", e)
            null
        }
    }
    
    private fun formatIpAddress(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xFF,
            ip shr 8 and 0xFF,
            ip shr 16 and 0xFF,
            ip shr 24 and 0xFF
        )
    }
}
