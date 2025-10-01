package com.parentalcontrol.mvp.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.mvp.R
import com.parentalcontrol.mvp.utils.LogEntry
import com.parentalcontrol.mvp.utils.LogType
import java.text.SimpleDateFormat
import java.util.*

class LogAdapter(private val context: Context) : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {
    
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val logEntry = getItem(position)
        holder.bind(logEntry)
    }
    
    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLogType: TextView = itemView.findViewById(R.id.tvLogType)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvFullContent: TextView = itemView.findViewById(R.id.tvFullContent)
        private val tvExpandCollapse: TextView = itemView.findViewById(R.id.tvExpandCollapse)
        private val viewTypeIndicator: View = itemView.findViewById(R.id.viewTypeIndicator)
        
        private var isExpanded = false
        
        fun bind(logEntry: LogEntry) {
            // Set log type
            val typeText = when (logEntry.type) {
                LogType.SERVICE -> context.getString(R.string.log_type_service)
                LogType.APP_ACTIVITY -> context.getString(R.string.log_type_app_activity)
                LogType.ALERT -> context.getString(R.string.log_type_alert)
                LogType.UNKNOWN -> context.getString(R.string.log_type_unknown)
            }
            tvLogType.text = typeText
            
            // Set colors based on type
            val (indicatorColor, typeColor) = when (logEntry.type) {
                LogType.ALERT -> Pair(
                    ContextCompat.getColor(context, android.R.color.holo_red_dark),
                    ContextCompat.getColor(context, android.R.color.holo_red_dark)
                )
                LogType.APP_ACTIVITY -> Pair(
                    ContextCompat.getColor(context, android.R.color.holo_blue_dark),
                    ContextCompat.getColor(context, android.R.color.holo_blue_dark)
                )
                LogType.SERVICE -> Pair(
                    ContextCompat.getColor(context, android.R.color.holo_green_dark),
                    ContextCompat.getColor(context, android.R.color.holo_green_dark)
                )
                LogType.UNKNOWN -> Pair(
                    ContextCompat.getColor(context, android.R.color.darker_gray),
                    ContextCompat.getColor(context, android.R.color.darker_gray)
                )
            }
            
            viewTypeIndicator.setBackgroundColor(indicatorColor)
            tvLogType.setTextColor(typeColor)
            
            // Set timestamp
            val timestamp = parseTimestamp(logEntry.timestamp)
            tvTimestamp.text = if (timestamp != null) {
                timeFormat.format(timestamp)
            } else {
                logEntry.timestamp
            }
            
            // Set message
            tvMessage.text = logEntry.message
            
            // Set full content
            tvFullContent.text = logEntry.fullContent
            
            // Initialize collapsed state
            isExpanded = false
            tvFullContent.visibility = View.GONE
            tvExpandCollapse.text = context.getString(R.string.show_details)
            
            // Set expand/collapse click listener
            tvExpandCollapse.setOnClickListener {
                isExpanded = !isExpanded
                if (isExpanded) {
                    tvFullContent.visibility = View.VISIBLE
                    tvFullContent.maxLines = Int.MAX_VALUE
                    tvExpandCollapse.text = context.getString(R.string.hide_details)
                } else {
                    tvFullContent.visibility = View.GONE
                    tvExpandCollapse.text = context.getString(R.string.show_details)
                }
            }
            
            // Show/hide expand button if content is short
            val shouldShowExpandButton = logEntry.fullContent.length > 100 || 
                                       logEntry.fullContent.lines().size > 2
            tvExpandCollapse.visibility = if (shouldShowExpandButton) View.VISIBLE else View.GONE
        }
        
        private fun parseTimestamp(timestamp: String): Date? {
            return try {
                dateFormat.parse(timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.timestamp == newItem.timestamp && 
                   oldItem.fullContent == newItem.fullContent
        }
        
        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
