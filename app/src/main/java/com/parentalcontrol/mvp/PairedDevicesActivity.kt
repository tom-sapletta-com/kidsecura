package com.parentalcontrol.mvp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.parentalcontrol.mvp.adapter.PairedDevicesAdapter
import com.parentalcontrol.mvp.manager.PairedDevicesManager
import com.parentalcontrol.mvp.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Aktywność do zarządzania sparowanymi urządzeniami
 */
class PairedDevicesActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PairedDevicesActivity"
    }
    
    // UI Components
    private lateinit var btnBack: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var tvTotalDevices: TextView
    private lateinit var tvActiveDevices: TextView
    private lateinit var tvParentDevices: TextView
    private lateinit var tvChildDevices: TextView
    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterActive: Button
    private lateinit var btnFilterParents: Button
    private lateinit var btnFilterChildren: Button
    private lateinit var rvDevices: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var fabAddDevice: FloatingActionButton
    
    // Components
    private lateinit var pairedDevicesManager: PairedDevicesManager
    private lateinit var devicesAdapter: PairedDevicesAdapter
    
    // State
    private var currentFilter = DeviceFilter.ALL
    private var allDevices = listOf<PairedDevice>()
    
    enum class DeviceFilter {
        ALL, ACTIVE, PARENTS, CHILDREN
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paired_devices)
        
        initializeComponents()
        setupUI()
        loadDevices()
        startAutoRefresh()
        
        Log.d(TAG, "PairedDevicesActivity created")
    }
    
    private fun initializeComponents() {
        pairedDevicesManager = PairedDevicesManager(this)
        
        // Initialize UI components
        btnBack = findViewById(R.id.btnBack)
        btnRefresh = findViewById(R.id.btnRefresh)
        tvTotalDevices = findViewById(R.id.tvTotalDevices)
        tvActiveDevices = findViewById(R.id.tvActiveDevices)
        tvParentDevices = findViewById(R.id.tvParentDevices)
        tvChildDevices = findViewById(R.id.tvChildDevices)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterActive = findViewById(R.id.btnFilterActive)
        btnFilterParents = findViewById(R.id.btnFilterParents)
        btnFilterChildren = findViewById(R.id.btnFilterChildren)
        rvDevices = findViewById(R.id.rvDevices)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        fabAddDevice = findViewById(R.id.fabAddDevice)
        
        // Setup RecyclerView
        devicesAdapter = PairedDevicesAdapter { device, action ->
            handleDeviceAction(device, action)
        }
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = devicesAdapter
    }
    
    private fun setupUI() {
        // Back button
        btnBack.setOnClickListener {
            finish()
        }
        
        // Refresh button
        btnRefresh.setOnClickListener {
            refreshDevices()
        }
        
        // Filter buttons
        btnFilterAll.setOnClickListener {
            setFilter(DeviceFilter.ALL)
        }
        
        btnFilterActive.setOnClickListener {
            setFilter(DeviceFilter.ACTIVE)
        }
        
        btnFilterParents.setOnClickListener {
            setFilter(DeviceFilter.PARENTS)
        }
        
        btnFilterChildren.setOnClickListener {
            setFilter(DeviceFilter.CHILDREN)
        }
        
        // Add device button
        fabAddDevice.setOnClickListener {
            startPairing()
        }
    }
    
    private fun loadDevices() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading paired devices...")
                
                allDevices = pairedDevicesManager.getAllPairedDevices()
                Log.d(TAG, "Loaded ${allDevices.size} paired devices")
                
                updateStatistics()
                applyCurrentFilter()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading devices", e)
                showError("Błąd podczas ładowania urządzeń: ${e.message}")
            }
        }
    }
    
    private fun refreshDevices() {
        Log.d(TAG, "Refreshing devices...")
        btnRefresh.isEnabled = false
        
        lifecycleScope.launch {
            try {
                // Symuluj sprawdzanie statusu urządzeń
                allDevices.forEach { device ->
                    // W rzeczywistej implementacji sprawdziłbyś połączenie
                    // pairedDevicesManager.pingDevice(device.deviceId)
                }
                
                loadDevices()
                
                delay(1000) // Krótkie opóźnienie dla UX
                
            } finally {
                btnRefresh.isEnabled = true
            }
        }
    }
    
    private fun updateStatistics() {
        val status = pairedDevicesManager.getDeviceManagementStatus()
        
        tvTotalDevices.text = status.totalPairedDevices.toString()
        tvActiveDevices.text = status.activeDevices.toString()
        tvParentDevices.text = status.parentDevices.toString()
        tvChildDevices.text = status.childDevices.toString()
        
        Log.d(TAG, "Statistics updated: ${status.totalPairedDevices} total, ${status.activeDevices} active")
    }
    
    private fun setFilter(filter: DeviceFilter) {
        currentFilter = filter
        updateFilterButtons()
        applyCurrentFilter()
        
        Log.d(TAG, "Filter changed to: $filter")
    }
    
    private fun updateFilterButtons() {
        // Reset all buttons
        resetFilterButton(btnFilterAll)
        resetFilterButton(btnFilterActive)
        resetFilterButton(btnFilterParents)
        resetFilterButton(btnFilterChildren)
        
        // Highlight selected button
        when (currentFilter) {
            DeviceFilter.ALL -> selectFilterButton(btnFilterAll)
            DeviceFilter.ACTIVE -> selectFilterButton(btnFilterActive)
            DeviceFilter.PARENTS -> selectFilterButton(btnFilterParents)
            DeviceFilter.CHILDREN -> selectFilterButton(btnFilterChildren)
        }
    }
    
    private fun resetFilterButton(button: Button) {
        button.setBackgroundResource(R.drawable.filter_button_unselected)
        button.setTextColor(resources.getColor(android.R.color.darker_gray, null))
    }
    
    private fun selectFilterButton(button: Button) {
        button.setBackgroundResource(R.drawable.filter_button_selected)
        button.setTextColor(resources.getColor(android.R.color.white, null))
    }
    
    private fun applyCurrentFilter() {
        val filteredDevices = when (currentFilter) {
            DeviceFilter.ALL -> allDevices
            DeviceFilter.ACTIVE -> pairedDevicesManager.getActiveDevices()
            DeviceFilter.PARENTS -> pairedDevicesManager.getParentDevices()
            DeviceFilter.CHILDREN -> pairedDevicesManager.getChildDevices()
        }
        
        devicesAdapter.updateDevices(filteredDevices)
        
        // Show/hide empty state
        if (filteredDevices.isEmpty()) {
            rvDevices.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            rvDevices.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
        
        Log.d(TAG, "Filter applied: ${filteredDevices.size} devices shown")
    }
    
    private fun handleDeviceAction(device: PairedDevice, action: String) {
        when (action) {
            "edit_nickname" -> showEditNicknameDialog(device)
            "remove" -> showRemoveDeviceDialog(device)
            "ping" -> pingDevice(device)
            "view_details" -> showDeviceDetails(device)
        }
    }
    
    private fun showEditNicknameDialog(device: PairedDevice) {
        val input = EditText(this).apply {
            setText(device.nickname ?: "")
            hint = "Wprowadź nazwę urządzenia"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edytuj nazwę urządzenia")
            .setMessage("Urządzenie: ${device.deviceName}")
            .setView(input)
            .setPositiveButton("Zapisz") { _, _ ->
                val nickname = input.text.toString().trim()
                updateDeviceNickname(device, nickname.ifEmpty { null })
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
    
    private fun showRemoveDeviceDialog(device: PairedDevice) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Usuń urządzenie")
            .setMessage("Czy na pewno chcesz usunąć urządzenie \"${device.deviceName}\"?\n\nTa operacja nie może być cofnięta.")
            .setPositiveButton("Usuń") { _, _ ->
                removeDevice(device)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
    
    private fun updateDeviceNickname(device: PairedDevice, nickname: String?) {
        lifecycleScope.launch {
            try {
                pairedDevicesManager.updateDeviceNickname(device.deviceId, nickname)
                loadDevices() // Odśwież listę
                showSuccess("Nazwa urządzenia została zaktualizowana")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating device nickname", e)
                showError("Błąd podczas aktualizacji nazwy: ${e.message}")
            }
        }
    }
    
    private fun removeDevice(device: PairedDevice) {
        lifecycleScope.launch {
            try {
                val success = pairedDevicesManager.removePairedDevice(device.deviceId)
                if (success) {
                    loadDevices() // Odśwież listę
                    showSuccess("Urządzenie zostało usunięte")
                } else {
                    showError("Nie można usunąć urządzenia")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error removing device", e)
                showError("Błąd podczas usuwania urządzenia: ${e.message}")
            }
        }
    }
    
    private fun pingDevice(device: PairedDevice) {
        lifecycleScope.launch {
            try {
                showInfo("Sprawdzanie połączenia z ${device.deviceName}...")
                
                // TODO: Implement actual ping
                delay(1000)
                
                val isOnline = (1..10).random() > 3 // Symulacja
                if (isOnline) {
                    pairedDevicesManager.updateDeviceConnectionStatus(device.deviceId, ConnectionStatus.CONNECTED)
                    showSuccess("Urządzenie ${device.deviceName} jest online")
                } else {
                    pairedDevicesManager.updateDeviceConnectionStatus(device.deviceId, ConnectionStatus.DISCONNECTED)
                    showError("Urządzenie ${device.deviceName} jest offline")
                }
                
                loadDevices() // Odśwież status
                
            } catch (e: Exception) {
                Log.e(TAG, "Error pinging device", e)
                showError("Błąd podczas sprawdzania połączenia: ${e.message}")
            }
        }
    }
    
    private fun showDeviceDetails(device: PairedDevice) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        
        val details = buildString {
            appendLine("Urządzenie: ${device.deviceName}")
            appendLine("Typ: ${device.deviceType}")
            appendLine("ID: ${device.deviceId}")
            appendLine("IP: ${device.ipAddress}:${device.port}")
            appendLine("Status: ${device.connectionStatus}")
            appendLine("Sparowano: ${dateFormat.format(Date(device.pairingDate))}")
            appendLine("Ostatnia aktywność: ${dateFormat.format(Date(device.lastSeen))}")
            if (device.nickname != null) {
                appendLine("Nazwa: ${device.nickname}")
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Szczegóły urządzenia")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun startPairing() {
        // TODO: Uruchom aktywność parowania
        showInfo("Uruchamianie parowania...")
        
        // W rzeczywistej implementacji:
        // startActivity(Intent(this, PairingActivity::class.java))
    }
    
    private fun startAutoRefresh() {
        lifecycleScope.launch {
            while (true) {
                delay(30000) // 30 sekund
                if (!isFinishing && !isDestroyed) {
                    loadDevices()
                }
            }
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
    
    override fun onDestroy() {
        super.onDestroy()
        pairedDevicesManager.shutdown()
        Log.d(TAG, "PairedDevicesActivity destroyed")
    }
}
