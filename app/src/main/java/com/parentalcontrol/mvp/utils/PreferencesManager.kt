package com.parentalcontrol.mvp.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "monitoring_prefs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_CAPTURE_INTERVAL = "capture_interval"
        private const val KEY_CROP_BOTTOM = "crop_bottom"
        private const val KEY_LOCAL_ANALYSIS = "local_analysis"
        private const val KEY_SAVE_SCREENSHOTS = "save_screenshots"
        private const val KEY_PARENT_PHONE = "parent_phone"
        private const val KEY_PARENT_EMAIL = "parent_email"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_DEVICE_PAIRED = "device_paired"
    }
    
    fun getCaptureInterval(): Int = prefs.getInt(KEY_CAPTURE_INTERVAL, 2)
    fun setCaptureInterval(interval: Int) = prefs.edit().putInt(KEY_CAPTURE_INTERVAL, interval).apply()
    
    fun isCropBottomEnabled(): Boolean = prefs.getBoolean(KEY_CROP_BOTTOM, true)
    fun setCropBottomEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_CROP_BOTTOM, enabled).apply()
    
    fun isLocalAnalysisEnabled(): Boolean = prefs.getBoolean(KEY_LOCAL_ANALYSIS, true)
    fun setLocalAnalysisEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_LOCAL_ANALYSIS, enabled).apply()
    
    fun isSaveScreenshotsEnabled(): Boolean = prefs.getBoolean(KEY_SAVE_SCREENSHOTS, false)
    fun setSaveScreenshotsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SAVE_SCREENSHOTS, enabled).apply()
    
    fun getParentPhone(): String? = prefs.getString(KEY_PARENT_PHONE, null)
    fun setParentPhone(phone: String) = prefs.edit().putString(KEY_PARENT_PHONE, phone).apply()
    
    fun getParentEmail(): String? = prefs.getString(KEY_PARENT_EMAIL, null)
    fun setParentEmail(email: String) = prefs.edit().putString(KEY_PARENT_EMAIL, email).apply()
    
    fun getPairingCode(): String? = prefs.getString(KEY_PAIRING_CODE, null)
    fun setPairingCode(code: String) = prefs.edit().putString(KEY_PAIRING_CODE, code).apply()
    
    fun isDevicePaired(): Boolean = prefs.getBoolean(KEY_DEVICE_PAIRED, false)
    fun setDevicePaired(paired: Boolean) = prefs.edit().putBoolean(KEY_DEVICE_PAIRED, paired).apply()
}
