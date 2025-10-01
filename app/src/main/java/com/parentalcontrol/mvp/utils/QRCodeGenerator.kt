package com.parentalcontrol.mvp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.parentalcontrol.mvp.model.DeviceType
import com.parentalcontrol.mvp.model.PairingData
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.*

class QRCodeGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "QRCodeGenerator"
        private const val QR_CODE_SIZE = 512
        private const val DEFAULT_PORT = 8080
        private const val PAIRING_CODE_LENGTH = 6
    }
    
    private val gson = Gson()
    private val random = SecureRandom()
    
    /**
     * Generuje kod QR dla parowania urządzenia rodzica
     */
    fun generateParentPairingQR(): Bitmap? {
        return try {
            val pairingData = createPairingData(DeviceType.PARENT)
            val qrContent = gson.toJson(pairingData)
            
            Log.d(TAG, "Generated parent pairing QR: $qrContent")
            generateQRCodeBitmap(qrContent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating parent pairing QR", e)
            null
        }
    }
    
    /**
     * Generuje kod QR dla parowania urządzenia dziecka
     */
    fun generateChildPairingQR(): Bitmap? {
        return try {
            val pairingData = createPairingData(DeviceType.CHILD)
            val qrContent = gson.toJson(pairingData)
            
            Log.d(TAG, "Generated child pairing QR: $qrContent")
            generateQRCodeBitmap(qrContent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating child pairing QR", e)
            null
        }
    }
    
    /**
     * Tworzy dane parowania dla danego typu urządzenia
     */
    private fun createPairingData(deviceType: DeviceType): PairingData {
        val deviceName = getDeviceName()
        val ipAddress = getLocalIPAddress() ?: "192.168.1.100"
        val securityKey = generateSecurityKey()
        val pairingCode = generatePairingCode()
        val wifiSSID = getCurrentWifiSSID()
        
        return PairingData(
            deviceName = deviceName,
            deviceType = deviceType,
            ipAddress = ipAddress,
            port = DEFAULT_PORT,
            securityKey = securityKey,
            pairingCode = pairingCode,
            wifiSSID = wifiSSID
        )
    }
    
    /**
     * Generuje bitmap kodu QR z podanej zawartości
     */
    private fun generateQRCodeBitmap(content: String): Bitmap? {
        return try {
            val writer = MultiFormatWriter()
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1
            
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hints)
            convertBitMatrixToBitmap(bitMatrix)
            
        } catch (e: WriterException) {
            Log.e(TAG, "Error generating QR code bitmap", e)
            null
        }
    }
    
    /**
     * Konwertuje BitMatrix na Bitmap
     */
    private fun convertBitMatrixToBitmap(bitMatrix: BitMatrix): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
    
    /**
     * Pobiera nazwę urządzenia
     */
    private fun getDeviceName(): String {
        return try {
            Settings.Global.getString(context.contentResolver, "device_name") 
                ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "${Build.MANUFACTURER} ${Build.MODEL}"
        } catch (e: Exception) {
            "${Build.MANUFACTURER} ${Build.MODEL}"
        }
    }
    
    /**
     * Pobiera lokalny adres IP urządzenia w sieci WiFi
     */
    private fun getLocalIPAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress
                        // Preferuj adresy z sieci prywatnych
                        if (ip != null && (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }
    
    /**
     * Pobiera aktualny SSID sieci WiFi
     */
    private fun getCurrentWifiSSID(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            
            var ssid = wifiInfo.ssid
            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            ssid
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi SSID", e)
            null
        }
    }
    
    /**
     * Generuje bezpieczny klucz szyfrowania
     */
    private fun generateSecurityKey(): String {
        val keyBytes = ByteArray(32) // 256-bit klucz
        random.nextBytes(keyBytes)
        return Base64.getEncoder().encodeToString(keyBytes)
    }
    
    /**
     * Generuje kod parowania (6-cyfrowy)
     */
    private fun generatePairingCode(): String {
        val code = StringBuilder()
        repeat(PAIRING_CODE_LENGTH) {
            code.append(random.nextInt(10))
        }
        return code.toString()
    }
    
    /**
     * Parsuje dane parowania z zawartości QR kodu
     */
    fun parsePairingData(qrContent: String): PairingData? {
        return try {
            gson.fromJson(qrContent, PairingData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing pairing data", e)
            null
        }
    }
    
    /**
     * Sprawdza czy urządzenia są w tej samej sieci WiFi
     */
    fun isInSameNetwork(pairingData: PairingData): Boolean {
        val currentSSID = getCurrentWifiSSID()
        return currentSSID != null && currentSSID == pairingData.wifiSSID
    }
    
    /**
     * Sprawdza czy adres IP jest w tym samym zakresie sieci
     */
    fun isInSameSubnet(pairingData: PairingData): Boolean {
        val localIP = getLocalIPAddress() ?: return false
        val remoteIP = pairingData.ipAddress
        
        // Prosta heurystyka dla sieci /24 (255.255.255.0)
        val localParts = localIP.split(".")
        val remoteParts = remoteIP.split(".")
        
        if (localParts.size != 4 || remoteParts.size != 4) return false
        
        return localParts[0] == remoteParts[0] && 
               localParts[1] == remoteParts[1] && 
               localParts[2] == remoteParts[2]
    }
}
