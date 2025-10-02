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
                    ContextCompat.getColor(context, R.color.danger),
                    ContextCompat.getColor(context, R.color.danger_light)
                )
                LogType.APP_ACTIVITY -> Pair(
                    ContextCompat.getColor(context, R.color.info),
                    ContextCompat.getColor(context, R.color.info_light)
                )
                LogType.SERVICE -> Pair(
                    ContextCompat.getColor(context, R.color.success),
                    ContextCompat.getColor(context, R.color.success_light)
                )
                LogType.UNKNOWN -> Pair(
                    ContextCompat.getColor(context, R.color.text_disabled),
                    ContextCompat.getColor(context, R.color.gray_400)
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
            
            // Set full content - zawsze widoczne
            tvFullContent.text = logEntry.fullContent
            tvFullContent.visibility = View.VISIBLE
            tvFullContent.maxLines = Int.MAX_VALUE
            
            // Ukryj przycisk expand/collapse
            tvExpandCollapse.visibility = View.GONE
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
