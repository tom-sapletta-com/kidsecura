package com.parentalcontrol.mvp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.parentalcontrol.mvp.databinding.ActivityQrscannerBinding
import com.parentalcontrol.mvp.model.DeviceType
import com.parentalcontrol.mvp.model.PairingData
import com.parentalcontrol.mvp.service.PairingService
import com.parentalcontrol.mvp.utils.QRCodeGenerator
import com.google.gson.Gson

class QRScannerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "QRScannerActivity"
        const val EXTRA_PAIRING_DATA = "pairing_data"
        const val RESULT_PAIRING_SUCCESS = 100
        const val RESULT_PAIRING_FAILED = 101
    }
    
    private lateinit var binding: ActivityQrscannerBinding
    private lateinit var qrCodeGenerator: QRCodeGenerator
    private lateinit var pairingService: PairingService
    
    private var isScanning = false
    
    // Request permission launcher
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startScanning()
        } else {
            showCameraPermissionError()
        }
    }
    
    private val barcodeCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
            if (result != null && isScanning) {
                isScanning = false
                binding.barcodeScanner.pause()
                handleQRCodeResult(result.text)
            }
        }
        
        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {
            // Optional: można dodać wizualne wskaźniki punktów wykrycia
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrscannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        qrCodeGenerator = QRCodeGenerator(this)
        pairingService = PairingService(this)
        
        setupUI()
        checkCameraPermission()
    }
    
    private fun setupUI() {
        binding.apply {
            // Konfiguracja skanera
            barcodeScanner.barcodeView.decoderFactory = DefaultDecoderFactory()
            barcodeScanner.initializeFromIntent(intent)
            barcodeScanner.decodeContinuous(barcodeCallback)
            
            // Przyciski
            btnCancel.setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
            
            btnFlashlight.setOnClickListener {
                toggleFlashlight()
            }
            
            // Informacje
            tvInstructions.text = getString(R.string.qr_scanner_instructions)
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startScanning()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startScanning() {
        if (!isScanning) {
            isScanning = true
            binding.barcodeScanner.resume()
            binding.tvStatus.text = getString(R.string.scanning_for_qr)
        }
    }
    
    private fun stopScanning() {
        if (isScanning) {
            isScanning = false
            binding.barcodeScanner.pause()
        }
    }
    
    private fun handleQRCodeResult(qrContent: String) {
        Log.d(TAG, "QR Code scanned: $qrContent")
        
        binding.tvStatus.text = getString(R.string.processing_qr_code)
        
        // Parsuj dane parowania
        val pairingData = qrCodeGenerator.parsePairingData(qrContent)
        
        if (pairingData == null) {
            showError(getString(R.string.invalid_qr_code))
            return
        }
        
        // Sprawdź czy urządzenia są w tej samej sieci
        if (!qrCodeGenerator.isInSameNetwork(pairingData)) {
            showError(getString(R.string.different_wifi_network))
            return
        }
        
        // Rozpocznij proces parowania
        startPairingProcess(pairingData)
    }
    
    private fun startPairingProcess(pairingData: PairingData) {
        binding.tvStatus.text = getString(R.string.starting_pairing_process)
        
        try {
            // Sprawdź typ urządzenia
            val isValidPairing = when {
                // Urządzenie rodzica skanuje kod dziecka
                pairingData.deviceType == DeviceType.CHILD -> true
                // Urządzenie dziecka skanuje kod rodzica
                pairingData.deviceType == DeviceType.PARENT -> true
                else -> false
            }
            
            if (!isValidPairing) {
                showError(getString(R.string.invalid_device_type))
                return
            }
            
            // Rozpocznij komunikację z urządzeniem
            pairingService.startPairing(pairingData) { success, message ->
                runOnUiThread {
                    if (success) {
                        handlePairingSuccess(pairingData)
                    } else {
                        handlePairingFailure(message ?: getString(R.string.pairing_failed))
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting pairing process", e)
            showError(getString(R.string.pairing_error_occurred))
        }
    }
    
    private fun handlePairingSuccess(pairingData: PairingData) {
        Log.d(TAG, "Pairing successful with device: ${pairingData.deviceName}")
        
        binding.tvStatus.text = getString(R.string.pairing_successful)
        
        // Przekaż dane parowania z powrotem
        val resultIntent = Intent().apply {
            putExtra(EXTRA_PAIRING_DATA, qrCodeGenerator.gson.toJson(pairingData))
        }
        
        setResult(RESULT_PAIRING_SUCCESS, resultIntent)
        
        Toast.makeText(this, 
            getString(R.string.paired_with_device, pairingData.deviceName), 
            Toast.LENGTH_LONG).show()
        
        // Zakończ aktywność po krótkiej pauzie
        binding.root.postDelayed({ finish() }, 2000)
    }
    
    private fun handlePairingFailure(message: String) {
        Log.e(TAG, "Pairing failed: $message")
        
        binding.tvStatus.text = getString(R.string.pairing_failed)
        showError(message)
        
        // Wznów skanowanie po błędzie
        binding.root.postDelayed({ 
            restartScanning() 
        }, 3000)
    }
    
    private fun restartScanning() {
        binding.tvStatus.text = getString(R.string.scanning_for_qr)
        startScanning()
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.tvStatus.text = message
    }
    
    private fun showCameraPermissionError() {
        showError(getString(R.string.camera_permission_required))
        setResult(RESULT_CANCELED)
        finish()
    }
    
    private fun toggleFlashlight() {
        try {
            val cameraSettings = binding.barcodeScanner.barcodeView.cameraSettings
            if (cameraSettings.isAutoFocusEnabled) {
                binding.barcodeScanner.setTorchOff()
                binding.btnFlashlight.text = getString(R.string.flashlight_on)
            } else {
                binding.barcodeScanner.setTorchOn()
                binding.btnFlashlight.text = getString(R.string.flashlight_off)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            startScanning()
        }
    }
    
    override fun onPause() {
        super.onPause()
        stopScanning()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pairingService.cleanup()
    }
}
