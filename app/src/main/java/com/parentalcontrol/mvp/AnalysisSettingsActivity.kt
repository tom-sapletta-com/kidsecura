package com.parentalcontrol.mvp

import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.SystemLogger

/**
 * Activity for comprehensive analysis and monitoring settings
 * 
 * Features:
 * - Capture interval configuration
 * - Screen capture settings (crop, save, etc.)
 * - Location tracking toggle
 * - Screen time tracking
 * - Debug notifications
 * - Temporary unlock for child sessions
 * - Full screen detection settings
 */
class AnalysisSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AnalysisSettings"
        private const val REQUEST_LOCATION_PERMISSION = 3001
    }

    private lateinit var prefsManager: PreferencesManager
    private lateinit var systemLogger: SystemLogger
    
    // UI Components - Capture Settings
    private lateinit var seekBarInterval: SeekBar
    private lateinit var tvIntervalValue: TextView
    private lateinit var switchCropBottom: Switch
    private lateinit var switchLocalAnalysis: Switch
    private lateinit var switchSaveScreenshots: Switch
    private lateinit var switchFullScreenDetection: Switch
    
    // UI Components - Location & Tracking
    private lateinit var switchLocationTracking: Switch
    private lateinit var switchScreenTimeTracking: Switch
    private lateinit var tvScreenTimeToday: TextView
    
    // UI Components - Debug & Notifications
    private lateinit var switchDebugNotifications: Switch
    private lateinit var switchVerboseLogging: Switch
    private lateinit var tvLogCount: TextView
    
    // UI Components - Child Session
    private lateinit var btnUnlockSession: Button
    private lateinit var tvSessionStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_settings)
        
        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Ustawienia Analizy"
        
        prefsManager = PreferencesManager(this)
        systemLogger = SystemLogger.getInstance(this)
        
        initializeViews()
        loadCurrentSettings()
        setupListeners()
        
        systemLogger.logActivity(TAG, "onCreate", "Analysis settings opened")
    }
    
    private fun initializeViews() {
        // Capture Settings
        seekBarInterval = findViewById(R.id.seekBarCaptureInterval)
        tvIntervalValue = findViewById(R.id.tvIntervalValue)
        switchCropBottom = findViewById(R.id.switchCropBottom)
        switchLocalAnalysis = findViewById(R.id.switchLocalAnalysis)
        switchSaveScreenshots = findViewById(R.id.switchSaveScreenshots)
        switchFullScreenDetection = findViewById(R.id.switchFullScreenDetection)
        
        // Location & Tracking
        switchLocationTracking = findViewById(R.id.switchLocationTracking)
        switchScreenTimeTracking = findViewById(R.id.switchScreenTimeTracking)
        tvScreenTimeToday = findViewById(R.id.tvScreenTimeToday)
        
        // Debug & Notifications
        switchDebugNotifications = findViewById(R.id.switchDebugNotifications)
        switchVerboseLogging = findViewById(R.id.switchVerboseLogging)
        tvLogCount = findViewById(R.id.tvLogCount)
        
        // Child Session
        btnUnlockSession = findViewById(R.id.btnUnlockSession)
        tvSessionStatus = findViewById(R.id.tvSessionStatus)
    }
    
    private fun loadCurrentSettings() {
        // Capture Settings
        val interval = prefsManager.getCaptureInterval()
        seekBarInterval.progress = interval
        tvIntervalValue.text = "$interval sekund"
        
        switchCropBottom.isChecked = prefsManager.isCropBottomEnabled()
        switchLocalAnalysis.isChecked = prefsManager.isLocalAnalysisEnabled()
        switchSaveScreenshots.isChecked = prefsManager.isSaveScreenshotsEnabled()
        switchFullScreenDetection.isChecked = prefsManager.getBoolean("full_screen_detection", true)
        
        // Location & Tracking
        switchLocationTracking.isChecked = prefsManager.getBoolean("location_tracking_enabled", false)
        switchScreenTimeTracking.isChecked = prefsManager.getBoolean("screen_time_tracking_enabled", true)
        updateScreenTimeDisplay()
        
        // Debug & Notifications
        switchDebugNotifications.isChecked = prefsManager.getBoolean("debug_notifications_enabled", false)
        switchVerboseLogging.isChecked = prefsManager.getBoolean("verbose_logging_enabled", true)
        updateLogCount()
        
        // Session Status
        updateSessionStatus()
    }
    
    private fun setupListeners() {
        // Capture Interval
        seekBarInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val interval = if (progress < 1) 1 else progress
                tvIntervalValue.text = "$interval sekund"
                if (fromUser) {
                    prefsManager.setCaptureInterval(interval)
                    systemLogger.d(TAG, "Capture interval changed to $interval seconds")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Capture Settings Switches
        switchCropBottom.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setCropBottomEnabled(isChecked)
            systemLogger.d(TAG, "Crop bottom navigation: $isChecked")
        }
        
        switchLocalAnalysis.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setLocalAnalysisEnabled(isChecked)
            systemLogger.d(TAG, "Local analysis: $isChecked")
        }
        
        switchSaveScreenshots.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setSaveScreenshotsEnabled(isChecked)
            systemLogger.d(TAG, "Save screenshots: $isChecked")
        }
        
        switchFullScreenDetection.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setBoolean("full_screen_detection", isChecked)
            systemLogger.i(TAG, "Full screen detection: $isChecked")
            Toast.makeText(this, 
                if (isChecked) "Wykrywanie na całym ekranie: WŁĄCZONE" 
                else "Wykrywanie na całym ekranie: WYŁĄCZONE", 
                Toast.LENGTH_SHORT).show()
        }
        
        // Location & Tracking
        switchLocationTracking.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setBoolean("location_tracking_enabled", isChecked)
            systemLogger.i(TAG, "Location tracking: $isChecked")
            if (isChecked) {
                requestLocationPermission()
            }
        }
        
        switchScreenTimeTracking.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setBoolean("screen_time_tracking_enabled", isChecked)
            systemLogger.i(TAG, "Screen time tracking: $isChecked")
        }
        
        // Debug & Notifications
        switchDebugNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setBoolean("debug_notifications_enabled", isChecked)
            systemLogger.i(TAG, "Debug notifications: $isChecked")
            Toast.makeText(this,
                if (isChecked) "Powiadomienia debugowania WŁĄCZONE - zobaczysz wykryte słowa w czasie rzeczywistym"
                else "Powiadomienia debugowania WYŁĄCZONE",
                Toast.LENGTH_LONG).show()
        }
        
        switchVerboseLogging.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setBoolean("verbose_logging_enabled", isChecked)
            systemLogger.i(TAG, "Verbose logging: $isChecked")
        }
        
        // Child Session Unlock
        btnUnlockSession.setOnClickListener {
            showUnlockSessionDialog()
        }
    }
    
    private fun updateScreenTimeDisplay() {
        val screenTimeMs = prefsManager.getLong("screen_time_today_ms", 0L)
        val hours = screenTimeMs / (1000 * 60 * 60)
        val minutes = (screenTimeMs / (1000 * 60)) % 60
        tvScreenTimeToday.text = "Czas dziś: ${hours}h ${minutes}min"
    }
    
    private fun updateLogCount() {
        val logCount = prefsManager.getInt("total_log_count", 0)
        tvLogCount.text = "Zapisanych logów: $logCount"
    }
    
    private fun updateSessionStatus() {
        val sessionUnlocked = prefsManager.getBoolean("child_session_unlocked", false)
        val sessionExpiry = prefsManager.getLong("child_session_expiry", 0L)
        
        if (sessionUnlocked && System.currentTimeMillis() < sessionExpiry) {
            val remainingMinutes = (sessionExpiry - System.currentTimeMillis()) / (1000 * 60)
            tvSessionStatus.text = "✅ Sesja odblokowana (pozostało: ${remainingMinutes}min)"
            tvSessionStatus.setTextColor(getColor(R.color.status_safe))
            btnUnlockSession.text = "Zakończ Sesję"
        } else {
            tvSessionStatus.text = "🔒 Sesja zablokowana - monitoring aktywny"
            tvSessionStatus.setTextColor(getColor(R.color.status_danger))
            btnUnlockSession.text = "Odblokuj Sesję dla Dziecka"
            
            // Auto-clear expired session
            if (sessionUnlocked) {
                prefsManager.setBoolean("child_session_unlocked", false)
            }
        }
    }
    
    private fun showUnlockSessionDialog() {
        val sessionUnlocked = prefsManager.getBoolean("child_session_unlocked", false)
        
        if (sessionUnlocked) {
            // End session
            AlertDialog.Builder(this)
                .setTitle("Zakończ Sesję")
                .setMessage("Czy chcesz zakończyć sesję dziecka i przywrócić monitoring?")
                .setPositiveButton("Tak") { _, _ ->
                    endChildSession()
                }
                .setNegativeButton("Anuluj", null)
                .show()
        } else {
            // Start session - ask for duration
            val durations = arrayOf("15 minut", "30 minut", "1 godzina", "2 godziny", "3 godziny")
            val durationMinutes = arrayOf(15, 30, 60, 120, 180)
            
            AlertDialog.Builder(this)
                .setTitle("Odblokuj Sesję dla Dziecka")
                .setMessage("Wybierz czas na jaki chcesz wyłączyć monitoring:")
                .setItems(durations) { _, which ->
                    startChildSession(durationMinutes[which])
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }
    }
    
    private fun startChildSession(durationMinutes: Int) {
        val expiryTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        prefsManager.setBoolean("child_session_unlocked", true)
        prefsManager.setLong("child_session_expiry", expiryTime)
        
        systemLogger.i(TAG, "Child session started for $durationMinutes minutes")
        Toast.makeText(this, 
            "✅ Sesja odblokowana na $durationMinutes minut\nMonitoring zostanie wstrzymany",
            Toast.LENGTH_LONG).show()
        
        updateSessionStatus()
    }
    
    private fun endChildSession() {
        prefsManager.setBoolean("child_session_unlocked", false)
        prefsManager.setLong("child_session_expiry", 0L)
        
        systemLogger.i(TAG, "Child session ended manually")
        Toast.makeText(this, 
            "🔒 Sesja zakończona\nMonitoring przywrócony",
            Toast.LENGTH_SHORT).show()
        
        updateSessionStatus()
    }
    
    private fun requestLocationPermission() {
        // Check if location permission is granted
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && 
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Uprawnienia lokalizacji przyznane", Toast.LENGTH_SHORT).show()
            } else {
                switchLocationTracking.isChecked = false
                prefsManager.setBoolean("location_tracking_enabled", false)
                Toast.makeText(this, "Lokalizacja wymaga uprawnień", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateScreenTimeDisplay()
        updateLogCount()
        updateSessionStatus()
    }
}
