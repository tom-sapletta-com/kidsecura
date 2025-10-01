package com.parentalcontrol.mvp

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.mvp.data.MonitoringDatabase
import com.parentalcontrol.mvp.data.MonitoringEvent
import com.parentalcontrol.mvp.databinding.ActivityEventHistoryBinding
import com.parentalcontrol.mvp.databinding.ItemEventBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EventHistoryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEventHistoryBinding
    private lateinit var database: MonitoringDatabase
    private lateinit var adapter: EventsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        database = MonitoringDatabase.getInstance(this)
        setupRecyclerView()
        loadEvents()
        setupFilters()
    }
    
    private fun setupRecyclerView() {
        adapter = EventsAdapter { event ->
            showEventDetails(event)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@EventHistoryActivity)
            adapter = this@EventHistoryActivity.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }
    
    private fun loadEvents() {
        lifecycleScope.launch {
            database.eventDao().getAllEvents().collectLatest { events ->
                adapter.submitList(events)
                updateEmptyState(events.isEmpty())
            }
        }
    }
    
    private fun setupFilters() {
        binding.apply {
            chipAll.setOnClickListener { loadAllEvents() }
            chipUnreviewed.setOnClickListener { loadUnreviewedEvents() }
            chipToday.setOnClickListener { loadTodayEvents() }
            chipWeek.setOnClickListener { loadWeekEvents() }
        }
    }
    
    private fun loadAllEvents() {
        lifecycleScope.launch {
            database.eventDao().getAllEvents().collectLatest { events ->
                adapter.submitList(events)
                updateEmptyState(events.isEmpty())
            }
        }
    }
    
    private fun loadUnreviewedEvents() {
        lifecycleScope.launch {
            database.eventDao().getUnreviewedEvents().collectLatest { events ->
                adapter.submitList(events)
                updateEmptyState(events.isEmpty())
            }
        }
    }
    
    private fun loadTodayEvents() {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        lifecycleScope.launch {
            database.eventDao().getEventsSince(todayStart).collectLatest { events ->
                adapter.submitList(events)
                updateEmptyState(events.isEmpty())
            }
        }
    }
    
    private fun loadWeekEvents() {
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        
        lifecycleScope.launch {
            database.eventDao().getEventsSince(weekAgo).collectLatest { events ->
                adapter.submitList(events)
                updateEmptyState(events.isEmpty())
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun showEventDetails(event: MonitoringEvent) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        val date = dateFormat.format(Date(event.timestamp))
        
        val message = buildString {
            append("Data: $date\n")
            append("Typ: ${event.type}\n")
            append("Poziom pewności: ${(event.confidence * 100).toInt()}%\n")
            append("Opis: ${event.description}\n\n")
            
            if (!event.ocrText.isNullOrEmpty()) {
                append("Wykryty tekst:\n${event.ocrText}\n\n")
            }
            
            append("Status: ")
            when {
                event.isFalsePositive -> append("Fałszywy alarm")
                event.isReviewed -> append("Przejrzane")
                else -> append("Do przejrzenia")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Szczegóły zdarzenia")
            .setMessage(message)
            .setPositiveButton("Oznacz jako przejrzane") { _, _ ->
                markAsReviewed(event.id)
            }
            .setNeutralButton("Fałszywy alarm") { _, _ ->
                markAsFalsePositive(event.id)
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }
    
    private fun markAsReviewed(eventId: Long) {
        lifecycleScope.launch {
            database.eventDao().markAsReviewed(eventId)
            Toast.makeText(this@EventHistoryActivity, "Oznaczono jako przejrzane", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun markAsFalsePositive(eventId: Long) {
        lifecycleScope.launch {
            database.eventDao().markAsFalsePositive(eventId)
            Toast.makeText(this@EventHistoryActivity, "Oznaczono jako fałszywy alarm", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// EventsAdapter
class EventsAdapter(
    private val onItemClick: (MonitoringEvent) -> Unit
) : RecyclerView.Adapter<EventsAdapter.EventViewHolder>() {
    
    private var events = listOf<MonitoringEvent>()
    
    fun submitList(newEvents: List<MonitoringEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EventViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position], onItemClick)
    }
    
    override fun getItemCount() = events.size
    
    class EventViewHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {
        
        private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        
        fun bind(event: MonitoringEvent, onItemClick: (MonitoringEvent) -> Unit) {
            binding.apply {
                tvTime.text = dateFormat.format(Date(event.timestamp))
                tvType.text = event.type
                tvConfidence.text = "${(event.confidence * 100).toInt()}%"
                tvDescription.text = event.description
                
                // Ustaw kolor tła w zależności od poziomu zagrożenia
                val backgroundColor = when {
                    event.confidence > 0.8f -> 0xFFFFCDD2.toInt() // Czerwony
                    event.confidence > 0.5f -> 0xFFFFF9C4.toInt() // Żółty
                    else -> 0xFFC8E6C9.toInt() // Zielony
                }
                root.setBackgroundColor(backgroundColor)
                
                // Ikony statusu
                ivReviewed.visibility = if (event.isReviewed) View.VISIBLE else View.GONE
                ivFalsePositive.visibility = if (event.isFalsePositive) View.VISIBLE else View.GONE
                
                // Miniatura jeśli dostępna
                if (!event.screenshotPath.isNullOrEmpty()) {
                    val file = File(event.screenshotPath)
                    if (file.exists()) {
                        try {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            ivThumbnail.setImageBitmap(bitmap)
                            ivThumbnail.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            ivThumbnail.visibility = View.GONE
                        }
                    } else {
                        ivThumbnail.visibility = View.GONE
                    }
                } else {
                    ivThumbnail.visibility = View.GONE
                }
                
                root.setOnClickListener { onItemClick(event) }
            }
        }
    }
}