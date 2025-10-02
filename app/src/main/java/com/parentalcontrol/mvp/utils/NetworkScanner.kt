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
        private const val SCAN_TIMEOUT = 500 // ms - krótki timeout dla szybkości
        private const val PAIRING_PORT = 8080 // Port używany do parowania
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
        val subnet = getLocalSubnet() ?: return@withContext emptyList()
        
        systemLogger.i(TAG, "🔍 Rozpoczęcie szybkiego skanowania sieci $subnet.0/24")
        val startTime = System.currentTimeMillis()
        
        try {
            // Skanuj 1-254 (pomijamy .0 i .255)
            coroutineScope {
                val jobs = (1..254).map { host ->
                    val ip = "$subnet.$host"
                    
                    // Asynchroniczne sprawdzenie każdego hosta
                    async {
                        try {
                            val device = checkHost(ip)
                            if (device != null) {
                                systemLogger.i(TAG, "✅ Znaleziono urządzenie: ${device.getDisplayName()}")
                                onDeviceFound(device)
                                device
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                
                // Czekaj na wszystkie wyniki
                jobs.forEach { job ->
                    val device = job.await()
                    if (device != null) {
                        devices.add(device)
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
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), SCAN_TIMEOUT)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Szybkie sprawdzenie tylko urządzeń z otwartym portem parowania
     * Używane gdy szukamy konkretnie urządzenia dziecka
     */
    suspend fun scanForPairingDevices(onDeviceFound: (NetworkDevice) -> Unit): List<NetworkDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<NetworkDevice>()
        val subnet = getLocalSubnet() ?: return@withContext emptyList()
        
        systemLogger.i(TAG, "🔍 Skanowanie urządzeń z portem parowania ($PAIRING_PORT)")
        
        try {
            coroutineScope {
                val jobs = (1..254).map { host ->
                    val ip = "$subnet.$host"
                    
                    async {
                        try {
                            // Najpierw sprawdź port (szybsze)
                            if (checkPort(ip, PAIRING_PORT)) {
                                val device = checkHost(ip)
                                if (device != null) {
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
                            null
                        }
                    }
                }
                
                jobs.forEach { job ->
                    val device = job.await()
                    if (device != null) {
                        devices.add(device)
                    }
                }
            }
            
            systemLogger.i(TAG, "✅ Znaleziono ${devices.size} urządzeń z portem parowania")
            devices.sortedBy { it.responseTime }
            
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Błąd skanowania urządzeń parowania", e)
            emptyList()
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
