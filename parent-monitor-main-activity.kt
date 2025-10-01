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
import com.parentalcontrol.mvp.service.ScreenCaptureService
import com.parentalcontrol.mvp.utils.PreferencesManager

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var prefsManager: PreferencesManager
    
    private var isServiceRunning = false
    
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
            Toast.makeText(this, "Odmowa dostępu do ekranu", Toast.LENGTH_SHORT).show()
            updateUI(false)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefsManager = PreferencesManager(this)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        setupUI()
        checkPermissions()
        updateServiceStatus()
    }
    
    private fun setupUI() {
        binding.apply {
            // Przycisk rozpoczęcia/zatrzymania monitorowania
            btnToggleMonitoring.setOnClickListener {
                if (isServiceRunning) {
                    stopMonitoring()
                } else {
                    showConsentDialog()
                }
            }
            
            // Konfiguracja interwału
            seekBarInterval.apply {
                min = 1
                max = 10
                progress = prefsManager.getCaptureInterval()
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        tvIntervalValue.text = "$progress sekund"
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
            
            // Przycisk historii
            btnViewHistory.setOnClickListener {
                startActivity(Intent(this@MainActivity, EventHistoryActivity::class.java))
            }
            
            // Przycisk parowania
            btnPairDevice.setOnClickListener {
                showPairingDialog()
            }
        }
    }
    
    private fun showConsentDialog() {
        AlertDialog.Builder(this)
            .setTitle("Zgoda na monitorowanie")
            .setMessage("""
                Ta aplikacja będzie przechwytywać zawartość ekranu w celu ochrony dziecka przed szkodliwymi treściami.
                
                • Zrzuty ekranu będą robione co ${prefsManager.getCaptureInterval()} sekund
                • Analizowana będzie ${if (prefsManager.isCropBottomEnabled()) "dolna połowa" else "cały"} ekran
                • Dane są przetwarzane ${if (prefsManager.isLocalAnalysisEnabled()) "lokalnie" else "w chmurze"}
                • Zrzuty ${if (prefsManager.isSaveScreenshotsEnabled()) "będą" else "nie będą"} zapisywane
                
                Czy wyrażasz zgodę na monitorowanie?
            """.trimIndent())
            .setPositiveButton("Tak, zgadzam się") { _, _ ->
                startMonitoring()
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
    
    private fun showPairingDialog() {
        val pairingCode = generatePairingCode()
        AlertDialog.Builder(this)
            .setTitle("Kod parowania")
            .setMessage("Kod parowania z aplikacją rodzica:\n\n$pairingCode\n\nWprowadź ten kod w aplikacji rodzica")
            .setPositiveButton("Skopiowano") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Pairing Code", pairingCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Kod skopiowany", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun generatePairingCode(): String {
        return (100000..999999).random().toString()
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
    
    private fun startMonitoring() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(captureIntent)
    }
    
    private fun stopMonitoring() {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        stopService(serviceIntent)
        updateUI(false)
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
        Toast.makeText(this, "Monitorowanie rozpoczęte", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateServiceStatus() {
        isServiceRunning = ScreenCaptureService.isRunning
        updateUI(isServiceRunning)
    }
    
    private fun updateUI(isRunning: Boolean) {
        isServiceRunning = isRunning
        binding.apply {
            btnToggleMonitoring.text = if (isRunning) "Zatrzymaj monitorowanie" else "Rozpocznij monitorowanie"
            btnToggleMonitoring.setBackgroundColor(
                ContextCompat.getColor(this@MainActivity, 
                    if (isRunning) android.R.color.holo_red_dark else android.R.color.holo_green_dark)
            )
            
            // Wyłącz ustawienia podczas działania
            seekBarInterval.isEnabled = !isRunning
            switchCropBottom.isEnabled = !isRunning
            switchLocalAnalysis.isEnabled = !isRunning
            switchSaveScreenshots.isEnabled = !isRunning
            
            tvStatus.text = if (isRunning) "Status: Aktywne monitorowanie" else "Status: Nieaktywne"
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }
}