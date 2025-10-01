package com.parentalcontrol.mvp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.parentalcontrol.mvp.manager.IncidentManager
import com.parentalcontrol.mvp.model.*
import com.parentalcontrol.mvp.utils.PreferencesManager
import kotlinx.coroutines.launch

/**
 * Aktywność do konfiguracji ustawień alertów i powiadomień
 */
class AlertSettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AlertSettingsActivity"
    }
    
    // UI Components
    private lateinit var btnBack: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var seekBarFrequencyThreshold: SeekBar
    private lateinit var tvFrequencyThresholdValue: TextView
    private lateinit var seekBarTimeWindow: SeekBar
    private lateinit var tvTimeWindowValue: TextView
    private lateinit var seekBarCriticalWeight: SeekBar
    private lateinit var tvCriticalWeightValue: TextView
    private lateinit var seekBarHighWeight: SeekBar
    private lateinit var tvHighWeightValue: TextView
    private lateinit var spinnerMinSeverity: Spinner
    private lateinit var seekBarDiversityThreshold: SeekBar
    private lateinit var tvDiversityThresholdValue: TextView
    private lateinit var switchRequireDiversity: Switch
    private lateinit var switchEnableNotifications: Switch
    private lateinit var switchEnableSounds: Switch
    private lateinit var switchEnableVibration: Switch
    private lateinit var seekBarNotificationCooldown: SeekBar
    private lateinit var tvNotificationCooldownValue: TextView
    private lateinit var btnResetDefaults: Button
    private lateinit var btnTestAlert: Button
    
    // Components
    private lateinit var incidentManager: IncidentManager
    private lateinit var preferencesManager: PreferencesManager
    
    // Current settings
    private var currentSettings = AlertSettings()
    private var hasUnsavedChanges = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_settings)
        
        initializeComponents()
        setupUI()
        loadCurrentSettings()
        
        Log.d(TAG, "AlertSettingsActivity created")
    }
    
    private fun initializeComponents() {
        incidentManager = IncidentManager(this)
        preferencesManager = PreferencesManager(this)
        
        // Initialize UI components
        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        seekBarFrequencyThreshold = findViewById(R.id.seekBarFrequencyThreshold)
        tvFrequencyThresholdValue = findViewById(R.id.tvFrequencyThresholdValue)
        seekBarTimeWindow = findViewById(R.id.seekBarTimeWindow)
        tvTimeWindowValue = findViewById(R.id.tvTimeWindowValue)
        seekBarCriticalWeight = findViewById(R.id.seekBarCriticalWeight)
        tvCriticalWeightValue = findViewById(R.id.tvCriticalWeightValue)
        seekBarHighWeight = findViewById(R.id.seekBarHighWeight)
        tvHighWeightValue = findViewById(R.id.tvHighWeightValue)
        spinnerMinSeverity = findViewById(R.id.spinnerMinSeverity)
        seekBarDiversityThreshold = findViewById(R.id.seekBarDiversityThreshold)
        tvDiversityThresholdValue = findViewById(R.id.tvDiversityThresholdValue)
        switchRequireDiversity = findViewById(R.id.switchRequireDiversity)
        switchEnableNotifications = findViewById(R.id.switchEnableNotifications)
        switchEnableSounds = findViewById(R.id.switchEnableSounds)
        switchEnableVibration = findViewById(R.id.switchEnableVibration)
        seekBarNotificationCooldown = findViewById(R.id.seekBarNotificationCooldown)
        tvNotificationCooldownValue = findViewById(R.id.tvNotificationCooldownValue)
        btnResetDefaults = findViewById(R.id.btnResetDefaults)
        btnTestAlert = findViewById(R.id.btnTestAlert)
    }
    
    private fun setupUI() {
        // Back button
        btnBack.setOnClickListener {
            handleBackPressed()
        }
        
        // Save button
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        // Reset defaults button
        btnResetDefaults.setOnClickListener {
            resetToDefaults()
        }
        
        // Test alert button
        btnTestAlert.setOnClickListener {
            testAlert()
        }
        
        // Frequency threshold SeekBar
        seekBarFrequencyThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val threshold = if (progress == 0) 1 else progress
                tvFrequencyThresholdValue.text = threshold.toString()
                if (fromUser) {
                    currentSettings = currentSettings.copy(frequencyThreshold = threshold)
                    markAsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Time window SeekBar
        seekBarTimeWindow.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val timeWindow = if (progress < 5) 5 else progress
                tvTimeWindowValue.text = timeWindow.toString()
                if (fromUser) {
                    currentSettings = currentSettings.copy(timeWindowMinutes = timeWindow)
                    markAsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Critical weight SeekBar
        seekBarCriticalWeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val weight = (progress + 1) * 0.5f // 0.5 to 5.0
                tvCriticalWeightValue.text = String.format("%.1f", weight)
                if (fromUser) {
                    currentSettings = currentSettings.copy(criticalWeight = weight)
                    markAsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // High weight SeekBar
        seekBarHighWeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val weight = (progress + 1) * 0.3f // 0.3 to 3.0
                tvHighWeightValue.text = String.format("%.1f", weight)
                if (fromUser) {
                    currentSettings = currentSettings.copy(highWeight = weight)
                    markAsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Diversity threshold SeekBar
        seekBarDiversityThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val threshold = if (progress == 0) 1 else progress
                tvDiversityThresholdValue.text = threshold.toString()
                if (fromUser) {
                    currentSettings = currentSettings.copy(diversityThreshold = threshold)
                    markAsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Notification cooldown SeekBar
        seekBarNotificationCooldown.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val cooldown = if (progress < 1) 1 else progress
                tvNotificationCooldownValue.text = cooldown.toString()
                if (fromUser) {
                    currentSettings = currentSettings.copy(notificationCooldownMinutes = cooldown)
                    markAsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Switches
        switchRequireDiversity.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(requireDiversity = isChecked)
            markAsChanged()
        }
        
        switchEnableNotifications.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(enableNotifications = isChecked)
            updateNotificationDependentControls()
            markAsChanged()
        }
        
        switchEnableSounds.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(enableSounds = isChecked)
            markAsChanged()
        }
        
        switchEnableVibration.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(enableVibration = isChecked)
            markAsChanged()
        }
        
        // Setup minimum severity spinner
        setupMinSeveritySpinner()
    }
    
    private fun setupMinSeveritySpinner() {
        val severityOptions = arrayOf(
            "Niskie",
            "Średnie", 
            "Wysokie",
            "Krytyczne"
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, severityOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMinSeverity.adapter = adapter
        
        spinnerMinSeverity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val severity = when (position) {
                    0 -> IncidentSeverity.LOW
                    1 -> IncidentSeverity.MEDIUM
                    2 -> IncidentSeverity.HIGH
                    3 -> IncidentSeverity.CRITICAL
                    else -> IncidentSeverity.MEDIUM
                }
                currentSettings = currentSettings.copy(minSeverityForAlert = severity)
                markAsChanged()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            try {
                // Load saved settings or use defaults
                currentSettings = loadSavedSettings() ?: AlertSettings()
                updateUIFromSettings()
                hasUnsavedChanges = false
                updateSaveButton()
                
                Log.d(TAG, "Settings loaded: $currentSettings")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings", e)
                showError("Błąd podczas ładowania ustawień: ${e.message}")
            }
        }
    }
    
    private fun updateUIFromSettings() {
        // Frequency settings
        seekBarFrequencyThreshold.progress = currentSettings.frequencyThreshold
        tvFrequencyThresholdValue.text = currentSettings.frequencyThreshold.toString()
        
        seekBarTimeWindow.progress = currentSettings.timeWindowMinutes
        tvTimeWindowValue.text = currentSettings.timeWindowMinutes.toString()
        
        // Severity weights
        seekBarCriticalWeight.progress = ((currentSettings.criticalWeight / 0.5f) - 1).toInt()
        tvCriticalWeightValue.text = String.format("%.1f", currentSettings.criticalWeight)
        
        seekBarHighWeight.progress = ((currentSettings.highWeight / 0.3f) - 1).toInt()
        tvHighWeightValue.text = String.format("%.1f", currentSettings.highWeight)
        
        // Minimum severity spinner
        val severityIndex = when (currentSettings.minSeverityForAlert) {
            IncidentSeverity.LOW -> 0
            IncidentSeverity.MEDIUM -> 1
            IncidentSeverity.HIGH -> 2
            IncidentSeverity.CRITICAL -> 3
        }
        spinnerMinSeverity.setSelection(severityIndex)
        
        // Diversity settings
        seekBarDiversityThreshold.progress = currentSettings.diversityThreshold
        tvDiversityThresholdValue.text = currentSettings.diversityThreshold.toString()
        switchRequireDiversity.isChecked = currentSettings.requireDiversity
        
        // Notification settings
        switchEnableNotifications.isChecked = currentSettings.enableNotifications
        switchEnableSounds.isChecked = currentSettings.enableSounds
        switchEnableVibration.isChecked = currentSettings.enableVibration
        
        seekBarNotificationCooldown.progress = currentSettings.notificationCooldownMinutes
        tvNotificationCooldownValue.text = currentSettings.notificationCooldownMinutes.toString()
        
        updateNotificationDependentControls()
    }
    
    private fun updateNotificationDependentControls() {
        val enabled = switchEnableNotifications.isChecked
        switchEnableSounds.isEnabled = enabled
        switchEnableVibration.isEnabled = enabled
        seekBarNotificationCooldown.isEnabled = enabled
        btnTestAlert.isEnabled = enabled
    }
    
    private fun markAsChanged() {
        hasUnsavedChanges = true
        updateSaveButton()
    }
    
    private fun updateSaveButton() {
        btnSave.alpha = if (hasUnsavedChanges) 1.0f else 0.5f
        btnSave.isEnabled = hasUnsavedChanges
    }
    
    private fun saveSettings() {
        lifecycleScope.launch {
            try {
                // Save to preferences
                saveSettingsToPreferences(currentSettings)
                
                // Update IncidentManager with new settings
                incidentManager.updateAlertSettings(currentSettings)
                
                hasUnsavedChanges = false
                updateSaveButton()
                
                showSuccess("Ustawienia zostały zapisane")
                Log.d(TAG, "Settings saved successfully: $currentSettings")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving settings", e)
                showError("Błąd podczas zapisywania ustawień: ${e.message}")
            }
        }
    }
    
    private fun resetToDefaults() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Przywróć domyślne ustawienia")
            .setMessage("Czy na pewno chcesz przywrócić wszystkie ustawienia do wartości domyślnych? Ta operacja jest nieodwracalna.")
            .setPositiveButton("Tak") { _, _ ->
                currentSettings = AlertSettings() // Default settings
                updateUIFromSettings()
                markAsChanged()
                showSuccess("Przywrócono domyślne ustawienia")
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
    
    private fun testAlert() {
        lifecycleScope.launch {
            try {
                showInfo("Wysyłanie testowego alertu...")
                
                // Create a test incident
                val testIncident = Incident(
                    id = "test_${System.currentTimeMillis()}",
                    deviceId = "test_device",
                    deviceName = "Urządzenie testowe",
                    timestamp = System.currentTimeMillis(),
                    description = "TEST: Alert testowy z ustawień",
                    severity = currentSettings.minSeverityForAlert,
                    confidence = 1.0f,
                    detectedKeywords = listOf("test", "alert"),
                    extractedText = "To jest testowy alert wysłany z ustawień aplikacji.",
                    isReviewed = false
                )
                
                // Send test notification
                incidentManager.sendTestNotification(testIncident, currentSettings)
                
                showSuccess("Alert testowy został wysłany!")
                Log.d(TAG, "Test alert sent successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending test alert", e)
                showError("Błąd podczas wysyłania alertu testowego: ${e.message}")
            }
        }
    }
    
    private fun handleBackPressed() {
        if (hasUnsavedChanges) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Niezapisane zmiany")
                .setMessage("Masz niezapisane zmiany. Czy chcesz je zapisać przed wyjściem?")
                .setPositiveButton("Zapisz") { _, _ ->
                    saveSettings()
                    finish()
                }
                .setNegativeButton("Odrzuć") { _, _ ->
                    finish()
                }
                .setNeutralButton("Anuluj", null)
                .show()
        } else {
            finish()
        }
    }
    
    // Settings persistence methods
    private fun loadSavedSettings(): AlertSettings? {
        return try {
            val prefs = getSharedPreferences("alert_settings", MODE_PRIVATE)
            
            AlertSettings(
                frequencyThreshold = prefs.getInt("frequency_threshold", 5),
                timeWindowMinutes = prefs.getInt("time_window_minutes", 60),
                criticalWeight = prefs.getFloat("critical_weight", 5.0f),
                highWeight = prefs.getFloat("high_weight", 3.0f),
                minSeverityForAlert = IncidentSeverity.valueOf(
                    prefs.getString("min_severity", IncidentSeverity.MEDIUM.name) ?: IncidentSeverity.MEDIUM.name
                ),
                diversityThreshold = prefs.getInt("diversity_threshold", 3),
                requireDiversity = prefs.getBoolean("require_diversity", true),
                enableNotifications = prefs.getBoolean("enable_notifications", true),
                enableSounds = prefs.getBoolean("enable_sounds", true),
                enableVibration = prefs.getBoolean("enable_vibration", true),
                notificationCooldownMinutes = prefs.getInt("notification_cooldown_minutes", 15)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved settings", e)
            null
        }
    }
    
    private fun saveSettingsToPreferences(settings: AlertSettings) {
        val prefs = getSharedPreferences("alert_settings", MODE_PRIVATE)
        prefs.edit().apply {
            putInt("frequency_threshold", settings.frequencyThreshold)
            putInt("time_window_minutes", settings.timeWindowMinutes)
            putFloat("critical_weight", settings.criticalWeight)
            putFloat("high_weight", settings.highWeight)
            putString("min_severity", settings.minSeverityForAlert.name)
            putInt("diversity_threshold", settings.diversityThreshold)
            putBoolean("require_diversity", settings.requireDiversity)
            putBoolean("enable_notifications", settings.enableNotifications)
            putBoolean("enable_sounds", settings.enableSounds)
            putBoolean("enable_vibration", settings.enableVibration)
            putInt("notification_cooldown_minutes", settings.notificationCooldownMinutes)
            apply()
        }
    }
    
    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showInfo(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        handleBackPressed()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        incidentManager.shutdown()
        Log.d(TAG, "AlertSettingsActivity destroyed")
    }
}
