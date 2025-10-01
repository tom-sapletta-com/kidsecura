package com.parentalcontrol.mvp

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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
     * Aktywuje zamaskowaną ikonę aplikacji (np. "System Update")
     */
    private fun enableDisguisedIcon() {
        try {
            // Ta funkcjonalność będzie wymagała dodania activity-alias w AndroidManifest.xml
            systemLogger.d(TAG, "🎭 Disguised icon would be enabled here (requires manifest setup)")
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Failed to enable disguised icon", e)
        }
    }
    
    /**
     * Dezaktywuje zamaskowaną ikonę aplikacji
     */
    private fun disableDisguisedIcon() {
        try {
            systemLogger.d(TAG, "🎭 Disguised icon would be disabled here (requires manifest setup)")
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Failed to disable disguised icon", e)
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
