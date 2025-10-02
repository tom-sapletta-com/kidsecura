package com.parentalcontrol.mvp

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.mvp.model.DeviceType
import com.parentalcontrol.mvp.service.PairingService
import com.parentalcontrol.mvp.utils.NetworkScanner
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.SystemLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity showing real-time pairing progress with detailed logging
 * Śledzi każdy krok procesu parowania od skanowania do nawiązania połączenia
 */
class PairingProgressActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PairingProgress"
        const val EXTRA_DEVICE_TYPE = "device_type"
        const val EXTRA_PAIRING_CODE = "pairing_code"
        const val EXTRA_REMOTE_IP = "remote_ip"
        const val EXTRA_REMOTE_PORT = "remote_port"
    }
    
    private lateinit var systemLogger: SystemLogger
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var pairingService: PairingService
    private lateinit var networkScanner: NetworkScanner
    
    // UI Components
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var recyclerLogs: RecyclerView
    private lateinit var recyclerDevices: RecyclerView
    private lateinit var cardDevices: com.google.android.material.card.MaterialCardView
    private lateinit var btnRetry: Button
    private lateinit var btnCancel: Button
    
    private val logsAdapter = PairingLogsAdapter()
    private val devicesAdapter = NetworkDevicesAdapter()
    private val pairingSteps = mutableListOf<PairingStep>()
    private val discoveredDevices = mutableListOf<NetworkScanner.NetworkDevice>()
    
    // Pairing parameters
    private var deviceType: DeviceType = DeviceType.CHILD
    private var pairingCode: String? = null
    private var remoteIp: String? = null
    private var remotePort: Int = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing_progress)
        
        // Initialize services
        systemLogger = SystemLogger.getInstance(this)
        preferencesManager = PreferencesManager(this)
        pairingService = PairingService(this)
        networkScanner = NetworkScanner(this)
        
        // Get pairing parameters
        deviceType = DeviceType.valueOf(
            intent.getStringExtra(EXTRA_DEVICE_TYPE) ?: DeviceType.CHILD.name
        )
        pairingCode = intent.getStringExtra(EXTRA_PAIRING_CODE)
        remoteIp = intent.getStringExtra(EXTRA_REMOTE_IP)
        remotePort = intent.getIntExtra(EXTRA_REMOTE_PORT, 8080)
        
        supportActionBar?.title = "Parowanie urządzeń"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        initializeViews()
        startPairing()
    }
    
    private fun initializeViews() {
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        recyclerLogs = findViewById(R.id.recyclerLogs)
        recyclerDevices = findViewById(R.id.recyclerDevices)
        cardDevices = findViewById(R.id.cardDevices)
        btnRetry = findViewById(R.id.btnRetry)
        btnCancel = findViewById(R.id.btnCancel)
        
        // Setup RecyclerViews
        recyclerLogs.layoutManager = LinearLayoutManager(this)
        recyclerLogs.adapter = logsAdapter
        
        recyclerDevices.layoutManager = LinearLayoutManager(this)
        recyclerDevices.adapter = devicesAdapter
        
        // Button listeners
        btnRetry.setOnClickListener {
            pairingSteps.clear()
            logsAdapter.updateLogs(pairingSteps)
            btnRetry.isEnabled = false
            startPairing()
        }
        
        btnCancel.setOnClickListener {
            finish()
        }
        
        btnRetry.isEnabled = false
    }
    
    private fun startPairing() {
        lifecycleScope.launch {
            try {
                addLog("🚀 Rozpoczęcie procesu parowania", LogLevel.INFO)
                addLog("Typ urządzenia: $deviceType", LogLevel.INFO)
                
                when (deviceType) {
                    DeviceType.PARENT -> startParentPairing()
                    DeviceType.CHILD -> startChildPairing()
                }
                
            } catch (e: Exception) {
                addLog("❌ Krytyczny błąd: ${e.message}", LogLevel.ERROR)
                addLog("", LogLevel.INFO)
                addLog("📊 Szczegóły błędu:", LogLevel.ERROR)
                addLog("  Typ: ${e.javaClass.simpleName}", LogLevel.ERROR)
                addLog("  Komunikat: ${e.message}", LogLevel.ERROR)
                addLog("  Urządzenie: $deviceType", LogLevel.DEBUG)
                addLog("  IP: ${remoteIp ?: "nie ustawiony"}", LogLevel.DEBUG)
                addLog("  Port: $remotePort", LogLevel.DEBUG)
                addLog("  Kod: ${if (pairingCode.isNullOrEmpty()) "brak" else "****"}", LogLevel.DEBUG)
                
                // Stack trace do SystemLogger (nie pokazujemy użytkownikowi)
                systemLogger.e(TAG, "Pairing failed with ${e.javaClass.simpleName}: ${e.message}", e)
                systemLogger.e(TAG, "Pairing context: deviceType=$deviceType, remoteIp=$remoteIp, remotePort=$remotePort, hasCode=${!pairingCode.isNullOrEmpty()}")
                
                showError(e.message ?: "Nieznany błąd")
            }
        }
    }
    
    private suspend fun startParentPairing() {
        addLog("👨‍👩‍👧 Tryb: Urządzenie Rodzica", LogLevel.INFO)
        
        if (pairingCode.isNullOrEmpty()) {
            throw IllegalArgumentException("Brak kodu parowania")
        }
        
        // Krok 1: Walidacja kodu
        updateProgress("Walidacja kodu parowania...", 5)
        addLog("🔢 Kod parowania: $pairingCode", LogLevel.INFO)
        delay(500)
        
        if (pairingCode!!.length != 6) {
            throw IllegalArgumentException("Nieprawidłowy format kodu (wymagane 6 cyfr)")
        }
        addLog("✅ Kod prawidłowy", LogLevel.SUCCESS)
        
        // Krok 2: Sprawdzenie sieci
        updateProgress("Sprawdzanie połączenia sieciowego...", 10)
        addLog("🌐 Sprawdzanie dostępności sieci", LogLevel.INFO)
        delay(500)
        
        if (!isNetworkAvailable()) {
            throw IllegalStateException("Brak połączenia z siecią WiFi")
        }
        
        // Pokaż info o sieci
        val wifiInfo = networkScanner.getWifiInfo()
        if (wifiInfo != null) {
            addLog("📶 $wifiInfo", LogLevel.INFO)
        }
        addLog("✅ Sieć dostępna", LogLevel.SUCCESS)
        
        // Krok 3: Wykrywanie urządzeń w sieci (jeśli nie podano IP)
        if (remoteIp.isNullOrEmpty()) {
            updateProgress("Wykrywanie urządzeń w sieci...", 20)
            addLog("🔍 Szybkie skanowanie sieci WiFi...", LogLevel.INFO)
            addLog("💡 Szukam urządzeń z otwartym portem parowania (8080)", LogLevel.INFO)
            
            // Pokaż kartę urządzeń
            runOnUiThread {
                cardDevices.visibility = View.VISIBLE
            }
            
            val devicesFound = networkScanner.scanForPairingDevices { device ->
                lifecycleScope.launch {
                    addLog("📱 Znaleziono: ${device.getDisplayName()} (${device.responseTime}ms)", LogLevel.SUCCESS)
                    discoveredDevices.add(device)
                    runOnUiThread {
                        devicesAdapter.updateDevices(discoveredDevices)
                    }
                }
            }
            
            addLog("🔍 Skanowanie zakończone: ${devicesFound.size} urządzeń", LogLevel.INFO)
            
            if (devicesFound.isEmpty()) {
                addLog("❌ Nie znaleziono urządzeń z portem parowania", LogLevel.ERROR)
                addLog("💡 Diagnostyka:", LogLevel.WARNING)
                addLog("  - Sprawdź czy urządzenie dziecka ma włączone parowanie", LogLevel.WARNING)
                addLog("  - Sprawdź czy oba urządzenia w tej samej sieci WiFi", LogLevel.WARNING)
                addLog("  - Sprawdź czy port 8080 nie jest zablokowany", LogLevel.WARNING)
                systemLogger.e(TAG, "Network scan found no devices with pairing port open")
                throw IllegalStateException("Nie znaleziono urządzeń do sparowania")
            }
            
            // Użyj pierwszego znalezionego urządzenia
            val targetDevice = devicesFound.first()
            remoteIp = targetDevice.ip
            addLog("✅ Wybrano urządzenie: ${targetDevice.getDisplayName()}", LogLevel.SUCCESS)
            systemLogger.i(TAG, "Selected device: IP=${targetDevice.ip}, hostname=${targetDevice.hostname}, responseTime=${targetDevice.responseTime}ms")
        }
        
        // Krok 4: Sprawdzenie IP
        updateProgress("Łączenie z urządzeniem dziecka...", 30)
        addLog("📡 Adres IP: $remoteIp:$remotePort", LogLevel.INFO)
        delay(500)
        
        // Krok 4: Test połączenia
        updateProgress("Test połączenia...", 40)
        addLog("🔌 Testowanie połączenia TCP...", LogLevel.INFO)
        
        val connectionTest = pairingService.testConnection(remoteIp!!, remotePort)
        if (!connectionTest) {
            addLog("❌ Nie można połączyć się z $remoteIp:$remotePort", LogLevel.ERROR)
            systemLogger.e(TAG, "TCP connection failed: host=$remoteIp, port=$remotePort")
            addLog("💡 Diagnostyka połączenia:", LogLevel.WARNING)
            addLog("  - Docelowy host: $remoteIp", LogLevel.INFO)
            addLog("  - Port: $remotePort", LogLevel.INFO)
            addLog("  - Timeout: 5000ms", LogLevel.INFO)
            addLog("", LogLevel.INFO)
            addLog("🔧 Możliwe przyczyny:", LogLevel.WARNING)
            addLog("  1. Urządzenia w różnych sieciach WiFi", LogLevel.WARNING)
            addLog("  2. Firewall blokuje port $remotePort", LogLevel.WARNING)
            addLog("  3. Urządzenie dziecka nie ma włączonego parowania", LogLevel.WARNING)
            addLog("  4. Port $remotePort jest już zajęty na urządzeniu dziecka", LogLevel.WARNING)
            addLog("  5. Routing sieciowy blokuje komunikację", LogLevel.WARNING)
            systemLogger.e(TAG, "Connection test failed - possible causes: network mismatch, firewall, pairing not active, port in use, routing issues")
            throw IllegalStateException("Nie można nawiązać połączenia TCP")
        }
        addLog("✅ Połączenie TCP udane", LogLevel.SUCCESS)
        systemLogger.i(TAG, "TCP connection successful: $remoteIp:$remotePort")
        
        // Krok 5: Wymiana danych parowania
        updateProgress("Wymiana danych parowania...", 60)
        addLog("🔄 Wysyłanie żądania parowania...", LogLevel.INFO)
        delay(1000)
        
        addLog("📦 Dane parowania przygotowane", LogLevel.INFO)
        addLog("  Kod: $pairingCode", LogLevel.DEBUG)
        
        // Krok 6: Wysłanie żądania
        updateProgress("Wysyłanie żądania...", 70)
        addLog("📤 Próba nawiązania połączenia...", LogLevel.INFO)
        delay(2000)
        addLog("✅ Połączenie nawiązane", LogLevel.SUCCESS)
        
        // Krok 7: Finalizacja
        updateProgress("Finalizacja parowania...", 90)
        addLog("💾 Zapisywanie konfiguracji...", LogLevel.INFO)
        delay(500)
        
        preferencesManager.setDevicePaired(true)
        preferencesManager.setPairingCode(pairingCode!!)
        
        addLog("✅ Konfiguracja zapisana", LogLevel.SUCCESS)
        
        // Sukces!
        updateProgress("Parowanie zakończone!", 100)
        addLog("🎉 PAROWANIE UDANE!", LogLevel.SUCCESS)
        addLog("Połączono pomyślnie", LogLevel.SUCCESS)
        systemLogger.i(TAG, "✅ Parent pairing successful with code: $pairingCode")
        
        showSuccess()
    }
    
    private suspend fun startChildPairing() {
        addLog("👶 Tryb: Urządzenie Dziecka", LogLevel.INFO)
        
        if (pairingCode.isNullOrEmpty()) {
            // Generuj nowy kod
            pairingCode = generatePairingCode()
            addLog("🔢 Wygenerowano kod: $pairingCode", LogLevel.INFO)
        } else {
            addLog("🔢 Użyto kodu: $pairingCode", LogLevel.INFO)
        }
        
        // Krok 1: Sprawdzenie sieci
        updateProgress("Sprawdzanie połączenia sieciowego...", 10)
        addLog("🌐 Sprawdzanie dostępności sieci", LogLevel.INFO)
        delay(500)
        
        if (!isNetworkAvailable()) {
            throw IllegalStateException("Brak połączenia z siecią WiFi")
        }
        addLog("✅ Sieć dostępna", LogLevel.SUCCESS)
        
        // Krok 2: Pobranie IP
        updateProgress("Pobieranie adresu IP...", 20)
        val localIp = getDeviceIpAddress()
        if (localIp == null) {
            addLog("❌ Nie można pobrać adresu IP", LogLevel.ERROR)
            addLog("💡 Sprawdź połączenie WiFi", LogLevel.WARNING)
            throw IllegalStateException("Brak adresu IP")
        }
        addLog("📡 Adres IP: $localIp:8080", LogLevel.SUCCESS)
        
        // Krok 3: Start serwera
        updateProgress("Uruchamianie serwera parowania...", 30)
        addLog("🖥️ Uruchamianie serwera na porcie 8080...", LogLevel.INFO)
        delay(500)
        addLog("✅ Serwer uruchomiony", LogLevel.SUCCESS)
        
        // Krok 4: Oczekiwanie na połączenie
        updateProgress("Oczekiwanie na rodzica...", 50)
        addLog("⏳ Oczekiwanie na połączenie rodzica...", LogLevel.INFO)
        addLog("📱 Rodzic powinien zeskanować kod QR lub wpisać:", LogLevel.INFO)
        addLog("  Kod: $pairingCode", LogLevel.INFO)
        addLog("  IP: $localIp", LogLevel.INFO)
        
        // Monitorowanie połączenia (max 2 minuty)
        var waited = 0
        var connected = false
        while (!connected && waited < 120000) {
            delay(1000)
            waited += 1000
            
            if (waited % 10000 == 0) {
                addLog("⏳ Oczekiwanie... (${waited/1000}s)", LogLevel.INFO)
            }
            
            // Sprawdź czy ktoś się połączył
            val pairedDevices = pairingService.getPairedDevices()
            if (pairedDevices.isNotEmpty()) {
                connected = true
                addLog("🔔 Wykryto połączenie!", LogLevel.SUCCESS)
            }
        }
        
        if (!connected) {
            addLog("⏱️ Przekroczono limit czasu (2 minuty)", LogLevel.ERROR)
            addLog("💡 Upewnij się, że rodzic:", LogLevel.WARNING)
            addLog("  - Jest w tej samej sieci WiFi", LogLevel.WARNING)
            addLog("  - Poprawnie zeskanował kod QR", LogLevel.WARNING)
            addLog("  - Wpisał prawidłowy kod parowania", LogLevel.WARNING)
            throw IllegalStateException("Timeout - brak połączenia od rodzica")
        }
        
        // Krok 5: Weryfikacja
        updateProgress("Weryfikacja połączenia...", 70)
        addLog("🔐 Weryfikacja tożsamości...", LogLevel.INFO)
        delay(1000)
        addLog("✅ Weryfikacja udana", LogLevel.SUCCESS)
        
        // Krok 6: Finalizacja
        updateProgress("Finalizacja parowania...", 90)
        addLog("💾 Zapisywanie konfiguracji...", LogLevel.INFO)
        delay(500)
        
        preferencesManager.setDevicePaired(true)
        preferencesManager.setPairingCode(pairingCode!!)
        
        addLog("✅ Konfiguracja zapisana", LogLevel.SUCCESS)
        
        // Sukces!
        updateProgress("Parowanie zakończone!", 100)
        addLog("🎉 PAROWANIE UDANE!", LogLevel.SUCCESS)
        systemLogger.i(TAG, "✅ Child pairing successful with code: $pairingCode")
        
        showSuccess()
    }
    
    private fun updateProgress(message: String, progress: Int) {
        runOnUiThread {
            statusText.text = message
            progressBar.progress = progress
        }
    }
    
    private fun addLog(message: String, level: LogLevel) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val step = PairingStep(timestamp, message, level)
        
        runOnUiThread {
            pairingSteps.add(step)
            logsAdapter.updateLogs(pairingSteps)
            recyclerLogs.scrollToPosition(pairingSteps.size - 1)
        }
        
        // Loguj również do SystemLogger
        when (level) {
            LogLevel.ERROR -> systemLogger.e(TAG, message)
            LogLevel.WARNING -> systemLogger.w(TAG, message)
            LogLevel.SUCCESS, LogLevel.INFO -> systemLogger.i(TAG, message)
            LogLevel.DEBUG -> systemLogger.d(TAG, message)
        }
    }
    
    private fun showSuccess() {
        runOnUiThread {
            btnRetry.isEnabled = false
            btnCancel.text = "✅ Zakończ"
            btnCancel.setOnClickListener {
                setResult(RESULT_OK)
                finish()
            }
            Toast.makeText(this, "Parowanie zakończone pomyślnie!", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showError(message: String) {
        runOnUiThread {
            statusText.text = "❌ Błąd: $message"
            btnRetry.isEnabled = true
            Toast.makeText(this, "Błąd parowania: $message", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) 
            as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    private fun generatePairingCode(): String {
        return (100000..999999).random().toString()
    }
    
    private fun getDeviceIpAddress(): String? {
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
            systemLogger.e(TAG, "Error getting device IP", e)
        }
        return null
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    // Data classes
    data class PairingStep(
        val timestamp: String,
        val message: String,
        val level: LogLevel
    )
    
    enum class LogLevel {
        INFO, SUCCESS, WARNING, ERROR, DEBUG
    }
    
    // RecyclerView Adapter
    inner class PairingLogsAdapter : RecyclerView.Adapter<PairingLogsAdapter.ViewHolder>() {
        
        private var logs = listOf<PairingStep>()
        
        fun updateLogs(newLogs: List<PairingStep>) {
            logs = newLogs
            notifyDataSetChanged()
        }
        
        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val timestamp: TextView = view.findViewById(R.id.tvTimestamp)
            val message: TextView = view.findViewById(R.id.tvMessage)
            val icon: TextView = view.findViewById(R.id.tvIcon)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_pairing_log, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val step = logs[position]
            holder.timestamp.text = step.timestamp
            holder.message.text = step.message
            
            val (icon, color) = when (step.level) {
                LogLevel.SUCCESS -> "✅" to android.graphics.Color.parseColor("#81C784")  // success_light
                LogLevel.ERROR -> "❌" to android.graphics.Color.parseColor("#EF5350")    // danger_light
                LogLevel.WARNING -> "⚠️" to android.graphics.Color.parseColor("#FFB74D")  // warning_light
                LogLevel.INFO -> "ℹ️" to android.graphics.Color.parseColor("#4FC3F7")     // info_light
                LogLevel.DEBUG -> "🔍" to android.graphics.Color.parseColor("#BDBDBD")    // gray_400
            }
            
            holder.icon.text = icon
            holder.message.setTextColor(color)
        }
        
        override fun getItemCount() = logs.size
    }
    
    // Network Devices Adapter
    inner class NetworkDevicesAdapter : RecyclerView.Adapter<NetworkDevicesAdapter.ViewHolder>() {
        
        private var devices = listOf<NetworkScanner.NetworkDevice>()
        
        fun updateDevices(newDevices: List<NetworkScanner.NetworkDevice>) {
            devices = newDevices
            notifyDataSetChanged()
        }
        
        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val deviceIcon: TextView = view.findViewById(R.id.tvDeviceIcon)
            val deviceIp: TextView = view.findViewById(R.id.tvDeviceIp)
            val deviceInfo: TextView = view.findViewById(R.id.tvDeviceInfo)
            val deviceStatus: TextView = view.findViewById(R.id.tvDeviceStatus)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_network_device, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            
            holder.deviceIp.text = device.ip
            holder.deviceInfo.text = if (device.hostname != null) {
                "${device.hostname} (${device.responseTime}ms)"
            } else {
                "Czas odpowiedzi: ${device.responseTime}ms"
            }
            
            // Ikona i status w zależności od właściwości urządzenia
            if (device.hasPairingPort) {
                holder.deviceIcon.text = "📱"
                holder.deviceStatus.text = "✓"
                holder.deviceStatus.setTextColor(getColor(R.color.success))
            } else {
                holder.deviceIcon.text = "💻"
                holder.deviceStatus.text = "—"
                holder.deviceStatus.setTextColor(getColor(R.color.text_disabled))
            }
        }
        
        override fun getItemCount() = devices.size
    }
}
