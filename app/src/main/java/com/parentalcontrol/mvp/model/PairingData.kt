package com.parentalcontrol.mvp.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Model danych do parowania urządzeń
 */
data class PairingData(
    @SerializedName("device_id")
    val deviceId: String = UUID.randomUUID().toString(),
    
    @SerializedName("device_name")
    val deviceName: String,
    
    @SerializedName("device_type")
    val deviceType: DeviceType,
    
    @SerializedName("ip_address")
    val ipAddress: String,
    
    @SerializedName("port")
    val port: Int,
    
    @SerializedName("security_key")
    val securityKey: String,
    
    @SerializedName("pairing_code")
    val pairingCode: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("family_id")
    val familyId: String = UUID.randomUUID().toString(),
    
    @SerializedName("wifi_ssid")
    val wifiSSID: String? = null
)

enum class DeviceType {
    @SerializedName("parent")
    PARENT,
    
    @SerializedName("child")
    CHILD
}

/**
 * Zaproszenie do rodziny do szybkiego współdzielenia (multi-rodzic/multi-dziecko)
 */
data class FamilyInvite(
    @SerializedName("type")
    val type: String = "family_invite",
    
    @SerializedName("family_id")
    val familyId: String,
    
    @SerializedName("created_by_name")
    val createdByName: String,
    
    @SerializedName("created_by_type")
    val createdByType: DeviceType,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Status parowania
 */
data class PairingStatus(
    val isPaired: Boolean = false,
    val pairedDeviceId: String? = null,
    val pairedDeviceName: String? = null,
    val pairedDeviceType: DeviceType? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val lastHeartbeat: Long = 0L
)

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    ERROR
}

/**
 * Model dla komunikatów między urządzeniami
 */
data class RemoteMessage(
    @SerializedName("message_id")
    val messageId: String = UUID.randomUUID().toString(),
    
    @SerializedName("sender_id")
    val senderId: String,
    
    @SerializedName("recipient_id")
    val recipientId: String,
    
    @SerializedName("message_type")
    val messageType: MessageType,
    
    @SerializedName("payload")
    val payload: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("requires_ack")
    val requiresAck: Boolean = false
)

enum class MessageType {
    @SerializedName("heartbeat")
    HEARTBEAT,
    
    @SerializedName("log_data")
    LOG_DATA,
    
    @SerializedName("alert")
    ALERT,
    
    @SerializedName("config_update")
    CONFIG_UPDATE,
    
    @SerializedName("pairing_request")
    PAIRING_REQUEST,
    
    @SerializedName("pairing_response")
    PAIRING_RESPONSE,
    
    @SerializedName("acknowledgment")
    ACKNOWLEDGMENT
}

/**
 * Model dla danych logów przesyłanych zdalnie
 */
data class RemoteLogData(
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("current_app")
    val currentApp: AppInfo,
    
    @SerializedName("activity_logs")
    val activityLogs: List<ActivityLog>,
    
    @SerializedName("alerts")
    val alerts: List<AlertData>,
    
    @SerializedName("system_info")
    val systemInfo: SystemInfo,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class AppInfo(
    @SerializedName("package_name")
    val packageName: String,
    
    @SerializedName("app_name")
    val appName: String,
    
    @SerializedName("category")
    val category: String? = null,
    
    @SerializedName("usage_time")
    val usageTime: Long = 0L,
    
    @SerializedName("last_used")
    val lastUsed: Long = System.currentTimeMillis()
)

data class ActivityLog(
    @SerializedName("app_info")
    val appInfo: AppInfo,
    
    @SerializedName("duration")
    val duration: Long,
    
    @SerializedName("start_time")
    val startTime: Long,
    
    @SerializedName("end_time")
    val endTime: Long
)

data class AlertData(
    @SerializedName("alert_id")
    val alertId: String = UUID.randomUUID().toString(),
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("confidence")
    val confidence: Float,
    
    @SerializedName("app_info")
    val appInfo: AppInfo,
    
    @SerializedName("extracted_text")
    val extractedText: String? = null,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("severity")
    val severity: AlertSeverity = AlertSeverity.MEDIUM
)

enum class AlertSeverity {
    @SerializedName("low")
    LOW,
    
    @SerializedName("medium")
    MEDIUM,
    
    @SerializedName("high")
    HIGH,
    
    @SerializedName("critical")
    CRITICAL
}

data class SystemInfo(
    @SerializedName("battery_level")
    val batteryLevel: Int,
    
    @SerializedName("wifi_connected")
    val wifiConnected: Boolean,
    
    @SerializedName("storage_available")
    val storageAvailable: Long,
    
    @SerializedName("accessibility_enabled")
    val accessibilityEnabled: Boolean,
    
    @SerializedName("app_version")
    val appVersion: String
)
