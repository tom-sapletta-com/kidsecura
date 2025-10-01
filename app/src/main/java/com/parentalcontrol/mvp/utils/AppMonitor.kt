package com.parentalcontrol.mvp.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.*

data class ActiveAppInfo(
    val packageName: String,
    val appName: String,
    val timestamp: Long
)

class AppMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "AppMonitor"
    }
    
    private val usageStatsManager: UsageStatsManager? by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }
    
    private val packageManager: PackageManager by lazy {
        context.packageManager
    }
    
    /**
     * Pobiera aktualnie aktywną aplikację
     */
    fun getCurrentApp(): ActiveAppInfo? {
        try {
            val time = System.currentTimeMillis()
            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 10, // ostatnie 10 sekund
                time
            )
            
            if (stats.isNullOrEmpty()) {
                Log.w(TAG, "No usage stats available - permission may not be granted")
                return null
            }
            
            // Znajdź najbardziej niedawno używaną aplikację
            val recentApp = stats.maxByOrNull { it.lastTimeUsed }
            
            if (recentApp != null && recentApp.packageName != context.packageName) {
                val appName = getAppName(recentApp.packageName)
                return ActiveAppInfo(
                    packageName = recentApp.packageName,
                    appName = appName,
                    timestamp = time
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current app", e)
        }
        
        return null
    }
    
    /**
     * Pobiera nazwę aplikacji na podstawie package name
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName // Fallback do package name jeśli nie można pobrać nazwy
        }
    }
    
    /**
     * Sprawdza czy aplikacja ma uprawnienie do użycia Usage Stats
     */
    fun hasUsageStatsPermission(): Boolean {
        try {
            val time = System.currentTimeMillis()
            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000,
                time
            )
            return stats != null && stats.isNotEmpty()
        } catch (e: Exception) {
            return false
        }
    }
}
