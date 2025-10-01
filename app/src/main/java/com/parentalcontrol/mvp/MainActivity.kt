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
import android.widget.Toast
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
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var prefsManager: PreferencesManager
    private lateinit var fileLogger: FileLogger
    
    private var isServiceRunning = false
    private val logUpdateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var logUpdateJob: Job? = null
    
    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val PERMISSION_REQUEST_CODE = 2001
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
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefsManager = PreferencesManager(this)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        fileLogger = FileLogger(this)
        
        setupUI()
        checkPermissions()
        updateServiceStatus()
        startLogUpdates()
    }
    
    private fun setupUI() {
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
            
            // Edytor słów kluczowych
            btnKeywordsEditor.setOnClickListener {
                startActivity(Intent(this@MainActivity, KeywordsEditorActivity::class.java))
            }
            
            // Zarządzanie urządzeniami
            btnManageDevices.setOnClickListener {
                startActivity(Intent(this@MainActivity, PairedDevicesActivity::class.java))
            }
            
            // Przeglądanie incydentów
            btnViewIncidents.setOnClickListener {
                startActivity(Intent(this@MainActivity, IncidentsActivity::class.java))
            }
            
            // Ustawienia alertów
            btnAlertSettings.setOnClickListener {
                startActivity(Intent(this@MainActivity, AlertSettingsActivity::class.java))
            }
            
            // Parowanie urządzeń
            btnPairDevice.setOnClickListener {
                showDeviceTypeSelectionDialog()
            }
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
     * Ładuje 3 najnowsze logi z pliku
     */
    private suspend fun loadRecentLogs(): List<String> = withContext(Dispatchers.IO) {
        try {
            val logFile = File(getExternalFilesDir(null), "monitoring_logs.txt")
            if (!logFile.exists()) {
                return@withContext emptyList()
            }
            
            val lines = logFile.readLines()
            val recentLogs = lines.takeLast(3).reversed() // 3 najnowsze, od najnowszego
            
            // Formatuj logi dla wyświetlenia
            recentLogs.map { line ->
                formatLogLine(line)
            }
        } catch (e: Exception) {
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
    
    override fun onDestroy() {
        super.onDestroy()
        logUpdateJob?.cancel()
        logUpdateScope.cancel()
    }
}
