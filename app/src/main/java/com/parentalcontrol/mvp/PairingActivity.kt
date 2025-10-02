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
    private var deviceType: DeviceType = DeviceType.CHILD
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Pobierz typ urządzenia z Intent
        deviceType = intent.getSerializableExtra(EXTRA_DEVICE_TYPE) as? DeviceType ?: DeviceType.CHILD
        
        // Inicjalizuj serwisy
        qrCodeGenerator = QRCodeGenerator(this)
        pairingService = PairingService(this)
        
        setupUI()
        setupClickListeners()
        
        // Automatycznie wygeneruj QR kod dla tego urządzenia
        generateQRCode()
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
    
    private fun generateQRCode() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Generowanie QR kodu dla typu urządzenia: $deviceType")
                binding.statusText.text = getString(R.string.generating_qr_code)
                showLoading(true)
                
                // Wygeneruj dane parowania dla tego urządzenia
                val pairingData = qrCodeGenerator.generatePairingData(deviceType)
                Log.d(TAG, "Dane parowania wygenerowane: $pairingData")
                
                if (pairingData != null) {
                    Log.d(TAG, "Generowanie QR bitmap dla danych: ${pairingData.deviceName}, typ: ${pairingData.deviceType}")
                    // Wygeneruj bitmap QR kodu w wysokiej rozdzielczości dla pełnej szerokości ekranu
                    val qrBitmap = qrCodeGenerator.generateQRCode(pairingData, 1024, 1024)
                    Log.d(TAG, "QR bitmap wygenerowany: ${qrBitmap != null}, rozmiar: ${qrBitmap?.width}x${qrBitmap?.height}")
                    
                    if (qrBitmap != null) {
                        // Wyświetl QR kod
                        binding.qrCodeImage.setImageBitmap(qrBitmap)
                        binding.statusText.text = getString(R.string.qr_code_ready)
                        Log.d(TAG, "QR kod pomyślnie wyświetlony dla urządzenia ${deviceType}")
                        
                        // Pokaż szczegóły urządzenia
                        showDeviceDetails(pairingData)
                        
                        // Rozpocznij nasłuchiwanie na połączenia
                        startPairingServer()
                        
                    } else {
                        binding.statusText.text = getString(R.string.error_generating_qr)
                        Log.e(TAG, "Failed to generate QR code bitmap for device type: $deviceType")
                    }
                } else {
                    binding.statusText.text = getString(R.string.error_device_info)
                    Log.e(TAG, "Failed to generate pairing data")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating QR code", e)
                binding.statusText.text = getString(R.string.error_generating_qr)
                Toast.makeText(this@PairingActivity, 
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
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
                Log.d(TAG, "🚀 Starting pairing server on port ${PairingConfig.PAIRING_PORT} for CHILD device...")
                
                // Najpierw zatrzymaj ewentualny poprzedni serwer
                pairingService.cleanup()
                
                // Poczekaj chwilę aby port został zwolniony
                kotlinx.coroutines.delay(500)
                
                // Uruchom tylko serwer nasłuchujący (bez łączenia się z innym urządzeniem)
                pairingService.startListeningServer { success, message ->
                    runOnUiThread {
                        if (success) {
                            Log.d(TAG, "✅ Pairing server started successfully on port ${PairingConfig.PAIRING_PORT}")
                            binding.statusText.text = "✅ Serwer uruchomiony - Port ${PairingConfig.PAIRING_PORT} OTWARTY\nOczekiwanie na połączenie od rodzica..."
                            Toast.makeText(this@PairingActivity, "✅ Port ${PairingConfig.PAIRING_PORT} otwarty - gotowy do parowania!", Toast.LENGTH_LONG).show()
                        } else {
                            Log.e(TAG, "❌ Failed to start pairing server: $message")
                            binding.statusText.text = "❌ Błąd serwera: $message"
                            Toast.makeText(this@PairingActivity, "❌ Błąd: $message", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting pairing server", e)
                runOnUiThread {
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
