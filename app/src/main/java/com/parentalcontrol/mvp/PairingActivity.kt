package com.parentalcontrol.mvp

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.parentalcontrol.mvp.config.PairingConfig
import com.parentalcontrol.mvp.databinding.ActivityPairingBinding
import com.parentalcontrol.mvp.model.DeviceType
import com.parentalcontrol.mvp.model.PairingData
import com.parentalcontrol.mvp.service.PairingService
import com.parentalcontrol.mvp.utils.QRCodeGenerator
import kotlinx.coroutines.launch

class PairingActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PairingActivity"
        const val EXTRA_DEVICE_TYPE = "device_type"
        const val REQUEST_CODE_QR_SCAN = 1001
    }
    
    private lateinit var binding: ActivityPairingBinding
    private lateinit var qrCodeGenerator: QRCodeGenerator
    private lateinit var pairingService: PairingService
    private lateinit var systemLogger: com.parentalcontrol.mvp.utils.SystemLogger
    private var deviceType: DeviceType = DeviceType.CHILD
    private var activePort: Int? = null  // Port na którym działa serwer
    private var pairingData: PairingData? = null  // Dane parowania po uruchomieniu serwera
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Pobierz typ urządzenia z Intent
        deviceType = intent.getSerializableExtra(EXTRA_DEVICE_TYPE) as? DeviceType ?: DeviceType.CHILD
        
        // Inicjalizuj serwisy
        systemLogger = com.parentalcontrol.mvp.utils.SystemLogger.getInstance(this)
        qrCodeGenerator = QRCodeGenerator(this)
        pairingService = PairingService(this)
        
        // Log rozpoczęcia parowania
        systemLogger.i(TAG, "📱 PAROWANIE: Rozpoczęto - typ urządzenia: ${if (deviceType == DeviceType.CHILD) "DZIECKO" else "RODZIC"}")
        
        setupUI()
        setupClickListeners()
        
        // Dla urządzenia DZIECKA: najpierw uruchom serwer, potem generuj QR
        // Dla urządzenia RODZICA: od razu pokaż skaner QR
        if (deviceType == DeviceType.CHILD) {
            startPairingServer()
        } else {
            binding.statusText.text = "Zeskanuj kod QR z urządzenia dziecka"
        }
    }
    
    private fun setupUI() {
        // Ustaw tytuł w zależności od typu urządzenia
        val titleResId = when (deviceType) {
            DeviceType.PARENT -> R.string.parent_pairing_title
            DeviceType.CHILD -> R.string.child_pairing_title
        }
        
        binding.titleText.setText(titleResId)
        
        // Ustaw instrukcje
        val instructionsResId = when (deviceType) {
            DeviceType.PARENT -> R.string.parent_pairing_instructions
            DeviceType.CHILD -> R.string.child_pairing_instructions
        }
        
        binding.instructionsText.setText(instructionsResId)
        
        // Ustaw tekst przycisku skanowania
        when (deviceType) {
            DeviceType.PARENT -> {
                binding.scanQrButton.text = getString(R.string.scan_child_qr)
            }
            DeviceType.CHILD -> {
                binding.scanQrButton.text = getString(R.string.scan_parent_qr)
            }
        }
    }
    
    private fun setupClickListeners() {
        // QR kod jest automatycznie generowany - nie potrzeba przycisku generate
        
        binding.scanQrButton.setOnClickListener {
            startQRScanner()
        }
        
        // Przycisk refresh jest ukryty, ale może być użyty w przyszłości
        binding.refreshQrButton.setOnClickListener {
            generateQRCode()
        }
        
        binding.backButton.setOnClickListener {
            finish()
        }
        
        binding.unpairButton.setOnClickListener {
            unpairDevice()
        }
    }
    
    /**
     * Generuje QR kod z pełnymi informacjami o serwerze
     * WYMAGANE: activePort i pairingData muszą być ustawione (po uruchomieniu serwera)
     */
    private fun generateQRCode() {
        lifecycleScope.launch {
            try {
                val port = activePort
                val data = pairingData
                
                if (port == null || data == null) {
                    Log.e(TAG, "Cannot generate QR: port=$port, data=$data")
                    binding.statusText.text = "❌ Błąd: Brak danych serwera"
                    return@launch
                }
                
                Log.d(TAG, "Generowanie QR kodu z portem $port")
                systemLogger.i(TAG, "📱 Generowanie kodu QR z danymi:")
                binding.statusText.text = "Generowanie kodu QR..."
                showLoading(true)
                
                // Aktualizuj dane parowania z rzeczywistym portem
                val updatedData = data.copy(port = port)
                
                Log.d(TAG, "Generowanie QR bitmap dla: IP=${updatedData.ipAddress}, Port=$port, WiFi=${updatedData.wifiSSID}")
                systemLogger.i(TAG, "  📡 IP: ${updatedData.ipAddress}")
                systemLogger.i(TAG, "  🔌 Port: $port")
                systemLogger.i(TAG, "  📶 WiFi: ${updatedData.wifiSSID}")
                systemLogger.i(TAG, "  🔑 Kod: ${updatedData.pairingCode}")
                
                val qrBitmap = qrCodeGenerator.generateQRCode(updatedData, 1024, 1024)
                
                if (qrBitmap != null) {
                    // Wyświetl QR kod
                    binding.qrCodeImage.setImageBitmap(qrBitmap)
                    binding.statusText.text = "✅ Gotowy do parowania!"
                    Log.d(TAG, "QR kod pomyślnie wyświetlony")
                    systemLogger.i(TAG, "✅ KOD QR WYGENEROWANY - Urządzenie gotowe!")
                    
                    // Pokaż szczegóły urządzenia z pełną walidacją
                    showDeviceDetailsWithValidation(updatedData)
                    
                } else {
                    binding.statusText.text = "❌ Błąd generowania QR"
                    Log.e(TAG, "Failed to generate QR code bitmap")
                    systemLogger.e(TAG, "❌ Błąd generowania kodu QR")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating QR code", e)
                binding.statusText.text = "❌ Błąd: ${e.message}"
                Toast.makeText(this@PairingActivity, 
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }
    
    /**
     * Pokazuje szczegóły urządzenia z pełną walidacją WiFi
     */
    private fun showDeviceDetailsWithValidation(pairingData: PairingData) {
        val details = buildString {
            append("📱 ${getString(R.string.device_name)}: ${pairingData.deviceName}\n")
            append("🔢 ${getString(R.string.device_type)}: ${getDeviceTypeString(pairingData.deviceType)}\n")
            append("\n")
            append("━━━━━━━━━━━━━━━━━━━━━\n")
            append("🌐 INFORMACJE SIECIOWE:\n")
            append("━━━━━━━━━━━━━━━━━━━━━\n")
            append("📡 IP: ${pairingData.ipAddress}\n")
            append("🔌 Port: ${pairingData.port}\n")
            append("📶 WiFi: ${pairingData.wifiSSID ?: "N/A"}\n")
            append("\n")
            append("━━━━━━━━━━━━━━━━━━━━━\n")
            append("🔐 BEZPIECZEŃSTWO:\n")
            append("━━━━━━━━━━━━━━━━━━━━━\n")
            append("🔑 ${getString(R.string.pairing_code)}: ${pairingData.pairingCode}\n")
            append("\n")
            append("━━━━━━━━━━━━━━━━━━━━━\n")
            append("✅ WALIDACJA:\n")
            append("━━━━━━━━━━━━━━━━━━━━━\n")
            append("Przed sparowaniem sprawdź:\n")
            append("✓ Urządzenie rodzica w WiFi:\n")
            append("   \"${pairingData.wifiSSID}\"\n")
            append("✓ Port ${pairingData.port} został\n")
            append("   przetestowany i działa\n")
            append("✓ Serwer nasłuchuje na:\n")
            append("   ${pairingData.ipAddress}:${pairingData.port}")
        }
        
        binding.deviceDetailsText.text = details
        binding.deviceDetailsCard.visibility = android.view.View.VISIBLE
        
        Log.d(TAG, "Device details with validation shown: ${pairingData.ipAddress}:${pairingData.port} on WiFi ${pairingData.wifiSSID}")
    }
    
    private fun showDeviceDetails(pairingData: PairingData) {
        val details = buildString {
            append("${getString(R.string.device_name)}: ${pairingData.deviceName}\n")
            append("${getString(R.string.device_type)}: ${getDeviceTypeString(pairingData.deviceType)}\n")
            append("${getString(R.string.ip_address)}: ${pairingData.ipAddress}\n")
            append("${getString(R.string.wifi_network)}: ${pairingData.wifiSSID ?: "N/A"}\n")
            append("${getString(R.string.pairing_code)}: ${pairingData.pairingCode}")
        }
        
        binding.deviceDetailsText.text = details
        binding.deviceDetailsCard.visibility = android.view.View.VISIBLE
    }
    
    private fun getDeviceTypeString(type: DeviceType): String {
        return when (type) {
            DeviceType.PARENT -> getString(R.string.parent_device)
            DeviceType.CHILD -> getString(R.string.child_device)
        }
    }
    
    private fun startPairingServer() {
        // Tylko dla urządzenia DZIECKA - uruchom serwer nasłuchujący
        if (deviceType != DeviceType.CHILD) {
            Log.d(TAG, "Not a child device - server not needed")
            return
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🚀 Starting pairing server - trying available ports...")
                systemLogger.i(TAG, "🔌 PAROWANIE DZIECKA: Szukam wolnego portu...")
                binding.statusText.text = "🔌 Szukam wolnego portu..."
                showLoading(true)
                
                // Najpierw zatrzymaj ewentualny poprzedni serwer
                systemLogger.i(TAG, "🛑 Zatrzymywanie poprzedniego serwera...")
                pairingService.cleanup()
                
                // Poczekaj chwilę aby port został zwolniony
                kotlinx.coroutines.delay(500)
                
                // Przygotuj dane parowania (będą zaktualizowane z rzeczywistym portem)
                pairingData = PairingData(
                    deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID),
                    deviceName = android.os.Build.MODEL,
                    deviceType = deviceType,
                    ipAddress = getLocalIpAddress() ?: "Unknown",
                    port = 0,  // Zostanie zaktualizowany po znalezieniu wolnego portu
                    securityKey = generateSecurityKey(),
                    pairingCode = generatePairingCode(),
                    wifiSSID = getWifiSSID()
                )
                
                // Uruchom serwer (automatycznie próbuje wszystkich portów)
                Log.d(TAG, "📞 Calling startListeningServer...")
                systemLogger.i(TAG, "🚀 Uruchamianie serwera nasłuchującego...")
                pairingService.startListeningServer { success, message, port ->
                    Log.d(TAG, "📞 Callback received: success=$success, port=$port, message=$message")
                    systemLogger.i(TAG, "📞 Odpowiedź z serwera: sukces=$success, port=$port")
                    runOnUiThread {
                        showLoading(false)
                        
                        if (success && port != null) {
                            activePort = port
                            Log.d(TAG, "✅ Pairing server started successfully on port $port")
                            systemLogger.i(TAG, "✅ SUKCES PAROWANIA: Serwer działa na porcie $port!")
                            systemLogger.i(TAG, "📱 Generowanie kodu QR...")
                            binding.statusText.text = "✅ Serwer uruchomiony - Port $port OTWARTY\nGenerowanie kodu QR..."
                            Toast.makeText(this@PairingActivity, "✅ Port $port otwarty!", Toast.LENGTH_LONG).show()
                            
                            // Teraz wygeneruj QR kod z potwierdzonym portem
                            generateQRCode()
                        } else {
                            Log.e(TAG, "❌ Failed to start pairing server: $message")
                            systemLogger.e(TAG, "❌ BŁĄD PAROWANIA: $message")
                            binding.statusText.text = "❌ Błąd serwera:\n$message"
                            Toast.makeText(this@PairingActivity, "❌ Nie można uruchomić serwera", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                Log.d(TAG, "📞 startListeningServer called, waiting for callback...")
                systemLogger.i(TAG, "⏳ Oczekiwanie na rezultat uruchomienia serwera...")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting pairing server", e)
                runOnUiThread {
                    showLoading(false)
                    binding.statusText.text = "❌ Błąd uruchamiania serwera: ${e.message}"
                    Toast.makeText(this@PairingActivity, "❌ Błąd serwera: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun getLocalIpAddress(): String? {
        try {
            val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            return String.format("%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
            return null
        }
    }
    
    private fun getWifiSSID(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            wifiManager.connectionInfo.ssid?.replace("\"", "")
        } catch (e: Exception) {
            null
        }
    }
    
    private fun generatePairingCode(): String {
        return (1000..9999).random().toString()
    }
    
    private fun generateSecurityKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32)
            .map { chars.random() }
            .joinToString("")
    }
    
    private fun startQRScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_QR_SCAN)
    }
    
    private fun unpairDevice() {
        try {
            pairingService.unpair()
            binding.statusText.text = getString(R.string.device_unpaired)
            
            // Ukryj szczegóły sparowanego urządzenia
            binding.pairedDeviceCard.visibility = android.view.View.GONE
            
            Toast.makeText(this, R.string.device_unpaired_success, Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error unpairing device", e)
            Toast.makeText(this, R.string.error_unpairing, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        // generateQrButton został usunięty - QR kod jest automatycznie widoczny
        binding.scanQrButton.isEnabled = !show
        binding.refreshQrButton.isEnabled = !show
    }
    
    private fun updatePairingStatus() {
        val status = pairingService.getPairingStatus()
        
        if (status.isPaired) {
            // Pokaż informacje o sparowanym urządzeniu
            binding.pairedDeviceCard.visibility = android.view.View.VISIBLE
            
            val pairedInfo = buildString {
                append("${getString(R.string.paired_with)}: ${status.pairedDeviceName}\n")
                append("${getString(R.string.device_type)}: ${getDeviceTypeString(status.pairedDeviceType ?: DeviceType.CHILD)}\n")
                append("${getString(R.string.connection_status_label)}: ${getConnectionStatusString(status.connectionStatus)}\n")
                
                status.lastHeartbeat?.let { heartbeat ->
                    val timeAgo = (System.currentTimeMillis() - heartbeat) / 1000
                    append("${getString(R.string.last_heartbeat_label)}: ${timeAgo}s ago")
                }
            }
            
            binding.pairedDeviceText.text = pairedInfo
            binding.unpairButton.visibility = android.view.View.VISIBLE
            
        } else {
            binding.pairedDeviceCard.visibility = android.view.View.GONE
            binding.unpairButton.visibility = android.view.View.GONE
        }
    }
    
    private fun getConnectionStatusString(status: com.parentalcontrol.mvp.model.ConnectionStatus): String {
        return when (status) {
            com.parentalcontrol.mvp.model.ConnectionStatus.CONNECTED -> getString(R.string.status_connected)
            com.parentalcontrol.mvp.model.ConnectionStatus.DISCONNECTED -> getString(R.string.status_disconnected)
            com.parentalcontrol.mvp.model.ConnectionStatus.CONNECTING -> getString(R.string.status_connecting)
            com.parentalcontrol.mvp.model.ConnectionStatus.ERROR -> getString(R.string.status_error)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CODE_QR_SCAN -> {
                when (resultCode) {
                    QRScannerActivity.RESULT_PAIRING_SUCCESS -> {
                        // Parowanie pomyślne
                        binding.statusText.text = getString(R.string.pairing_successful)
                        updatePairingStatus()
                        
                        Toast.makeText(this, R.string.pairing_successful, Toast.LENGTH_LONG).show()
                    }
                    
                    QRScannerActivity.RESULT_PAIRING_FAILED -> {
                        // Parowanie nieudane
                        val errorMessage = data?.getStringExtra("error_message") 
                            ?: getString(R.string.pairing_failed_unknown)
                        
                        binding.statusText.text = getString(R.string.pairing_failed)
                        
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updatePairingStatus()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup - nie zamykaj PairingService, bo może być używany przez inne aktywności
    }
}
