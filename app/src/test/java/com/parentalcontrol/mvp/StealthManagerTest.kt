package com.parentalcontrol.mvp

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.SystemLogger
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for StealthManager
 * 
 * CRITICAL TESTS:
 * - Verifies that anti-tampering protection does NOT disable MainActivity in DEBUG builds
 * - Ensures component states remain correct during development
 * - Prevents the "Activity does not exist" error
 */
@RunWith(MockitoJUnitRunner::class)
class StealthManagerTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockPackageManager: PackageManager
    
    @Mock
    private lateinit var mockPrefsManager: PreferencesManager
    
    @Mock
    private lateinit var mockSystemLogger: SystemLogger
    
    private lateinit var stealthManager: StealthManager

    @Before
    fun setup() {
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockContext.packageName).thenReturn("com.parentalcontrol.mvp")
        
        stealthManager = StealthManager(mockContext, mockPrefsManager, mockSystemLogger)
    }

    /**
     * CRITICAL TEST: Ensures anti-tampering does not disable MainActivity in DEBUG builds
     * This test prevents the "Activity does not exist" error during development
     */
    @Test
    fun activateAntiTamperingProtection_inDebugBuild_shouldNotDisableMainActivity() {
        // Given: DEBUG build (BuildConfig.DEBUG = true in tests)
        // When: Anti-tampering protection is activated
        stealthManager.activateAntiTamperingProtection()
        
        // Then: setComponentEnabledSetting should NEVER be called for MainActivity
        verify(mockPackageManager, never()).setComponentEnabledSetting(
            any(),
            eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
            any()
        )
    }

    /**
     * CRITICAL TEST: Ensures MainActivity component is never disabled in DEBUG
     */
    @Test
    fun activateAntiTamperingProtection_inDebugBuild_shouldNotCallPackageManager() {
        // Given: DEBUG build
        // When: Anti-tampering is activated
        stealthManager.activateAntiTamperingProtection()
        
        // Then: PackageManager should never be touched
        verify(mockPackageManager, never()).setComponentEnabledSetting(any(), anyInt(), anyInt())
    }

    @Test
    fun isStealthModeEnabled_whenDisabled_shouldReturnFalse() {
        // Given
        whenever(mockPrefsManager.isStealthModeEnabled()).thenReturn(false)
        
        // When
        val result = stealthManager.isStealthModeEnabled()
        
        // Then
        assertFalse(result)
    }

    @Test
    fun isStealthModeEnabled_whenEnabled_shouldReturnTrue() {
        // Given
        whenever(mockPrefsManager.isStealthModeEnabled()).thenReturn(true)
        
        // When
        val result = stealthManager.isStealthModeEnabled()
        
        // Then
        assertTrue(result)
    }

    @Test
    fun verifyStealthAccess_withCorrectPin_shouldGrantAccess() {
        // Given
        val correctPin = "1234"
        whenever(mockPrefsManager.getStealthAccessPin()).thenReturn(correctPin)
        
        // When
        val result = stealthManager.verifyStealthAccess(correctPin)
        
        // Then
        assertTrue(result)
        verify(mockPrefsManager).setStealthLastAccess(any())
    }

    @Test
    fun verifyStealthAccess_withIncorrectPin_shouldDenyAccess() {
        // Given
        val correctPin = "1234"
        val wrongPin = "9999"
        whenever(mockPrefsManager.getStealthAccessPin()).thenReturn(correctPin)
        
        // When
        val result = stealthManager.verifyStealthAccess(wrongPin)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun setDisguiseMode_shouldSaveToPreferences() {
        // Given
        val disguiseMode = StealthManager.DISGUISE_CALCULATOR
        
        // When
        stealthManager.setDisguiseMode(disguiseMode)
        
        // Then
        verify(mockPrefsManager).setStealthDisguiseMode(disguiseMode)
    }

    @Test
    fun getDisguiseMode_shouldReturnSavedMode() {
        // Given
        val savedMode = StealthManager.DISGUISE_NOTES
        whenever(mockPrefsManager.getStealthDisguiseMode()).thenReturn(savedMode)
        
        // When
        val result = stealthManager.getDisguiseMode()
        
        // Then
        verify(mockPrefsManager).getStealthDisguiseMode()
    }

    @Test
    fun shouldHideFromAccessibility_whenStealthEnabled_shouldReturnTrue() {
        // Given
        whenever(mockPrefsManager.isStealthModeEnabled()).thenReturn(true)
        
        // When
        val result = stealthManager.shouldHideFromAccessibility()
        
        // Then
        assertTrue(result)
    }

    @Test
    fun shouldHideFromAccessibility_whenStealthDisabled_shouldReturnFalse() {
        // Given
        whenever(mockPrefsManager.isStealthModeEnabled()).thenReturn(false)
        
        // When
        val result = stealthManager.shouldHideFromAccessibility()
        
        // Then
        assertFalse(result)
    }

    @Test
    fun shouldExcludeFromRecents_whenStealthEnabled_shouldReturnTrue() {
        // Given
        whenever(mockPrefsManager.isStealthModeEnabled()).thenReturn(true)
        
        // When
        val result = stealthManager.shouldExcludeFromRecents()
        
        // Then
        assertTrue(result)
    }

    @Test
    fun generateSecretAccessCode_shouldReturnValidCode() {
        // When
        val code = stealthManager.generateSecretAccessCode()
        
        // Then
        assertTrue(code.isNotEmpty())
        assertTrue(code.startsWith("*#*#"))
        assertTrue(code.endsWith("#*#*"))
    }

    @Test
    fun getStealthStatus_shouldReturnCompleteStatus() {
        // Given
        whenever(mockPrefsManager.isStealthModeEnabled()).thenReturn(true)
        whenever(mockPrefsManager.getStealthDisguiseMode()).thenReturn(StealthManager.DISGUISE_CALCULATOR)
        whenever(mockPrefsManager.getStealthLastAccess()).thenReturn(123456789L)
        whenever(mockPrefsManager.getStealthAccessPin()).thenReturn("1234")
        
        // When
        val status = stealthManager.getStealthStatus()
        
        // Then
        assertTrue(status["enabled"] as Boolean)
        assertTrue(status["pin_configured"] as Boolean)
    }

    @Test
    fun logDetectionAttempt_shouldLogWarning() {
        // Given
        val source = "ChildDevice"
        val details = "Attempted to find hidden app"
        
        // When
        stealthManager.logDetectionAttempt(source, details)
        
        // Then
        verify(mockSystemLogger).w(
            eq("StealthManager"),
            contains("STEALTH DETECTION ATTEMPT")
        )
    }
}
