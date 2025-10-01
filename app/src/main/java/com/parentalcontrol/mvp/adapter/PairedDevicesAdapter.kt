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
 * Adapter do wyświetlania listy sparowanych urządzeń
 */
class PairedDevicesAdapter(
    private val onDeviceAction: (PairedDevice, String) -> Unit
) : RecyclerView.Adapter<PairedDevicesAdapter.DeviceViewHolder>() {
    
    private var devices = listOf<PairedDevice>()
    private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    private val relativeDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    
    fun updateDevices(newDevices: List<PairedDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_paired_device, parent, false)
        return DeviceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }
    
    override fun getItemCount(): Int = devices.size
    
    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivDeviceIcon: ImageView = itemView.findViewById(R.id.ivDeviceIcon)
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvDeviceType: TextView = itemView.findViewById(R.id.tvDeviceType)
        private val tvNickname: TextView = itemView.findViewById(R.id.tvNickname)
        private val viewConnectionStatus: View = itemView.findViewById(R.id.viewConnectionStatus)
        private val tvIpAddress: TextView = itemView.findViewById(R.id.tvIpAddress)
        private val tvLastSeen: TextView = itemView.findViewById(R.id.tvLastSeen)
        private val tvPairingDate: TextView = itemView.findViewById(R.id.tvPairingDate)
        private val btnDeviceMenu: ImageButton = itemView.findViewById(R.id.btnDeviceMenu)
        private val layoutConnectionIssue: LinearLayout = itemView.findViewById(R.id.layoutConnectionIssue)
        private val tvConnectionIssue: TextView = itemView.findViewById(R.id.tvConnectionIssue)
        
        fun bind(device: PairedDevice) {
            // Basic device info
            tvDeviceName.text = device.deviceName
            tvIpAddress.text = "${device.ipAddress}:${device.port}"
            
            // Device type
            when (device.deviceType) {
                DeviceType.PARENT -> {
                    tvDeviceType.text = "Rodzic"
                    tvDeviceType.setBackgroundResource(R.drawable.device_type_badge)
                    tvDeviceType.setTextColor(itemView.context.resources.getColor(android.R.color.holo_orange_dark, null))
                    ivDeviceIcon.setImageResource(R.drawable.ic_smartphone)
                    ivDeviceIcon.backgroundTintList = itemView.context.resources.getColorStateList(android.R.color.holo_orange_light, null)
                }
                DeviceType.CHILD -> {
                    tvDeviceType.text = "Dziecko"
                    tvDeviceType.setBackgroundResource(R.drawable.device_type_badge)
                    tvDeviceType.setTextColor(itemView.context.resources.getColor(android.R.color.holo_purple, null))
                    ivDeviceIcon.setImageResource(R.drawable.ic_smartphone)
                    ivDeviceIcon.backgroundTintList = itemView.context.resources.getColorStateList(android.R.color.holo_blue_light, null)
                }
            }
            
            // Nickname
            if (device.nickname != null) {
                tvNickname.text = device.nickname
                tvNickname.visibility = View.VISIBLE
            } else {
                tvNickname.visibility = View.GONE
            }
            
            // Connection status
            when (device.connectionStatus) {
                ConnectionStatus.CONNECTED -> {
                    viewConnectionStatus.backgroundTintList = itemView.context.resources.getColorStateList(android.R.color.holo_green_light, null)
                    layoutConnectionIssue.visibility = View.GONE
                }
                ConnectionStatus.DISCONNECTED -> {
                    viewConnectionStatus.backgroundTintList = itemView.context.resources.getColorStateList(android.R.color.holo_red_light, null)
                    layoutConnectionIssue.visibility = View.VISIBLE
                    tvConnectionIssue.text = "Urządzenie offline - ostatnia aktywność: ${formatRelativeTime(device.lastSeen)}"
                }
                ConnectionStatus.CONNECTING -> {
                    viewConnectionStatus.backgroundTintList = itemView.context.resources.getColorStateList(android.R.color.holo_orange_light, null)
                    layoutConnectionIssue.visibility = View.VISIBLE
                    tvConnectionIssue.text = "Łączenie z urządzeniem..."
                }
                ConnectionStatus.ERROR -> {
                    viewConnectionStatus.backgroundTintList = itemView.context.resources.getColorStateList(android.R.color.holo_red_dark, null)
                    layoutConnectionIssue.visibility = View.VISIBLE
                    tvConnectionIssue.text = "Błąd połączenia z urządzeniem"
                }
            }
            
            // Timestamps
            tvLastSeen.text = formatRelativeTime(device.lastSeen)
            tvPairingDate.text = formatPairingDate(device.pairingDate)
            
            // Menu actions
            btnDeviceMenu.setOnClickListener {
                showDeviceMenu(device, it)
            }
            
            // Long click for quick actions
            itemView.setOnLongClickListener {
                onDeviceAction(device, "view_details")
                true
            }
            
            // Regular click for ping/status check
            itemView.setOnClickListener {
                if (device.connectionStatus == ConnectionStatus.DISCONNECTED) {
                    onDeviceAction(device, "ping")
                } else {
                    onDeviceAction(device, "view_details")
                }
            }
        }
        
        private fun showDeviceMenu(device: PairedDevice, anchorView: View) {
            val popup = PopupMenu(itemView.context, anchorView)
            
            // Add menu items
            popup.menu.add(0, 1, 0, "Sprawdź połączenie")
            popup.menu.add(0, 2, 0, "Edytuj nazwę")
            popup.menu.add(0, 3, 0, "Zobacz szczegóły")
            popup.menu.add(0, 4, 0, "Usuń urządzenie")
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> onDeviceAction(device, "ping")
                    2 -> onDeviceAction(device, "edit_nickname")
                    3 -> onDeviceAction(device, "view_details")
                    4 -> onDeviceAction(device, "remove")
                }
                true
            }
            
            popup.show()
        }
        
        private fun formatRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60 * 1000 -> "Teraz"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} min temu"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} godz. temu"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} dni temu"
                else -> dateFormat.format(Date(timestamp))
            }
        }
        
        private fun formatPairingDate(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 24 * 60 * 60 * 1000 -> "Dzisiaj"
                diff < 2 * 24 * 60 * 60 * 1000 -> "Wczoraj"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} dni temu"
                else -> relativeDateFormat.format(Date(timestamp))
            }
        }
    }
}
