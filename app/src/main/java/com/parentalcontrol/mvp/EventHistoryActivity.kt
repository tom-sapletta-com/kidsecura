package com.parentalcontrol.mvp

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        supportActionBar?.title = getString(R.string.history_title)
        
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
            .setTitle(R.string.event_details_title)
            .setMessage(message)
            .setPositiveButton(R.string.mark_as_reviewed) { _, _ ->
                markAsReviewed(event.id)
            }
            .setNeutralButton(R.string.mark_as_false_positive) { _, _ ->
                markAsFalsePositive(event.id)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }
    
    private fun markAsReviewed(eventId: Long) {
        lifecycleScope.launch {
            database.eventDao().markAsReviewed(eventId)
            Toast.makeText(this@EventHistoryActivity, R.string.reviewed_toast, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun markAsFalsePositive(eventId: Long) {
        lifecycleScope.launch {
            database.eventDao().markAsFalsePositive(eventId)
            Toast.makeText(this@EventHistoryActivity, R.string.false_positive_toast, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
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
        holder.bind(events[position])
    }
    
    override fun getItemCount(): Int = events.size
    
    inner class EventViewHolder(
        private val binding: ItemEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(event: MonitoringEvent) {
            val context = binding.root.context
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            
            binding.apply {
                tvType.text = event.type
                tvTimestamp.text = dateFormat.format(Date(event.timestamp))
                tvDescription.text = event.description
                tvConfidence.text = "${(event.confidence * 100).toInt()}%"
                
                // Kolor ikony i pewności w zależności od poziomu zagrożenia
                val color = when {
                    event.confidence >= 0.8f -> R.color.status_danger
                    event.confidence >= 0.5f -> R.color.status_warning
                    else -> R.color.status_safe
                }
                ivIcon.setColorFilter(ContextCompat.getColor(context, color))
                tvConfidence.setTextColor(ContextCompat.getColor(context, color))
                
                // Status chip
                when {
                    event.isFalsePositive -> {
                        chipStatus.text = "Fałszywy alarm"
                        chipStatus.setChipBackgroundColorResource(R.color.status_safe)
                    }
                    event.isReviewed -> {
                        chipStatus.text = "Przejrzane"
                        chipStatus.setChipBackgroundColorResource(R.color.md_theme_light_secondary)
                    }
                    else -> {
                        chipStatus.text = "Do przejrzenia"
                        chipStatus.setChipBackgroundColorResource(R.color.status_warning)
                    }
                }
                
                root.setOnClickListener {
                    onItemClick(event)
                }
            }
        }
    }
}
