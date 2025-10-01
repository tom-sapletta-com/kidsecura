package com.parentalcontrol.mvp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.parentalcontrol.mvp.adapter.LogAdapter
import com.parentalcontrol.mvp.databinding.ActivityLogViewerBinding
import com.parentalcontrol.mvp.utils.LogEntry
import com.parentalcontrol.mvp.utils.LogFileReader
import com.parentalcontrol.mvp.utils.LogType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LogViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLogViewerBinding
    private lateinit var logAdapter: LogAdapter
    private lateinit var logFileReader: LogFileReader
    
    private var allLogs: List<LogEntry> = emptyList()
    private var filteredLogs: List<LogEntry> = emptyList()
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    // Request permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadLogs()
        } else {
            showPermissionError()
        }
    }
    
    // Request manage external storage permission
    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { 
        if (canReadExternalStorage()) {
            loadLogs()
        } else {
            showPermissionError()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize components
        logFileReader = LogFileReader(this)
        setupRecyclerView()
        setupFilters()
        setupClickListeners()
        
        // Check permissions and load logs
        checkPermissionsAndLoadLogs()
    }
    
    private fun setupRecyclerView() {
        logAdapter = LogAdapter(this)
        binding.recyclerViewLogs.apply {
            adapter = logAdapter
            layoutManager = LinearLayoutManager(this@LogViewerActivity)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    // Optional: Add scroll behavior if needed
                }
            })
        }
    }
    
    private fun setupFilters() {
        // Setup date filter spinner
        val dateOptions = arrayOf(
            getString(R.string.date_all),
            getString(R.string.date_today),
            getString(R.string.date_yesterday),
            getString(R.string.date_week),
            getString(R.string.date_month)
        )
        
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dateOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDateFilter.adapter = spinnerAdapter
        
        // Setup date filter listener
        binding.spinnerDateFilter.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilters()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        
        // Setup chip filter listeners
        binding.chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            applyFilters()
        }
    }
    
    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            loadLogs()
        }
    }
    
    private fun checkPermissionsAndLoadLogs() {
        when {
            canReadExternalStorage() -> {
                loadLogs()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ - request MANAGE_EXTERNAL_STORAGE
                if (!Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    requestManageStorageLauncher.launch(intent)
                } else {
                    loadLogs()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6-10 - request READ_EXTERNAL_STORAGE
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                loadLogs()
            }
        }
    }
    
    private fun canReadExternalStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun loadLogs() {
        binding.tvLogStats.text = getString(R.string.loading_logs)
        binding.tvLastUpdate.text = ""
        
        lifecycleScope.launch {
            try {
                if (!logFileReader.canReadLogs()) {
                    showEmptyState()
                    return@launch
                }
                
                val logs = logFileReader.readAllLogs()
                allLogs = logs
                
                if (logs.isEmpty()) {
                    showEmptyState()
                } else {
                    applyFilters()
                    updateLastUpdateTime()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@LogViewerActivity, 
                    getString(R.string.error_logs_not_accessible), 
                    Toast.LENGTH_LONG).show()
                showEmptyState()
            }
        }
    }
    
    private fun applyFilters() {
        var filtered = allLogs
        
        // Apply type filter
        val selectedChipId = binding.chipGroupFilter.checkedChipId
        val filterType = when (selectedChipId) {
            R.id.chipAlerts -> LogType.ALERT
            R.id.chipApps -> LogType.APP_ACTIVITY
            R.id.chipService -> LogType.SERVICE
            else -> null // All types
        }
        
        if (filterType != null) {
            filtered = logFileReader.filterLogs(filtered, filterType)
        }
        
        // Apply date filter
        val dateFilterPosition = binding.spinnerDateFilter.selectedItemPosition
        val daysBack = when (dateFilterPosition) {
            1 -> 0  // Today
            2 -> 1  // Yesterday
            3 -> 7  // Week
            4 -> 30 // Month
            else -> -1 // All
        }
        
        if (daysBack >= 0) {
            filtered = logFileReader.filterLogsByDate(filtered, daysBack)
        }
        
        filteredLogs = filtered
        updateLogsList()
    }
    
    private fun updateLogsList() {
        logAdapter.submitList(filteredLogs)
        
        // Update stats
        val statsText = getString(R.string.logs_count, filteredLogs.size, allLogs.size)
        binding.tvLogStats.text = statsText
        
        // Show/hide empty state
        if (filteredLogs.isEmpty()) {
            binding.recyclerViewLogs.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            binding.recyclerViewLogs.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = View.GONE
        }
    }
    
    private fun showEmptyState() {
        allLogs = emptyList()
        filteredLogs = emptyList()
        binding.recyclerViewLogs.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvLogStats.text = getString(R.string.no_logs_found)
    }
    
    private fun updateLastUpdateTime() {
        val currentTime = timeFormat.format(Date())
        binding.tvLastUpdate.text = getString(R.string.last_update, currentTime)
    }
    
    private fun showPermissionError() {
        Toast.makeText(this, getString(R.string.error_no_storage_permission), Toast.LENGTH_LONG).show()
        showEmptyState()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh logs when returning to activity
        if (canReadExternalStorage() && allLogs.isNotEmpty()) {
            loadLogs()
        }
    }
}
