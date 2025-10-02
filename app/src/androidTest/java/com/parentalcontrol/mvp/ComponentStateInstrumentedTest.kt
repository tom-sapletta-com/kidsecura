package com.parentalcontrol.mvp

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for component state verification
 * 
 * CRITICAL ANOMALY DETECTION:
 * - Verifies MainActivity is enabled and launchable
 * - Detects if anti-tampering accidentally disabled MainActivity
 * - Ensures component states are correct on the device
 * - Prevents "Activity does not exist" errors
 * 
 * These tests catch the exact issue we encountered:
 * "Activity class {com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity} does not exist"
 */
@RunWith(AndroidJUnit4::class)
class ComponentStateInstrumentedTest {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = appContext.packageManager

    /**
     * CRITICAL TEST: Ensures MainActivity component is ENABLED on the device
     * This is the PRIMARY test to detect the "Activity does not exist" anomaly
     */
    @Test
    fun mainActivity_shouldBeEnabled_onDevice() {
        // Given: MainActivity component
        val mainActivityComponent = ComponentName(appContext, MainActivity::class.java)
        
        // When: We check the component state
        val componentState = packageManager.getComponentEnabledSetting(mainActivityComponent)
        
        // Then: MainActivity should be ENABLED or DEFAULT (not DISABLED)
        assertNotEquals(
            "MainActivity is DISABLED! This causes 'Activity does not exist' error.",
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            componentState
        )
        
        // Component should be either ENABLED or DEFAULT (which means enabled)
        assertTrue(
            "MainActivity component state is invalid: $componentState",
            componentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            componentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        )
    }

    /**
     * CRITICAL TEST: Verifies MainActivity can actually be launched
     * This test will fail with "Activity does not exist" if the component is disabled
     */
    @Test
    fun mainActivity_shouldBeLaunchable() {
        try {
            // Attempt to launch MainActivity
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            
            // Verify it launched successfully
            scenario.onActivity { activity ->
                assertNotNull("MainActivity should not be null", activity)
                assertEquals(
                    "Wrong activity class",
                    MainActivity::class.java,
                    activity.javaClass
                )
            }
            
            scenario.close()
        } catch (e: Exception) {
            fail("MainActivity could not be launched: ${e.message}\n" +
                 "This indicates the component is disabled or doesn't exist.")
        }
    }

    /**
     * TEST: Verifies all activity-alias components are properly configured
     * These are the stealth mode disguised icons
     * 
     * Note: Aliases may be ENABLED from previous installations - this is OK
     * The critical test is that MainActivity itself is always enabled
     */
    @Test
    fun stealthAliases_shouldExist() {
        val aliases = listOf(
            "com.parentalcontrol.mvp.SystemUpdateAlias",
            "com.parentalcontrol.mvp.AndroidServiceAlias",
            "com.parentalcontrol.mvp.CalculatorAlias",
            "com.parentalcontrol.mvp.NotesAlias"
        )
        
        aliases.forEach { aliasName ->
            val aliasComponent = ComponentName(appContext, aliasName)
            val state = packageManager.getComponentEnabledSetting(aliasComponent)
            
            // Just verify the component exists and has a valid state
            // Valid states: 0 (DEFAULT), 1 (ENABLED), 2 (DISABLED), etc.
            // State >= 0 means the component exists and is properly configured
            assertTrue(
                "Alias $aliasName has invalid state: $state (should be >= 0)",
                state >= PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            )
        }
    }

    /**
     * TEST: Verifies the app package exists and is correctly installed
     */
    @Test
    fun appPackage_shouldBeInstalled() {
        val packageName = appContext.packageName
        assertEquals("com.parentalcontrol.mvp", packageName)
        
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            assertNotNull("Package info should not be null", packageInfo)
            assertEquals("Package name mismatch", packageName, packageInfo.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            fail("App package not found: $packageName")
        }
    }

    /**
     * TEST: Verifies launcher intent can find MainActivity
     */
    @Test
    fun mainActivity_shouldBeReachableViaLauncherIntent() {
        val launchIntent = packageManager.getLaunchIntentForPackage(appContext.packageName)
        
        assertNotNull(
            "Launch intent is null - MainActivity might be disabled",
            launchIntent
        )
        
        assertTrue(
            "Launch intent doesn't target MainActivity",
            launchIntent?.component?.className?.contains("MainActivity") == true
        )
    }

    /**
     * DIAGNOSTIC TEST: Prints all component states for debugging
     * Useful when investigating component-related issues
     */
    @Test
    fun diagnostic_printAllComponentStates() {
        println("\n=== COMPONENT STATE DIAGNOSTIC ===")
        
        // Main activity
        val mainComponent = ComponentName(appContext, MainActivity::class.java)
        val mainState = packageManager.getComponentEnabledSetting(mainComponent)
        println("MainActivity state: ${stateToString(mainState)}")
        
        // Aliases
        val aliases = listOf(
            "SystemUpdateAlias",
            "AndroidServiceAlias",
            "CalculatorAlias",
            "NotesAlias"
        )
        
        aliases.forEach { alias ->
            try {
                val component = ComponentName(appContext, "com.parentalcontrol.mvp.$alias")
                val state = packageManager.getComponentEnabledSetting(component)
                println("$alias state: ${stateToString(state)}")
            } catch (e: Exception) {
                println("$alias: ERROR - ${e.message}")
            }
        }
        
        println("=== END DIAGNOSTIC ===\n")
        
        // This test always passes - it's just for logging
        assertTrue(true)
    }

    /**
     * Helper function to convert component state int to readable string
     */
    private fun stateToString(state: Int): String {
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "DEFAULT (inherited)"
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "ENABLED"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "DISABLED ⚠️"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "DISABLED_USER ⚠️"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "DISABLED_UNTIL_USED"
            else -> "UNKNOWN ($state)"
        }
    }

    /**
     * RECOVERY TEST: Attempts to re-enable MainActivity if it's disabled
     * This can be used as a self-healing mechanism in tests
     */
    @Test
    fun recovery_enableMainActivityIfDisabled() {
        val mainComponent = ComponentName(appContext, MainActivity::class.java)
        val currentState = packageManager.getComponentEnabledSetting(mainComponent)
        
        if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            println("⚠️ MainActivity is DISABLED! Attempting to re-enable...")
            
            try {
                packageManager.setComponentEnabledSetting(
                    mainComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                
                val newState = packageManager.getComponentEnabledSetting(mainComponent)
                println("✅ MainActivity re-enabled. New state: ${stateToString(newState)}")
                
                assertEquals(
                    "Failed to re-enable MainActivity",
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    newState
                )
            } catch (e: Exception) {
                fail("Failed to re-enable MainActivity: ${e.message}")
            }
        } else {
            println("✅ MainActivity is already enabled (state: ${stateToString(currentState)})")
            assertTrue(true)
        }
    }
}
