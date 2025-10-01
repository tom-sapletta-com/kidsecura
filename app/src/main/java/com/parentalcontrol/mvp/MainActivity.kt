package com.parentalcontrol.mvp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.parentalcontrol.mvp.databinding.ActivityMainBinding
import com.parentalcontrol.mvp.model.DeviceType
import com.parentalcontrol.mvp.service.ScreenCaptureService
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.FileLogger
import com.parentalcontrol.mvp.utils.SystemLogger
import com.parentalcontrol.mvp.messaging.MessagingIntegrationManager
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.view.View
import android.widget.ScrollView

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var prefsManager: PreferencesManager
    private lateinit var fileLogger: FileLogger
    private lateinit var systemLogger: SystemLogger
    private lateinit var stealthManager: StealthManager
    private lateinit var messagingManager: MessagingIntegrationManager
    
    private var isServiceRunning = false
    private val logUpdateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var logUpdateJob: Job? = null
    
    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val PERMISSION_REQUEST_CODE = 2001
        private const val TAG = "MainActivity"
    }
    
    // Launcher dla rezultatu MediaProjection
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                startScreenCaptureService(result.resultCode, data)
            }
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
            updateUI(false)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "🚀 MainActivity.onCreate() - START")
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "✅ super.onCreate() completed")
            
            binding = ActivityMainBinding.inflate(layoutInflater)
            Log.d(TAG, "✅ ActivityMainBinding inflated successfully")
            
            setContentView(binding.root)
            Log.d(TAG, "✅ setContentView completed")
            
            prefsManager = PreferencesManager(this)
            Log.d(TAG, "✅ PreferencesManager initialized")
            
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            Log.d(TAG, "✅ MediaProjectionManager initialized")
            
            fileLogger = FileLogger(this)
            Log.d(TAG, "✅ FileLogger initialized")
            
            systemLogger = SystemLogger.getInstance(this)
            Log.d(TAG, "✅ SystemLogger initialized")
            systemLogger.logActivity("MainActivity", "onCreate", "App startup initiated")
            
            stealthManager = StealthManager(this, prefsManager, systemLogger)
            Log.d(TAG, "✅ StealthManager initialized")
            
            messagingManager = MessagingIntegrationManager(this, prefsManager, systemLogger)
            Log.d(TAG, "✅ MessagingIntegrationManager initialized")
            
            // Aktywuj ochronę anti-tampering przy starcie aplikacji
            stealthManager.activateAntiTamperingProtection()
            Log.d(TAG, "🛡️ Anti-tampering protection activated")
            
            setupUI()
            Log.d(TAG, "✅ setupUI() completed")
            
            checkPermissions()
            Log.d(TAG, "✅ checkPermissions() completed")
            
            Log.d(TAG, "🎉 MainActivity.onCreate() - COMPLETED SUCCESSFULLY")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR in MainActivity.onCreate()", e)
            Toast.makeText(this, "Błąd inicjalizacji: ${e.message}", Toast.LENGTH_LONG).show()
        }
        updateServiceStatus()
        startLogUpdates()
    }
    
    private fun setupUI() {
        Log.d(TAG, "🔧 setupUI() - START")
        try {
            binding.apply {
            // Przycisk rozpoczęcia/zatrzymania monitorowania
            btnToggleMonitoring.setOnClickListener {
                if (isServiceRunning) {
                    stopMonitoring()
                } else {
                    requestScreenCapture()
                }
            }
            
            // Konfiguracja interwału
            seekBarInterval.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    min = 1
                }
                max = 10
                progress = prefsManager.getCaptureInterval()
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        tvIntervalValue.text = getString(R.string.interval_value, progress)
                        if (fromUser) {
                            prefsManager.setCaptureInterval(progress)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                })
            }
            
            // Przełączniki
            switchCropBottom.isChecked = prefsManager.isCropBottomEnabled()
            switchCropBottom.setOnCheckedChangeListener { _, isChecked ->
                prefsManager.setCropBottomEnabled(isChecked)
            }
            
            switchLocalAnalysis.isChecked = prefsManager.isLocalAnalysisEnabled()
            switchLocalAnalysis.setOnCheckedChangeListener { _, isChecked ->
                prefsManager.setLocalAnalysisEnabled(isChecked)
            }
            
            switchSaveScreenshots.isChecked = prefsManager.isSaveScreenshotsEnabled()
            switchSaveScreenshots.setOnCheckedChangeListener { _, isChecked ->
                prefsManager.setSaveScreenshotsEnabled(isChecked)
            }
            
            // Podgląd logów
            btnViewLogs.setOnClickListener {
                startActivity(Intent(this@MainActivity, LogViewerActivity::class.java))
            }
            
            // Edytor słów kluczowych - NOWY DIALOG zamiast problematycznego Activity
            Log.d(TAG, "🔧 Setting up btnKeywordsEditor click listener - NEW DIALOG VERSION")
            btnKeywordsEditor.setOnClickListener {
                try {
                    Log.d(TAG, "🔧 btnKeywordsEditor clicked - Opening new keywords dialog")
                    systemLogger.logButtonClick("Słowa Kluczowe", "MainActivity", true)
                    showKeywordsEditorDialog()
                    Log.d(TAG, "✅ Keywords editor dialog shown successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ BŁĄD podczas otwierania dialog słów kluczowych", e)
                    systemLogger.logButtonClick("Słowa Kluczowe", "MainActivity", false, e.message)
                    Toast.makeText(this@MainActivity, "Błąd Słowa Kluczowe: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            // Zarządzanie urządzeniami
            Log.d(TAG, "🔧 Setting up btnManageDevices click listener")
            btnManageDevices.setOnClickListener {
                try {
                    Log.d(TAG, "🔧 btnManageDevices clicked - Opening PairedDevicesActivity")
                    systemLogger.logButtonClick("Zarządzanie urządzeń", "MainActivity", true)
                    val intent = Intent(this@MainActivity, PairedDevicesActivity::class.java)
                    startActivity(intent)
                    Log.d(TAG, "✅ PairedDevicesActivity opened successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ BŁĄD podczas otwierania PairedDevicesActivity", e)
                    systemLogger.logButtonClick("Zarządzanie urządzeń", "MainActivity", false, e.message)
                    Toast.makeText(this@MainActivity, "Błąd Urządzenia: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            // Przeglądanie incydentów
            Log.d(TAG, "🔧 Setting up btnViewIncidents click listener")
            btnViewIncidents.setOnClickListener {
                try {
                    Log.d(TAG, "🔧 btnViewIncidents clicked - Opening IncidentsActivity")
                    systemLogger.logButtonClick("Historia incydentów", "MainActivity", true)
                    val intent = Intent(this@MainActivity, IncidentsActivity::class.java)
                    startActivity(intent)
                    Log.d(TAG, "✅ IncidentsActivity opened successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ BŁĄD podczas otwierania IncidentsActivity", e)
                    systemLogger.logButtonClick("Historia incydentów", "MainActivity", false, e.message)
                    Toast.makeText(this@MainActivity, "Błąd Historia: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            // Ustawienia alertów
            Log.d(TAG, "🔧 Setting up btnAlertSettings click listener")
            btnAlertSettings.setOnClickListener {
                try {
                    Log.d(TAG, "🔧 btnAlertSettings clicked - Opening AlertSettingsActivity")
                    systemLogger.logButtonClick("Ustawienia alertów", "MainActivity", true)
                    val intent = Intent(this@MainActivity, AlertSettingsActivity::class.java)
                    startActivity(intent)
                    Log.d(TAG, "✅ AlertSettingsActivity opened successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ BŁĄD podczas otwierania AlertSettingsActivity", e)
                    systemLogger.logButtonClick("Ustawienia alertów", "MainActivity", false, e.message)
                    Toast.makeText(this@MainActivity, "Błąd Alerty: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            // Parowanie urządzeń
            Log.d(TAG, "🔧 Setting up btnPairDevice click listener")
            btnPairDevice.setOnClickListener {
                try {
                    Log.d(TAG, "🔧 btnPairDevice clicked - Opening device pairing dialog")
                    systemLogger.logButtonClick("Parowanie urządzeń", "MainActivity", true)
                    showDeviceTypeSelectionDialog()
                    Log.d(TAG, "✅ Device pairing dialog shown successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ BŁĄD podczas otwierania dialog parowania", e)
                    systemLogger.logButtonClick("Parowanie urządzeń", "MainActivity", false, e.message)
                    Toast.makeText(this@MainActivity, "Błąd Parowanie: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            // STEALTH MODE CONTROLS
            Log.d(TAG, "🕵️ Setting up Stealth Mode controls")
            btnStealthMode.setOnClickListener {
                try {
                    Log.d(TAG, "🕵️ btnStealthMode clicked - Toggle stealth mode")
                    systemLogger.logButtonClick("Tryb Ukryty", "MainActivity", true)
                    toggleStealthMode()
                    Log.d(TAG, "✅ Stealth mode toggle completed")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ BŁĄD podczas toggle stealth mode", e)
                    systemLogger.logButtonClick("Tryb Ukryty", "MainActivity", false, e.message)
                    Toast.makeText(this@MainActivity, "Błąd trybu ukrytego: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            btnStealthSettings.setOnClickListener {
                try {
                    Log.d(TAG, "🎭 btnStealthSettings clicked - Opening stealth configuration")
                    systemLogger.logButtonClick("Konfiguracja Stealth", "MainActivity", true)
                    showStealthSettingsDialog()
                    Log.d(TAG, "✅ Stealth settings dialog opened")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ BŁĄD podczas otwierania stealth settings", e)
                    systemLogger.logButtonClick("Konfiguracja Stealth", "MainActivity", false, e.message)
                    Toast.makeText(this@MainActivity, "Błąd konfiguracji stealth: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            // MESSAGING INTEGRATION CONTROLS
            Log.d(TAG, "📱 Setting up Messaging Integration controls")
            btnAlertSettings.setOnClickListener {
                try {
                    Log.d(TAG, "📱 btnAlertSettings clicked - Opening messaging configuration")
                    systemLogger.logButtonClick("Konfiguracja Alertów", "MainActivity", true)
                    showMessagingConfigDialog()
                    Log.d(TAG, "✅ Messaging config dialog opened")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ BŁĄD podczas otwierania messaging config", e)
                    systemLogger.logButtonClick("Konfiguracja Alertów", "MainActivity", false, e.message)
                    Toast.makeText(this@MainActivity, "Błąd konfiguracji alertów: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        Log.d(TAG, "✅ setupUI() - COMPLETED SUCCESSFULLY")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR in setupUI()", e)
            Toast.makeText(this, "Błąd setup UI: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
        
        // Sprawdź uprawnienie SYSTEM_ALERT_WINDOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showOverlayPermissionDialog()
            }
        }
    }
    
    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_rationale_title)
            .setMessage(R.string.permission_rationale_message)
            .setPositiveButton(R.string.permission_settings) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
    }
    
    private fun showDeviceTypeSelectionDialog() {
        val options = arrayOf(
            getString(R.string.device_type_parent),
            getString(R.string.device_type_child)
        )
        
        AlertDialog.Builder(this)
            .setTitle(R.string.select_device_type)
            .setItems(options) { _, which ->
                val deviceType = if (which == 0) DeviceType.PARENT else DeviceType.CHILD
                startPairingActivity(deviceType)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun startPairingActivity(deviceType: DeviceType) {
        val intent = Intent(this, PairingActivity::class.java).apply {
            putExtra(PairingActivity.EXTRA_DEVICE_TYPE, deviceType)
        }
        startActivity(intent)
    }
    
    
    private fun requestScreenCapture() {
        // Pokaż krótką instrukcję użytkownikowi
        Toast.makeText(this, R.string.screen_capture_instruction, Toast.LENGTH_LONG).show()
        
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(captureIntent)
    }
    
    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("RESULT_CODE", resultCode)
            putExtra("DATA", data)
            putExtra("CAPTURE_INTERVAL", prefsManager.getCaptureInterval())
            putExtra("CROP_BOTTOM", prefsManager.isCropBottomEnabled())
            putExtra("LOCAL_ANALYSIS", prefsManager.isLocalAnalysisEnabled())
            putExtra("SAVE_SCREENSHOTS", prefsManager.isSaveScreenshotsEnabled())
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        updateUI(true)
        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
    }
    
    private fun stopMonitoring() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        updateUI(false)
        Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
    }
    
    private fun updateServiceStatus() {
        isServiceRunning = ScreenCaptureService.isRunning
        updateUI(isServiceRunning)
    }
    
    private fun updateUI(running: Boolean) {
        isServiceRunning = running
        binding.apply {
            if (running) {
                tvStatus.text = getString(R.string.status_active)
                tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_safe))
                btnToggleMonitoring.text = getString(R.string.btn_stop_monitoring)
            } else {
                tvStatus.text = getString(R.string.status_inactive)
                tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_danger))
                btnToggleMonitoring.text = getString(R.string.btn_start_monitoring)
            }
            
            // Wyłącz ustawienia podczas działania serwisu
            seekBarInterval.isEnabled = !running
            switchCropBottom.isEnabled = !running
            switchLocalAnalysis.isEnabled = !running
            switchSaveScreenshots.isEnabled = !running
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }
    
    /**
     * New Keywords Editor Dialog - replaces problematic KeywordsEditorActivity
     * Simple, reliable dialog for managing threat keywords inline in MainActivity
     */
    private fun showKeywordsEditorDialog() {
        try {
            systemLogger.d(TAG, "🔧 showKeywordsEditorDialog() - START")
            
            val currentKeywords = prefsManager.getThreatKeywords().toMutableList()
            systemLogger.d(TAG, "✅ Loaded ${currentKeywords.size} current keywords")
            
            // Create dialog layout
            val dialogView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            
            // Title
            val titleView = TextView(this).apply {
                text = "🔍 Edytor Słów Kluczowych"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 24)
            }
            dialogView.addView(titleView)
            
            // Keywords list container
            val keywordsContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16)
            }
            
            // Function to refresh keywords display
            fun refreshKeywordsList() {
                keywordsContainer.removeAllViews()
                if (currentKeywords.isEmpty()) {
                    val emptyView = TextView(this@MainActivity).apply {
                        text = "Brak słów kluczowych. Dodaj pierwsze słowo poniżej."
                        setPadding(16, 16, 16, 16)
                        setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                    }
                    keywordsContainer.addView(emptyView)
                } else {
                    currentKeywords.forEachIndexed { index, keyword ->
                        val keywordRow = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(8, 8, 8, 8)
                        }
                        
                        val keywordText = TextView(this@MainActivity).apply {
                            text = "• $keyword"
                            textSize = 16f
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            setPadding(8, 8, 8, 8)
                        }
                        
                        val deleteButton = android.widget.Button(this@MainActivity).apply {
                            text = "✕"
                            layoutParams = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
                            setOnClickListener {
                                currentKeywords.removeAt(index)
                                refreshKeywordsList()
                                systemLogger.d(TAG, "🗑️ Removed keyword: $keyword")
                            }
                        }
                        
                        keywordRow.addView(keywordText)
                        keywordRow.addView(deleteButton)
                        keywordsContainer.addView(keywordRow)
                    }
                }
            }
            
            refreshKeywordsList()
            dialogView.addView(keywordsContainer)
            
            // Add new keyword section
            val addKeywordLabel = TextView(this).apply {
                text = "Dodaj nowe słowo kluczowe:"
                setPadding(0, 16, 0, 8)
            }
            dialogView.addView(addKeywordLabel)
            
            val newKeywordInput = EditText(this).apply {
                hint = "Wpisz słowo kluczowe..."
                setPadding(16, 16, 16, 16)
            }
            dialogView.addView(newKeywordInput)
            
            val addButton = android.widget.Button(this).apply {
                text = "➕ Dodaj"
                setOnClickListener {
                    val newKeyword = newKeywordInput.text.toString().trim()
                    if (newKeyword.isNotEmpty() && !currentKeywords.contains(newKeyword)) {
                        currentKeywords.add(newKeyword)
                        newKeywordInput.setText("")
                        refreshKeywordsList()
                        systemLogger.d(TAG, "➕ Added new keyword: $newKeyword")
                    } else if (currentKeywords.contains(newKeyword)) {
                        Toast.makeText(this@MainActivity, "To słowo już istnieje!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialogView.addView(addButton)
            
            // Reset to defaults button
            val resetButton = android.widget.Button(this).apply {
                text = "🔄 Przywróć domyślne"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Przywrócić domyślne słowa kluczowe?")
                        .setMessage("To zastąpi wszystkie obecne słowa kluczowe domyślną listą.")
                        .setPositiveButton("Tak") { _, _ ->
                            // Use the public method to reset keywords
                            prefsManager.resetThreatKeywordsToDefault()
                            // Reload the keywords from preferences
                            currentKeywords.clear()
                            currentKeywords.addAll(prefsManager.getThreatKeywords())
                            refreshKeywordsList()
                            systemLogger.d(TAG, "🔄 Reset to default keywords")
                        }
                        .setNegativeButton("Anuluj", null)
                        .show()
                }
            }
            dialogView.addView(resetButton)
            
            // Show dialog
            AlertDialog.Builder(this)
                .setTitle("Słowa Kluczowe")
                .setView(dialogView)
                .setPositiveButton("💾 Zapisz") { _, _ ->
                    prefsManager.setThreatKeywords(currentKeywords)
                    systemLogger.d(TAG, "💾 Saved ${currentKeywords.size} keywords to preferences")
                    Toast.makeText(this, "Słowa kluczowe zapisane!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Anuluj", null)
                .show()
                
            systemLogger.d(TAG, "✅ Keywords editor dialog shown successfully")
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error in showKeywordsEditorDialog()", e)
            Toast.makeText(this, "Błąd dialogu słów kluczowych: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_rationale_message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Rozpoczyna okresowe aktualizacje podglądu logów
     */
    private fun startLogUpdates() {
        logUpdateJob?.cancel()
        logUpdateJob = logUpdateScope.launch {
            while (isActive) {
                updateLogPreview()
                delay(3000) // Aktualizuj co 3 sekundy
            }
        }
    }
    
    /**
     * Ładuje 3 najnowsze logi z pliku używając tej samej lokalizacji co FileLogger
     */
    private suspend fun loadRecentLogs(): List<String> = withContext(Dispatchers.IO) {
        try {
            // Używamy tej samej ścieżki co FileLogger: Downloads/KidSecura/
            val logDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val kidsecuraDir = File(logDir, "KidSecura")
            
            if (!kidsecuraDir.exists()) {
                Log.d("MainActivity", "Log directory does not exist: ${kidsecuraDir.absolutePath}")
                return@withContext emptyList()
            }
            
            // Znajdź najnowszy plik logu (monitoring_log_YYYY-MM-DD.txt)
            val logFiles = kidsecuraDir.listFiles { file ->
                file.name.startsWith("monitoring_log_") && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() }
            
            if (logFiles.isNullOrEmpty()) {
                Log.d("MainActivity", "No log files found in: ${kidsecuraDir.absolutePath}")
                return@withContext emptyList()
            }
            
            val mostRecentLogFile = logFiles.first()
            Log.d("MainActivity", "Reading from log file: ${mostRecentLogFile.absolutePath}")
            
            if (!mostRecentLogFile.exists()) {
                return@withContext emptyList()
            }
            
            val lines = mostRecentLogFile.readLines()
            val recentLogs = lines.takeLast(3).reversed() // 3 najnowsze, od najnowszego
            
            Log.d("MainActivity", "Loaded ${recentLogs.size} recent log entries")
            
            // Formatuj logi dla wyświetlenia
            recentLogs.map { line ->
                formatLogLine(line)
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading logs", e)
            emptyList()
        }
    }
    
    /**
     * Formatuje linię loga dla czytelnego wyświetlenia
     */
    private fun formatLogLine(line: String): String {
        return try {
            // Format loga: [timestamp] TYPE: message
            val parts = line.split("] ", limit = 2)
            if (parts.size >= 2) {
                val timestamp = parts[0].replace("[", "")
                val content = parts[1]
                
                // Skróć timestamp do godziny:minuta
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val shortTime = try {
                    val fullTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timestamp)
                    timeFormat.format(fullTime ?: Date())
                } catch (e: Exception) {
                    timestamp.substringAfter(" ").substringBefore(":")
                }
                
                "$shortTime: $content"
            } else {
                line.take(60) + if (line.length > 60) "..." else ""
            }
        } catch (e: Exception) {
            line.take(60) + if (line.length > 60) "..." else ""
        }
    }
    
    /**
     * Aktualizuje podgląd logów w UI
     */
    private fun updateLogPreview() {
        logUpdateScope.launch {
            val recentLogs = loadRecentLogs()
            
            binding.apply {
                if (recentLogs.isEmpty()) {
                    tvNoLogs.visibility = View.VISIBLE
                    layoutRecentLogs.removeAllViews()
                    layoutRecentLogs.addView(tvNoLogs)
                } else {
                    tvNoLogs.visibility = View.GONE
                    layoutRecentLogs.removeAllViews()
                    
                    recentLogs.forEach { logText ->
                        val logView = android.widget.TextView(this@MainActivity).apply {
                            text = logText
                            textSize = 12f
                            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
                            setPadding(8, 4, 8, 4)
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        }
                        layoutRecentLogs.addView(logView)
                    }
                    
                    // Auto-scroll do dołu aby pokazać najnowsze logi
                    scrollViewRecentLogs.post {
                        scrollViewRecentLogs.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                }
            }
        }
    }
    
    /**
     * Toggle Stealth Mode - włącza/wyłącza tryb ukryty aplikacji
     */
    private fun toggleStealthMode() {
        try {
            systemLogger.i(TAG, "🕵️ toggleStealthMode() - START")
            
            if (stealthManager.isStealthModeEnabled()) {
                // Wyłącz tryb stealth
                AlertDialog.Builder(this)
                    .setTitle("🔓 Wyłączyć Tryb Ukryty?")
                    .setMessage("Aplikacja stanie się widoczna dla dzieci. Czy kontynuować?")
                    .setPositiveButton("Tak, wyłącz") { _, _ ->
                        if (stealthManager.disableStealthMode()) {
                            updateStealthUI()
                            systemLogger.i(TAG, "✅ Stealth Mode DISABLED successfully")
                            Toast.makeText(this, "🔓 Tryb ukryty WYŁĄCZONY", Toast.LENGTH_SHORT).show()
                        } else {
                            systemLogger.e(TAG, "❌ Failed to disable Stealth Mode")
                            Toast.makeText(this, "❌ Błąd wyłączania trybu ukrytego", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Anuluj", null)
                    .show()
            } else {
                // Włącz tryb stealth - wymagaj PIN
                showStealthActivationDialog()
            }
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error in toggleStealthMode()", e)
            Toast.makeText(this, "Błąd stealth mode: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Pokazuje dialog aktywacji trybu stealth z konfiguracją PIN
     */
    private fun showStealthActivationDialog() {
        try {
            systemLogger.i(TAG, "🔐 showStealthActivationDialog() - START")
            
            val dialogView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            
            // Title
            val titleView = TextView(this).apply {
                text = "🕵️ Aktywacja Trybu Ukrytego"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 24)
            }
            dialogView.addView(titleView)
            
            // Warning message
            val warningView = TextView(this).apply {
                text = "⚠️ UWAGA: Po aktywacji aplikacja zostanie UKRYTA przed dziećmi.\n\n" +
                       "• Ikona zniknie z listy aplikacji\n" +
                       "• Dostęp tylko przez sekretny kod\n" +
                       "• Monitoring będzie działał w tle\n\n" +
                       "Ustaw PIN dostępu (opcjonalnie):"
                textSize = 14f
                setPadding(0, 0, 0, 16)
            }
            dialogView.addView(warningView)
            
            // PIN input
            val pinInput = EditText(this).apply {
                hint = "Kod PIN (zostaw puste dla brak PIN)"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setPadding(16, 16, 16, 16)
            }
            dialogView.addView(pinInput)
            
            // Show dialog
            AlertDialog.Builder(this)
                .setTitle("Tryb Ukryty")
                .setView(dialogView)
                .setPositiveButton("🕵️ AKTYWUJ") { _, _ ->
                    val pin = pinInput.text.toString().trim()
                    if (stealthManager.enableStealthMode(pin.ifEmpty { null })) {
                        updateStealthUI()
                        systemLogger.i(TAG, "✅ Stealth Mode ENABLED successfully")
                        showStealthAccessInstructions()
                    } else {
                        systemLogger.e(TAG, "❌ Failed to enable Stealth Mode")
                        Toast.makeText(this, "❌ Błąd aktywacji trybu ukrytego", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
                
            systemLogger.d(TAG, "✅ Stealth activation dialog shown")
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error in showStealthActivationDialog()", e)
            Toast.makeText(this, "Błąd dialogu aktywacji: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Pokazuje instrukcje dostępu do ukrytej aplikacji
     */
    private fun showStealthAccessInstructions() {
        try {
            val secretCode = stealthManager.generateSecretAccessCode()
            
            AlertDialog.Builder(this)
                .setTitle("🔒 Instrukcje Dostępu")
                .setMessage("Aplikacja zostanie ukryta za 10 sekund.\n\n" +
                           "SPOSÓB DOSTĘPU:\n" +
                           "• Otwórz telefon i wpisz: $secretCode\n" +
                           "• Lub 3x tapnij w róg ekranu i narysuj wzór\n" +
                           "• Aplikacja pojawi się ponownie\n\n" +
                           "⚠️ ZAPAMIĘTAJ TEN KOD!")
                .setPositiveButton("Rozumiem") { _, _ ->
                    // Ukryj aplikację po 10 sekundach
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 10000)
                }
                .setCancelable(false)
                .show()
                
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error showing access instructions", e)
        }
    }

    /**
     * Pokazuje dialog ustawień trybu stealth
     */
    private fun showStealthSettingsDialog() {
        try {
            systemLogger.i(TAG, "🎭 showStealthSettingsDialog() - START")
            
            val stealthStatus = stealthManager.getStealthStatus()
            val isEnabled = stealthStatus["enabled"] as Boolean
            val disguiseMode = stealthStatus["disguise_mode"] as String
            val hasPIN = stealthStatus["pin_configured"] as Boolean
            
            val dialogView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            
            // Title
            val titleView = TextView(this).apply {
                text = "🎭 Konfiguracja Trybu Ukrytego"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 24)
            }
            dialogView.addView(titleView)
            
            // Current status
            val statusView = TextView(this).apply {
                text = "Status: ${if (isEnabled) "🟢 AKTYWNY" else "🔴 NIEAKTYWNY"}\n" +
                       "Tryb maskowania: $disguiseMode\n" +
                       "PIN skonfigurowany: ${if (hasPIN) "✅ TAK" else "❌ NIE"}"
                textSize = 14f
                setPadding(0, 0, 0, 16)
            }
            dialogView.addView(statusView)
            
            // Show dialog
            AlertDialog.Builder(this)
                .setTitle("Konfiguracja Stealth")
                .setView(dialogView)
                .setPositiveButton("💾 Zapisz") { _, _ ->
                    systemLogger.d(TAG, "✅ Stealth settings saved")
                    Toast.makeText(this, "Konfiguracja zapisana", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Anuluj", null)
                .show()
                
            systemLogger.d(TAG, "✅ Stealth settings dialog shown")
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error in showStealthSettingsDialog()", e)
            Toast.makeText(this, "Błąd dialogu ustawień: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Aktualizuje UI przycisków stealth na podstawie aktualnego stanu
     */
    private fun updateStealthUI() {
        try {
            val isStealthEnabled = stealthManager.isStealthModeEnabled()
            
            binding.btnStealthMode.text = if (isStealthEnabled) {
                "🔓 Wyłącz Ukryty"
            } else {
                "🕵️ Tryb Ukryty"
            }
            
            binding.btnStealthMode.setBackgroundColor(
                ContextCompat.getColor(this, 
                    if (isStealthEnabled) android.R.color.holo_orange_dark 
                    else android.R.color.holo_blue_dark
                )
            )
            
            systemLogger.d(TAG, "✅ Stealth UI updated - enabled: $isStealthEnabled")
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error updating stealth UI", e)
        }
    }
    
    /**
     * Wyświetla dialog konfiguracji Telegram/WhatsApp messaging
     */
    private fun showMessagingConfigDialog() {
        try {
            systemLogger.d(TAG, "📱 showMessagingConfigDialog() - START")
            
            // Create dialog layout
            val dialogView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            
            // Title
            val titleView = TextView(this).apply {
                text = "📱 Konfiguracja Alertów"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 24)
            }
            dialogView.addView(titleView)
            
            // Telegram section
            val telegramTokenInput = EditText(this).apply {
                hint = "Telegram Bot Token (od @BotFather)"
                setPadding(16, 16, 16, 16)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
            dialogView.addView(telegramTokenInput)
            
            val telegramChatInput = EditText(this).apply {
                hint = "Telegram Chat ID (np. 123456789)"
                setPadding(16, 16, 16, 16)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            dialogView.addView(telegramChatInput)
            
            // Test button
            val testButton = android.widget.Button(this).apply {
                text = "🧪 Wyślij Test"
                setOnClickListener {
                    testMessaging()
                }
            }
            dialogView.addView(testButton)
            
            // Show dialog
            AlertDialog.Builder(this)
                .setTitle("Konfiguracja Alertów")
                .setView(dialogView)
                .setPositiveButton("💾 Zapisz") { _, _ ->
                    try {
                        val botToken = telegramTokenInput.text.toString().trim()
                        val chatId = telegramChatInput.text.toString().trim()
                        
                        if (botToken.isNotEmpty() && chatId.isNotEmpty()) {
                            messagingManager.enableTelegram(botToken, listOf(chatId))
                            systemLogger.d(TAG, "✅ Telegram enabled")
                            Toast.makeText(this, "✅ Telegram skonfigurowany!", Toast.LENGTH_SHORT).show()
                        } else {
                            messagingManager.disableTelegram()
                            systemLogger.d(TAG, "❌ Telegram disabled - empty config")
                            Toast.makeText(this, "❌ Telegram wyłączony", Toast.LENGTH_SHORT).show()
                        }
                        
                    } catch (e: Exception) {
                        systemLogger.e(TAG, "❌ Error saving messaging config", e)
                        Toast.makeText(this, "Błąd zapisu: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
                
            systemLogger.d(TAG, "✅ Messaging config dialog shown successfully")
            
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error in showMessagingConfigDialog()", e)
            Toast.makeText(this, "Błąd dialogu alertów: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Testuje konfigurację messaging
     */
    private fun testMessaging() {
        lifecycleScope.launch {
            try {
                systemLogger.d(TAG, "🧪 Testing messaging configuration...")
                val success = messagingManager.sendTestMessage()
                
                val message = if (success) {
                    "✅ Test wiadomości wysłany pomyślnie!"
                } else {
                    "⚠️ Test wiadomości nie został wysłany. Sprawdź konfigurację."
                }
                
                runOnUiThread {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
                
                systemLogger.d(TAG, "🧪 Messaging test completed: success=$success")
                
            } catch (e: Exception) {
                systemLogger.e(TAG, "❌ Error testing messaging", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Błąd testu: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logUpdateJob?.cancel()
        logUpdateScope.cancel()
    }
}
