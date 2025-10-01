package com.parentalcontrol.mvp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.mvp.R
import com.parentalcontrol.mvp.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter do wyświetlania listy incydentów bezpieczeństwa
 */
class IncidentsAdapter(
    private val onIncidentAction: (Incident, String) -> Unit
) : RecyclerView.Adapter<IncidentsAdapter.IncidentViewHolder>() {
    
    private var incidents = listOf<Incident>()
    private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    private val expandedItems = mutableSetOf<String>()
    
    fun updateIncidents(newIncidents: List<Incident>) {
        incidents = newIncidents
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncidentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_incident, parent, false)
        return IncidentViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: IncidentViewHolder, position: Int) {
        holder.bind(incidents[position])
    }
    
    override fun getItemCount(): Int = incidents.size
    
    inner class IncidentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewSeverityIndicator: View = itemView.findViewById(R.id.viewSeverityIndicator)
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvSeverityLevel: TextView = itemView.findViewById(R.id.tvSeverityLevel)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val btnIncidentMenu: ImageButton = itemView.findViewById(R.id.btnIncidentMenu)
        private val tvDetectionType: TextView = itemView.findViewById(R.id.tvDetectionType)
        private val tvConfidence: TextView = itemView.findViewById(R.id.tvConfidence)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val layoutKeywords: LinearLayout = itemView.findViewById(R.id.layoutKeywords)
        private val layoutTextPreview: LinearLayout = itemView.findViewById(R.id.layoutTextPreview)
        private val tvExtractedText: TextView = itemView.findViewById(R.id.tvExtractedText)
        private val ivReviewStatus: ImageView = itemView.findViewById(R.id.ivReviewStatus)
        private val tvReviewStatus: TextView = itemView.findViewById(R.id.tvReviewStatus)
        private val btnViewDetails: Button = itemView.findViewById(R.id.btnViewDetails)
        private val btnMarkReviewed: Button = itemView.findViewById(R.id.btnMarkReviewed)
        
        fun bind(incident: Incident) {
            // Device name
            tvDeviceName.text = incident.deviceName
            
            // Severity styling
            configureSeverityDisplay(incident.severity)
            
            // Timestamp
            tvTimestamp.text = formatRelativeTime(incident.timestamp)
            
            // Detection type and confidence
            tvDetectionType.text = incident.description
            tvConfidence.text = "${(incident.confidence * 100).toInt()}%"
            
            // Description (truncated)
            tvDescription.text = if (incident.description.length > 100) {
                incident.description.take(100) + "..."
            } else {
                incident.description
            }
            
            // Keywords chips
            setupKeywordsChips(incident.detectedKeywords)
            
            // Extracted text preview
            setupTextPreview(incident)
            
            // Review status
            setupReviewStatus(incident)
            
            // Action buttons
            setupActionButtons(incident)
            
            // Menu button
            btnIncidentMenu.setOnClickListener {
                showIncidentMenu(incident, it)
            }
            
            // Click handlers
            itemView.setOnClickListener {
                if (incident.extractedText != null) {
                    toggleTextPreview(incident)
                } else {
                    onIncidentAction(incident, "view_details")
                }
            }
            
            itemView.setOnLongClickListener {
                onIncidentAction(incident, "view_details")
                true
            }
        }
        
        private fun configureSeverityDisplay(severity: IncidentSeverity) {
            // Severity level text and background
            tvSeverityLevel.text = severity.displayName.uppercase()
            
            when (severity) {
                IncidentSeverity.CRITICAL -> {
                    viewSeverityIndicator.setBackgroundResource(R.color.severity_critical)
                    tvSeverityLevel.setBackgroundResource(R.drawable.severity_critical_badge)
                }
                IncidentSeverity.HIGH -> {
                    viewSeverityIndicator.setBackgroundResource(R.color.severity_high)
                    tvSeverityLevel.setBackgroundResource(R.drawable.severity_high_badge)
                }
                IncidentSeverity.MEDIUM -> {
                    viewSeverityIndicator.setBackgroundResource(R.color.severity_medium)
                    tvSeverityLevel.setBackgroundResource(R.drawable.severity_medium_badge)
                }
                IncidentSeverity.LOW -> {
                    viewSeverityIndicator.setBackgroundResource(R.color.severity_low)
                    tvSeverityLevel.setBackgroundResource(R.drawable.severity_low_badge)
                }
            }
        }
        
        private fun setupKeywordsChips(keywords: List<String>) {
            layoutKeywords.removeAllViews()
            
            keywords.take(4).forEach { keyword -> // Show max 4 keywords
                val chip = TextView(itemView.context).apply {
                    text = keyword
                    textSize = 10f
                    setTextColor(itemView.context.resources.getColor(android.R.color.darker_gray, null))
                    setBackgroundResource(R.drawable.keyword_chip)
                    setPadding(16, 8, 16, 8)
                    
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, 0, 8, 0)
                    layoutParams = params
                }
                layoutKeywords.addView(chip)
            }
            
            // Add "more" indicator if there are additional keywords
            if (keywords.size > 4) {
                val moreChip = TextView(itemView.context).apply {
                    text = "+${keywords.size - 4}"
                    textSize = 10f
                    setTextColor(itemView.context.resources.getColor(android.R.color.darker_gray, null))
                    setBackgroundResource(R.drawable.keyword_chip)
                    setPadding(16, 8, 16, 8)
                    
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams = params
                }
                layoutKeywords.addView(moreChip)
            }
        }
        
        private fun setupTextPreview(incident: Incident) {
            if (incident.extractedText != null) {
                tvExtractedText.text = incident.extractedText
                
                // Check if this incident is expanded
                val isExpanded = expandedItems.contains(incident.id)
                layoutTextPreview.visibility = if (isExpanded) View.VISIBLE else View.GONE
            } else {
                layoutTextPreview.visibility = View.GONE
            }
        }
        
        private fun setupReviewStatus(incident: Incident) {
            if (incident.isReviewed) {
                ivReviewStatus.setImageResource(R.drawable.ic_visibility)
                ivReviewStatus.setColorFilter(itemView.context.resources.getColor(android.R.color.holo_green_light, null))
                tvReviewStatus.text = "Przejrzane"
                tvReviewStatus.setTextColor(itemView.context.resources.getColor(android.R.color.holo_green_light, null))
                btnMarkReviewed.visibility = View.GONE
            } else {
                ivReviewStatus.setImageResource(R.drawable.ic_visibility_off)
                ivReviewStatus.setColorFilter(itemView.context.resources.getColor(android.R.color.holo_orange_light, null))
                tvReviewStatus.text = "Nieprzejrzane"
                tvReviewStatus.setTextColor(itemView.context.resources.getColor(android.R.color.holo_orange_light, null))
                btnMarkReviewed.visibility = View.VISIBLE
            }
        }
        
        private fun setupActionButtons(incident: Incident) {
            btnViewDetails.setOnClickListener {
                onIncidentAction(incident, "view_details")
            }
            
            btnMarkReviewed.setOnClickListener {
                onIncidentAction(incident, "mark_reviewed")
            }
        }
        
        private fun showIncidentMenu(incident: Incident, anchorView: View) {
            val popup = PopupMenu(itemView.context, anchorView)
            
            // Add menu items based on incident state
            popup.menu.add(0, 1, 0, "Zobacz szczegóły")
            if (incident.extractedText != null) {
                popup.menu.add(0, 2, 0, if (expandedItems.contains(incident.id)) "Ukryj tekst" else "Pokaż tekst")
            }
            if (!incident.isReviewed) {
                popup.menu.add(0, 3, 0, "Oznacz jako przejrzane")
            }
            popup.menu.add(0, 4, 0, "Eksportuj")
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> onIncidentAction(incident, "view_details")
                    2 -> toggleTextPreview(incident)
                    3 -> onIncidentAction(incident, "mark_reviewed")
                    4 -> onIncidentAction(incident, "export")
                }
                true
            }
            
            popup.show()
        }
        
        private fun toggleTextPreview(incident: Incident) {
            if (incident.extractedText != null) {
                if (expandedItems.contains(incident.id)) {
                    expandedItems.remove(incident.id)
                    layoutTextPreview.visibility = View.GONE
                } else {
                    expandedItems.add(incident.id)
                    layoutTextPreview.visibility = View.VISIBLE
                }
                onIncidentAction(incident, "show_text")
            }
        }
        
        private fun formatRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60 * 1000 -> "Teraz"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} min temu"
                diff < 6 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} godz. temu"
                diff < 24 * 60 * 60 * 1000 -> {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = timestamp
                    "Dziś o ${String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))}"
                }
                diff < 2 * 24 * 60 * 60 * 1000 -> {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = timestamp
                    "Wczoraj o ${String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))}"
                }
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} dni temu"
                else -> dateFormat.format(Date(timestamp))
            }
        }
    }
}
