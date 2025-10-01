package com.parentalcontrol.mvp

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.SystemLogger
import java.util.*

/**
 * StealthManager - zarządza trybem ukrytym aplikacji
 * 
 * Funkcje:
 * - Ukrycie/pokazanie ikony aplikacji
 * - Zmiana tożsamości aplikacji (disguised identity)  
 * - Zarządzanie stealth access control
 * - Monitoring prób wykrycia aplikacji przez dzieci
 */
class StealthManager(
    private val context: Context,
    private val prefsManager: PreferencesManager,
    private val systemLogger: SystemLogger
) {
    companion object {
        private const val TAG = "StealthManager"
        
        // Klucze preferencji
        private const val PREF_STEALTH_MODE_ENABLED = "stealth_mode_enabled"
        private const val PREF_STEALTH_ACCESS_PIN = "stealth_access_pin"
        private const val PREF_STEALTH_DISGUISE_MODE = "stealth_disguise_mode"
        private const val PREF_STEALTH_LAST_ACCESS = "stealth_last_access"
        
        // Tryby maskowania aplikacji
        const val DISGUISE_SYSTEM_UPDATE = "system_update"
        const val DISGUISE_ANDROID_SERVICE = "android_service"  
        const val DISGUISE_CALCULATOR = "calculator"
        const val DISGUISE_NOTES = "notes"
        
        // Komponenty aplikacji do ukrywania
        private const val MAIN_ACTIVITY_ALIAS = "com.parentalcontrol.mvp.MainActivityAlias"
        private const val STEALTH_ACTIVITY_ALIAS = "com.parentalcontrol.mvp.StealthActivityAlias"
    }
    
    /**
     * Sprawdza czy tryb stealth jest aktywny
     */
    fun isStealthModeEnabled(): Boolean {
        return prefsManager.isStealthModeEnabled()
    }
    
    /**
     * Aktywuje tryb stealth - ukrywa ikonę aplikacji
     */
    fun enableStealthMode(pin: String? = null): Boolean {
        return try {
            systemLogger.i(TAG, "🕵️ Enabling Stealth Mode...")
            
            // Zapisz PIN dostępu jeśli podany
            pin?.let { 
                prefsManager.setStealthAccessPin(it)
                systemLogger.d(TAG, "🔐 Stealth access PIN configured")
            }
            
            // Ukryj główną ikonę aplikacji
            hideAppIcon()
            
            // Aktywuj alternatywną, zamaskowaną ikonę
            enableDisguisedIcon()
            
            // Zapisz stan stealth mode
            prefsManager.setStealthModeEnabled(true)
            prefsManager.setStealthLastAccess(System.currentTimeMillis())
            
            systemLogger.i(TAG, "✅ Stealth Mode ENABLED - App is now hidden from children")
            true
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Failed to enable Stealth Mode", e)
            false
        }
    }
    
    /**
     * Dezaktywuje tryb stealth - przywraca widoczną ikonę
     */
    fun disableStealthMode(): Boolean {
        return try {
            systemLogger.i(TAG, "🔓 Disabling Stealth Mode...")
            
            // Przywróć główną ikonę aplikacji  
            showAppIcon()
            
            // Ukryj zamaskowaną ikonę
            disableDisguisedIcon()
            
            // Zapisz stan
            prefsManager.setStealthModeEnabled(false)
            
            systemLogger.i(TAG, "✅ Stealth Mode DISABLED - App is now visible")
            true
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Failed to disable Stealth Mode", e)
            false
        }
    }
    
    /**
     * Ukrywa główną ikonę aplikacji w launcherze
     */
    private fun hideAppIcon() {
        try {
            val componentName = ComponentName(context, MainActivity::class.java)
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            systemLogger.d(TAG, "👻 Main app icon HIDDEN from launcher")
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Failed to hide app icon", e)
            throw e
        }
    }
    
    /**
     * Przywraca główną ikonę aplikacji w launcherze
     */
    private fun showAppIcon() {
        try {
            val componentName = ComponentName(context, MainActivity::class.java)
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            systemLogger.d(TAG, "👁️ Main app icon RESTORED to launcher")
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Failed to show app icon", e)
            throw e
        }
    }
    
    /**
     * Aktywuje zamaskowaną ikonę aplikacji według aktualnego trybu disguise
     */
    private fun enableDisguisedIcon() {
        try {
            val disguiseMode = getDisguiseMode()
            val aliasName = getAliasNameForDisguiseMode(disguiseMode)
            
            if (aliasName != null) {
                val componentName = ComponentName(context, aliasName)
                context.packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                systemLogger.d(TAG, "🎭 Enabled disguised icon: $disguiseMode ($aliasName)")
            } else {
                systemLogger.w(TAG, "⚠️ Unknown disguise mode: $disguiseMode")
            }
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Failed to enable disguised icon", e)
            throw e
        }
    }
    
    /**
     * Dezaktywuje wszystkie zamaskowane ikony aplikacji
     */
    private fun disableDisguisedIcon() {
        try {
            val allAliases = listOf(
                "com.parentalcontrol.mvp.SystemUpdateAlias",
                "com.parentalcontrol.mvp.AndroidServiceAlias", 
                "com.parentalcontrol.mvp.CalculatorAlias",
                "com.parentalcontrol.mvp.NotesAlias"
            )
            
            allAliases.forEach { aliasName ->
                try {
                    val componentName = ComponentName(context, aliasName)
                    context.packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    systemLogger.d(TAG, "🎭 Disabled disguised icon: $aliasName")
                } catch (e: Exception) {
                    systemLogger.w(TAG, "⚠️ Failed to disable alias $aliasName: ${e.message}")
                }
            }
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Failed to disable disguised icons", e)
        }
    }
    
    /**
     * Mapuje tryb disguise na nazwę activity-alias
     */
    private fun getAliasNameForDisguiseMode(disguiseMode: String): String? {
        return when (disguiseMode) {
            DISGUISE_SYSTEM_UPDATE -> "com.parentalcontrol.mvp.SystemUpdateAlias"
            DISGUISE_ANDROID_SERVICE -> "com.parentalcontrol.mvp.AndroidServiceAlias"
            DISGUISE_CALCULATOR -> "com.parentalcontrol.mvp.CalculatorAlias"
            DISGUISE_NOTES -> "com.parentalcontrol.mvp.NotesAlias"
            else -> null
        }
    }
    
    /**
     * Usuwa aplikację z listy ostatnio używanych aplikacji (Recent Apps)
     */
    fun removeFromRecentApps() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val appTasks = activityManager.appTasks
                
                appTasks.forEach { appTask ->
                    try {
                        appTask.finishAndRemoveTask()
                        systemLogger.d(TAG, "👻 Removed task from recent apps")
                    } catch (e: Exception) {
                        systemLogger.w(TAG, "⚠️ Failed to remove specific task: ${e.message}")
                    }
                }
                
                systemLogger.d(TAG, "👻 Successfully removed app from recent apps list")
            } else {
                systemLogger.w(TAG, "⚠️ Recent apps removal not supported on API < 21")
            }
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Failed to remove from recent apps", e)
        }
    }
    
    /**
     * Wykrywa próby inżynierii wstecznej lub manipulacji aplikacji
     */
    fun detectTamperingAttempts(): Boolean {
        try {
            var tamperingDetected = false
            val detectionResults = mutableListOf<String>()
            
            // Sprawdź, czy aplikacja jest debugowana
            if (isDebuggingDetected()) {
                tamperingDetected = true
                detectionResults.add("DEBUGGING_DETECTED")
                systemLogger.w(TAG, "🚨 TAMPERING: Debugging detected")
            }
            
            // Sprawdź, czy urządzenie jest zrootowane (podstawowe sprawdzenie)
            if (isRootDetected()) {
                tamperingDetected = true
                detectionResults.add("ROOT_DETECTED") 
                systemLogger.w(TAG, "🚨 TAMPERING: Root access detected")
            }
            
            // Sprawdź integralność pakietu (podstawowe sprawdzenie)
            if (isPackageIntegrityCompromised()) {
                tamperingDetected = true
                detectionResults.add("PACKAGE_INTEGRITY_COMPROMISED")
                systemLogger.w(TAG, "🚨 TAMPERING: Package integrity compromised")
            }
            
            if (tamperingDetected) {
                val detectionInfo = detectionResults.joinToString(", ")
                systemLogger.e(TAG, "🚨 CRITICAL: Tampering attempts detected: $detectionInfo")
                
                // Zapisz informację o wykrytej manipulacji
                prefsManager.setStealthLastAccess(System.currentTimeMillis())
                
                // Opcjonalnie: ukryj aplikację automatycznie
                if (isStealthModeEnabled()) {
                    systemLogger.d(TAG, "🔒 Auto-hiding app due to tampering detection")
                }
            }
            
            return tamperingDetected
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error during tampering detection", e)
            return false
        }
    }
    
    /**
     * Wykrywa, czy aplikacja jest debugowana
     */
    private fun isDebuggingDetected(): Boolean {
        return try {
            // Sprawdź, czy debugger jest podłączony
            android.os.Debug.isDebuggerConnected() || 
            // Sprawdź, czy aplikacja działa w trybie debug
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Podstawowe wykrywanie root (nie zawsze skuteczne, ale lepsze niż nic)
     */
    private fun isRootDetected(): Boolean {
        return try {
            // Sprawdź popularne pliki/aplikacje root
            val rootIndicators = listOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            
            rootIndicators.any { path ->
                try {
                    java.io.File(path).exists()
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Sprawdza integralność pakietu aplikacji (podstawowe sprawdzenie)
     */
    private fun isPackageIntegrityCompromised(): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            
            // Sprawdź, czy aplikacja ma podpis debugowy
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            // Podstawowe sprawdzenie - jeśli nie ma podpisu, to podejrzane
            signatures == null || signatures.isEmpty()
        } catch (e: Exception) {
            systemLogger.w(TAG, "⚠️ Could not verify package integrity: ${e.message}")
            false
        }
    }
    
    /**
     * Funkcja ochrony przed anti-tampering - ukrywa aplikację gdy wykryje manipulację
     */
    fun activateAntiTamperingProtection() {
        try {
            systemLogger.d(TAG, "🛡️ Activating anti-tampering protection")
            
            if (detectTamperingAttempts()) {
                systemLogger.e(TAG, "🚨 CRITICAL: Tampering detected! Activating emergency stealth mode")
                
                // Automatycznie ukryj aplikację
                if (!isStealthModeEnabled()) {
                    enableStealthMode(null) // Bez PIN w trybie emergency
                }
                
                // Usuń z recent apps
                removeFromRecentApps()
                
                systemLogger.d(TAG, "🔒 Emergency stealth mode activated due to tampering")
            } else {
                systemLogger.d(TAG, "✅ No tampering detected - system integrity OK")
            }
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error in anti-tampering protection", e)
        }
    }
    
    /**
     * Weryfikuje PIN dostępu do trybu stealth
     */
    fun verifyStealthAccess(inputPin: String): Boolean {
        val savedPin = prefsManager.getStealthAccessPin()
        
        return if (savedPin == null || savedPin == inputPin) {
            prefsManager.setStealthLastAccess(System.currentTimeMillis())
            systemLogger.d(TAG, "🔓 Stealth access GRANTED")
            true
        } else {
            systemLogger.w(TAG, "🚫 Stealth access DENIED - incorrect PIN")
            false
        }
    }
    
    /**
     * Ustawia tryb maskowania aplikacji
     */
    fun setDisguiseMode(mode: String) {
        prefsManager.setStealthDisguiseMode(mode)
        systemLogger.d(TAG, "🎭 Disguise mode set to: $mode")
    }
    
    /**
     * Pobiera aktualny tryb maskowania
     */
    fun getDisguiseMode(): String {
        return prefsManager.getStealthDisguiseMode()
    }
    
    /**
     * Sprawdza czy aplikacja powinna być ukryta przed accessibility services
     */
    fun shouldHideFromAccessibility(): Boolean {
        return isStealthModeEnabled()
    }
    
    /**
     * Generuje sekretny kod dostępu (gesture pattern)
     */
    fun generateSecretAccessCode(): String {
        val random = Random()
        val codes = listOf("*#*#2563#*#*", "*#*#7483#*#*", "*#*#5437#*#*")
        return codes[random.nextInt(codes.size)]
    }
    
    /**
     * Loguje próbę wykrycia aplikacji przez dziecko
     */
    fun logDetectionAttempt(source: String, details: String) {
        systemLogger.w(TAG, "🚨 STEALTH DETECTION ATTEMPT from $source: $details")
        
        // Tutaj można dodać alert dla rodziców o próbie wykrycia
        // np. poprzez P2P lub notyfikację
    }
    
    /**
     * Sprawdza czy aplikacja powinna być ukryta w recent apps
     */
    fun shouldExcludeFromRecents(): Boolean {
        return isStealthModeEnabled()
    }
    
    /**
     * Pobiera status stealth mode dla dashboardu
     */
    fun getStealthStatus(): Map<String, Any> {
        return mapOf(
            "enabled" to isStealthModeEnabled(),
            "disguise_mode" to getDisguiseMode(),
            "last_access" to prefsManager.getStealthLastAccess(),
            "pin_configured" to (prefsManager.getStealthAccessPin() != null)
        )
    }
}
