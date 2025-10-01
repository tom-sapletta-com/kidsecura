package com.parentalcontrol.mvp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.mvp.adapter.IncidentsAdapter
import com.parentalcontrol.mvp.manager.IncidentManager
import com.parentalcontrol.mvp.manager.PairedDevicesManager
import com.parentalcontrol.mvp.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Aktywność do przeglądania i zarządzania incydentami bezpieczeństwa
 */
class IncidentsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "IncidentsActivity"
    }
    
    // UI Components
    private lateinit var btnBack: ImageButton
    private lateinit var btnFilter: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var tvTotalIncidents: TextView
    private lateinit var tvLast24h: TextView
    private lateinit var tvCriticalIncidents: TextView
    private lateinit var tvUnreviewed: TextView
    private lateinit var layoutFilterPanel: LinearLayout
    private lateinit var cbCritical: CheckBox
    private lateinit var cbHigh: CheckBox
    private lateinit var cbMedium: CheckBox
    private lateinit var cbLow: CheckBox
    private lateinit var spinnerTimeRange: Spinner
    private lateinit var spinnerDevice: Spinner
    private lateinit var cbShowReviewed: CheckBox
    private lateinit var cbShowUnreviewed: CheckBox
    private lateinit var btnApplyFilters: Button
    private lateinit var btnSortTime: Button
    private lateinit var btnSortSeverity: Button
    private lateinit var btnSortDevice: Button
    private lateinit var tvIncidentCount: TextView
    private lateinit var rvIncidents: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var layoutLoading: LinearLayout
    
    // Components
    private lateinit var incidentManager: IncidentManager
    private lateinit var pairedDevicesManager: PairedDevicesManager
    private lateinit var incidentsAdapter: IncidentsAdapter
    
    // State
    private var allIncidents = listOf<Incident>()
    private var filteredIncidents = listOf<Incident>()
    private var currentFilter = IncidentFilter()
    private var currentSort = SortType.TIME_DESC
    private var isFilterPanelVisible = false
    
    enum class SortType {
        TIME_DESC, TIME_ASC, SEVERITY_DESC, SEVERITY_ASC, DEVICE_NAME
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incidents)
        
        initializeComponents()
        setupUI()
        setupFilters()
        loadIncidents()
        startAutoRefresh()
        
        Log.d(TAG, "IncidentsActivity created")
    }
    
    private fun initializeComponents() {
        incidentManager = IncidentManager(this)
        pairedDevicesManager = PairedDevicesManager(this)
        
        // Initialize UI components
        btnBack = findViewById(R.id.btnBack)
        btnFilter = findViewById(R.id.btnFilter)
        btnRefresh = findViewById(R.id.btnRefresh)
        tvTotalIncidents = findViewById(R.id.tvTotalIncidents)
        tvLast24h = findViewById(R.id.tvLast24h)
        tvCriticalIncidents = findViewById(R.id.tvCriticalIncidents)
        tvUnreviewed = findViewById(R.id.tvUnreviewed)
        layoutFilterPanel = findViewById(R.id.layoutFilterPanel)
        cbCritical = findViewById(R.id.cbCritical)
        cbHigh = findViewById(R.id.cbHigh)
        cbMedium = findViewById(R.id.cbMedium)
        cbLow = findViewById(R.id.cbLow)
        spinnerTimeRange = findViewById(R.id.spinnerTimeRange)
        spinnerDevice = findViewById(R.id.spinnerDevice)
        cbShowReviewed = findViewById(R.id.cbShowReviewed)
        cbShowUnreviewed = findViewById(R.id.cbShowUnreviewed)
        btnApplyFilters = findViewById(R.id.btnApplyFilters)
        btnSortTime = findViewById(R.id.btnSortTime)
        btnSortSeverity = findViewById(R.id.btnSortSeverity)
        btnSortDevice = findViewById(R.id.btnSortDevice)
        tvIncidentCount = findViewById(R.id.tvIncidentCount)
        rvIncidents = findViewById(R.id.rvIncidents)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        layoutLoading = findViewById(R.id.layoutLoading)
        
        // Setup RecyclerView
        incidentsAdapter = IncidentsAdapter { incident, action ->
            handleIncidentAction(incident, action)
        }
        rvIncidents.layoutManager = LinearLayoutManager(this)
        rvIncidents.adapter = incidentsAdapter
    }
    
    private fun setupUI() {
        // Back button
        btnBack.setOnClickListener {
            finish()
        }
        
        // Filter toggle
        btnFilter.setOnClickListener {
            toggleFilterPanel()
        }
        
        // Refresh button
        btnRefresh.setOnClickListener {
            refreshIncidents()
        }
        
        // Apply filters button
        btnApplyFilters.setOnClickListener {
            applyFilters()
        }
        
        // Sort buttons
        btnSortTime.setOnClickListener {
            setSort(if (currentSort == SortType.TIME_DESC) SortType.TIME_ASC else SortType.TIME_DESC)
        }
        
        btnSortSeverity.setOnClickListener {
            setSort(if (currentSort == SortType.SEVERITY_DESC) SortType.SEVERITY_ASC else SortType.SEVERITY_DESC)
        }
        
        btnSortDevice.setOnClickListener {
            setSort(SortType.DEVICE_NAME)
        }
    }
    
    private fun setupFilters() {
        // Time range filter
        val timeRanges = arrayOf("Wszystkie", "Ostatnia godzina", "Ostatnie 24h", "Ostatni tydzień", "Ostatni miesiąc")
        val timeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeRanges)
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimeRange.adapter = timeAdapter
        
        // Device filter - will be populated when devices are loaded
        updateDeviceSpinner()
    }
    
    private fun updateDeviceSpinner() {
        val devices = pairedDevicesManager.getAllPairedDevices()
        val deviceNames = mutableListOf("Wszystkie urządzenia")
        deviceNames.addAll(devices.map { it.deviceName })
        
        val deviceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDevice.adapter = deviceAdapter
    }
    
    private fun toggleFilterPanel() {
        isFilterPanelVisible = !isFilterPanelVisible
        layoutFilterPanel.visibility = if (isFilterPanelVisible) View.VISIBLE else View.GONE
        
        // Update filter button appearance
        btnFilter.alpha = if (isFilterPanelVisible) 1.0f else 0.7f
        
        Log.d(TAG, "Filter panel visibility: $isFilterPanelVisible")
    }
    
    private fun loadIncidents() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                Log.d(TAG, "Loading incidents...")
                
                // Get all incidents from last 30 days
                val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
                allIncidents = incidentManager.getRecentIncidents(30 * 24 * 60 * 60 * 1000L)
                
                Log.d(TAG, "Loaded ${allIncidents.size} incidents")
                
                updateStatistics()
                applyCurrentFilter()
                updateDeviceSpinner()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading incidents", e)
                showError("Błąd podczas ładowania incydentów: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun refreshIncidents() {
        Log.d(TAG, "Refreshing incidents...")
        btnRefresh.isEnabled = false
        
        lifecycleScope.launch {
            try {
                loadIncidents()
                delay(1000) // Krótkie opóźnienie dla UX
                showSuccess("Incydenty odświeżone")
                
            } finally {
                btnRefresh.isEnabled = true
            }
        }
    }
    
    private fun updateStatistics() {
        val stats = incidentManager.getIncidentStatistics()
        
        tvTotalIncidents.text = stats.totalIncidents.toString()
        tvLast24h.text = stats.incidentsLast24h.toString()
        tvCriticalIncidents.text = stats.criticalIncidents.toString()
        
        val unreviewed = allIncidents.count { !it.isReviewed }
        tvUnreviewed.text = unreviewed.toString()
        
        Log.d(TAG, "Statistics updated: ${stats.totalIncidents} total, ${stats.incidentsLast24h} last 24h")
    }
    
    private fun applyFilters() {
        // Build filter based on UI state
        val severityLevels = mutableListOf<IncidentSeverity>()
        if (cbCritical.isChecked) severityLevels.add(IncidentSeverity.CRITICAL)
        if (cbHigh.isChecked) severityLevels.add(IncidentSeverity.HIGH)
        if (cbMedium.isChecked) severityLevels.add(IncidentSeverity.MEDIUM)
        if (cbLow.isChecked) severityLevels.add(IncidentSeverity.LOW)
        
        // Time range
        val currentTime = System.currentTimeMillis()
        val timeRangeStart = when (spinnerTimeRange.selectedItemPosition) {
            1 -> currentTime - (60 * 60 * 1000L) // Last hour
            2 -> currentTime - (24 * 60 * 60 * 1000L) // Last 24h
            3 -> currentTime - (7 * 24 * 60 * 60 * 1000L) // Last week
            4 -> currentTime - (30 * 24 * 60 * 60 * 1000L) // Last month
            else -> null
        }
        
        // Device filter
        val selectedDevice = if (spinnerDevice.selectedItemPosition > 0) {
            pairedDevicesManager.getAllPairedDevices().getOrNull(spinnerDevice.selectedItemPosition - 1)?.deviceId
        } else null
        
        currentFilter = IncidentFilter(
            deviceIds = if (selectedDevice != null) listOf(selectedDevice) else null,
            severityLevels = severityLevels.ifEmpty { null },
            timeRangeStart = timeRangeStart,
            timeRangeEnd = currentTime,
            showReviewedOnly = cbShowReviewed.isChecked && !cbShowUnreviewed.isChecked,
            showUnreviewedOnly = cbShowUnreviewed.isChecked && !cbShowReviewed.isChecked
        )
        
        applyCurrentFilter()
        toggleFilterPanel() // Hide panel after applying
        
        Log.d(TAG, "Filters applied - showing ${filteredIncidents.size} incidents")
    }
    
    private fun applyCurrentFilter() {
        filteredIncidents = allIncidents.filter { incident ->
            // Severity filter
            val severityLevels = currentFilter.severityLevels
            if (severityLevels != null && incident.severity !in severityLevels) {
                return@filter false
            }
            
            // Time range filter
            val timeRangeStart = currentFilter.timeRangeStart
            if (timeRangeStart != null && incident.timestamp < timeRangeStart) {
                return@filter false
            }
            
            val timeRangeEnd = currentFilter.timeRangeEnd
            if (timeRangeEnd != null && incident.timestamp > timeRangeEnd) {
                return@filter false
            }
            
            // Device filter
            val deviceIds = currentFilter.deviceIds
            if (deviceIds != null && incident.deviceId !in deviceIds) {
                return@filter false
            }
            
            // Review status filter
            if (currentFilter.showReviewedOnly && !incident.isReviewed) {
                return@filter false
            }
            
            if (currentFilter.showUnreviewedOnly && incident.isReviewed) {
                return@filter false
            }
            
            true
        }
        
        applySorting()
        updateIncidentsList()
    }
    
    private fun setSort(sortType: SortType) {
        currentSort = sortType
        updateSortButtons()
        applySorting()
        updateIncidentsList()
        
        Log.d(TAG, "Sort changed to: $sortType")
    }
    
    private fun updateSortButtons() {
        // Reset all sort buttons
        resetSortButton(btnSortTime)
        resetSortButton(btnSortSeverity)
        resetSortButton(btnSortDevice)
        
        // Highlight active sort button
        when (currentSort) {
            SortType.TIME_DESC, SortType.TIME_ASC -> {
                selectSortButton(btnSortTime)
                btnSortTime.text = if (currentSort == SortType.TIME_DESC) "Czas ↓" else "Czas ↑"
            }
            SortType.SEVERITY_DESC, SortType.SEVERITY_ASC -> {
                selectSortButton(btnSortSeverity)
                btnSortSeverity.text = if (currentSort == SortType.SEVERITY_DESC) "Waga ↓" else "Waga ↑"
            }
            SortType.DEVICE_NAME -> {
                selectSortButton(btnSortDevice)
            }
        }
    }
    
    private fun resetSortButton(button: Button) {
        button.setBackgroundResource(R.drawable.filter_button_unselected)
        button.setTextColor(resources.getColor(android.R.color.darker_gray, null))
    }
    
    private fun selectSortButton(button: Button) {
        button.setBackgroundResource(R.drawable.filter_button_selected)
        button.setTextColor(resources.getColor(android.R.color.white, null))
    }
    
    private fun applySorting() {
        filteredIncidents = when (currentSort) {
            SortType.TIME_DESC -> filteredIncidents.sortedByDescending { it.timestamp }
            SortType.TIME_ASC -> filteredIncidents.sortedBy { it.timestamp }
            SortType.SEVERITY_DESC -> filteredIncidents.sortedByDescending { it.severity.weight }
            SortType.SEVERITY_ASC -> filteredIncidents.sortedBy { it.severity.weight }
            SortType.DEVICE_NAME -> filteredIncidents.sortedBy { it.deviceName }
        }
    }
    
    private fun updateIncidentsList() {
        incidentsAdapter.updateIncidents(filteredIncidents)
        
        // Update count
        tvIncidentCount.text = "${filteredIncidents.size} incydentów"
        
        // Show/hide empty state
        if (filteredIncidents.isEmpty()) {
            rvIncidents.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            rvIncidents.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }
    
    private fun handleIncidentAction(incident: Incident, action: String) {
        when (action) {
            "view_details" -> showIncidentDetails(incident)
            "mark_reviewed" -> markIncidentAsReviewed(incident)
            "show_text" -> toggleTextPreview(incident)
            "export" -> exportIncident(incident)
        }
    }
    
    private fun showIncidentDetails(incident: Incident) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        
        val details = buildString {
            appendLine("Incydent: ${incident.id}")
            appendLine("Urządzenie: ${incident.deviceName}")
            appendLine("Czas: ${dateFormat.format(Date(incident.timestamp))}")
            appendLine("Typ wykrycia: ${incident.description}")
            appendLine("Poziom: ${incident.severity.displayName}")
            appendLine("Pewność: ${(incident.confidence * 100).toInt()}%")
            appendLine("Słowa kluczowe: ${incident.detectedKeywords.joinToString(", ")}")
            appendLine("Status: ${if (incident.isReviewed) "Przejrzane" else "Nieprzejrzane"}")
            
            if (incident.extractedText != null) {
                appendLine("\nWykryty tekst:")
                appendLine(incident.extractedText)
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Szczegóły incydentu")
            .setMessage(details)
            .setPositiveButton("Zamknij", null)
            .setNeutralButton("Oznacz jako przejrzane") { _, _ ->
                markIncidentAsReviewed(incident)
            }
            .show()
    }
    
    private fun markIncidentAsReviewed(incident: Incident) {
        lifecycleScope.launch {
            try {
                incidentManager.markIncidentAsReviewed(incident.id)
                loadIncidents() // Refresh to show updated status
                showSuccess("Incydent oznaczony jako przejrzany")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error marking incident as reviewed", e)
                showError("Błąd podczas oznaczania incydentu: ${e.message}")
            }
        }
    }
    
    private fun toggleTextPreview(incident: Incident) {
        // This would be handled by the adapter
        Log.d(TAG, "Toggle text preview for incident: ${incident.id}")
    }
    
    private fun exportIncident(incident: Incident) {
        // TODO: Implement export functionality
        showInfo("Eksport incydentu - funkcja w przygotowaniu")
    }
    
    private fun startAutoRefresh() {
        lifecycleScope.launch {
            while (true) {
                delay(60000) // 1 minute
                if (!isFinishing && !isDestroyed) {
                    updateStatistics()
                }
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        layoutLoading.visibility = if (show) View.VISIBLE else View.GONE
        rvIncidents.visibility = if (show) View.GONE else View.VISIBLE
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
        incidentManager.shutdown()
        pairedDevicesManager.shutdown()
        Log.d(TAG, "IncidentsActivity destroyed")
    }
}
