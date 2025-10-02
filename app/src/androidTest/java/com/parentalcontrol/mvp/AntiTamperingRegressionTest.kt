package com.parentalcontrol.mvp

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for anti-tampering protection
 * 
 * PURPOSE:
 * - Ensures anti-tampering protection NEVER disables MainActivity in DEBUG builds
 * - Prevents regression of the "Activity does not exist" bug
 * - Validates that StealthManager.activateAntiTamperingProtection() is safe in development
 * 
 * REGRESSION SCENARIO:
 * Previously, activateAntiTamperingProtection() treated DEBUG builds as "tampering"
 * and called enableStealthMode(), which disabled MainActivity via PackageManager,
 * causing "Activity class does not exist" errors.
 * 
 * This test suite ensures this NEVER happens again.
 */
@RunWith(AndroidJUnit4::class)
class AntiTamperingRegressionTest {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = appContext.packageManager
    private lateinit var mainActivityComponent: ComponentName

    @Before
    fun setup() {
        mainActivityComponent = ComponentName(appContext, MainActivity::class.java)
        
        // Ensure MainActivity is enabled before each test
        val currentState = packageManager.getComponentEnabledSetting(mainActivityComponent)
        if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            packageManager.setComponentEnabledSetting(
                mainActivityComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    @After
    fun teardown() {
        // Always re-enable MainActivity after tests
        val currentState = packageManager.getComponentEnabledSetting(mainActivityComponent)
        if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            packageManager.setComponentEnabledSetting(
                mainActivityComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /**
     * CRITICAL REGRESSION TEST:
     * Verifies MainActivity remains enabled after launching with anti-tampering active
     * 
     * This test catches the exact bug we had:
     * MainActivity.onCreate() -> stealthManager.activateAntiTamperingProtection()
     * -> detectTamperingAttempts() returns true (because DEBUG build)
     * -> enableStealthMode() -> hideAppIcon() -> MainActivity DISABLED
     * -> Result: "Activity does not exist" error
     */
    @Test
    fun regression_mainActivityLaunch_withAntiTampering_shouldNotDisableActivity() {
        // Given: MainActivity is enabled
        val initialState = packageManager.getComponentEnabledSetting(mainActivityComponent)
        assertTrue(
            "MainActivity should be enabled before test",
            initialState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        )

        // When: We launch MainActivity (which calls activateAntiTamperingProtection in onCreate)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            assertNotNull("MainActivity should launch successfully", activity)
            
            // Give anti-tampering a moment to execute
            Thread.sleep(500)
        }
        
        // Then: MainActivity component should STILL be enabled
        val finalState = packageManager.getComponentEnabledSetting(mainActivityComponent)
        assertNotEquals(
            "REGRESSION BUG: MainActivity was DISABLED by anti-tampering! " +
            "This causes 'Activity does not exist' error.",
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            finalState
        )
        
        assertTrue(
            "MainActivity should remain enabled after launch",
            finalState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            finalState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        )
        
        scenario.close()
    }

    /**
     * REGRESSION TEST: Multiple launches should not accumulate issues
     */
    @Test
    fun regression_multipleLaunches_shouldNotDisableActivity() {
        repeat(3) { iteration ->
            println("Launch iteration: ${iteration + 1}")
            
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            
            scenario.onActivity { activity ->
                assertNotNull("MainActivity should launch on iteration ${iteration + 1}", activity)
            }
            
            // Check state after each launch
            val state = packageManager.getComponentEnabledSetting(mainActivityComponent)
            assertNotEquals(
                "MainActivity disabled after launch ${iteration + 1}",
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                state
            )
            
            scenario.close()
            Thread.sleep(300)
        }
    }

    /**
     * REGRESSION TEST: Anti-tampering should be a no-op in DEBUG
     */
    @Test
    fun regression_antiTampering_inDebugBuild_shouldBeNoOp() {
        // Launch activity and verify anti-tampering doesn't affect components
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            // Access StealthManager through reflection or directly
            // Verify no components were modified
            val mainState = packageManager.getComponentEnabledSetting(mainActivityComponent)
            
            assertNotEquals(
                "Anti-tampering should NOT disable MainActivity in DEBUG build",
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                mainState
            )
        }
        
        scenario.close()
    }

    /**
     * STRESS TEST: Rapid launches should not cause component state corruption
     */
    @Test
    fun stressTest_rapidLaunches_shouldNotCorruptComponentState() {
        repeat(5) { i ->
            try {
                val scenario = ActivityScenario.launch(MainActivity::class.java)
                scenario.close()
                
                val state = packageManager.getComponentEnabledSetting(mainActivityComponent)
                assertTrue(
                    "Component state corrupted after rapid launch $i",
                    state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                )
            } catch (e: Exception) {
                fail("Rapid launch $i failed: ${e.message}")
            }
        }
    }

    /**
     * DIAGNOSTIC TEST: Logs anti-tampering behavior
     */
    @Test
    fun diagnostic_antiTamperingBehavior() {
        println("\n=== ANTI-TAMPERING DIAGNOSTIC ===")
        println("BuildConfig.DEBUG: ${BuildConfig.DEBUG}")
        
        val beforeState = packageManager.getComponentEnabledSetting(mainActivityComponent)
        println("MainActivity state BEFORE launch: $beforeState")
        
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            Thread.sleep(1000) // Wait for anti-tampering to complete
        }
        
        val afterState = packageManager.getComponentEnabledSetting(mainActivityComponent)
        println("MainActivity state AFTER launch: $afterState")
        
        println("State changed: ${beforeState != afterState}")
        if (beforeState != afterState) {
            println("⚠️ WARNING: Component state changed during launch!")
        }
        
        println("=== END DIAGNOSTIC ===\n")
        
        scenario.close()
        
        // Test passes if component is still enabled
        assertTrue(
            "Component should remain enabled",
            afterState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        )
    }

    /**
     * VALIDATION TEST: Ensures test infrastructure is working
     */
    @Test
    fun sanityCheck_testInfrastructureWorks() {
        // Verify we can read component state
        val state = packageManager.getComponentEnabledSetting(mainActivityComponent)
        assertTrue("Should be able to read component state", state >= -1)
        
        // Verify we can launch MainActivity
        var launched = false
        try {
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { launched = true }
            scenario.close()
        } catch (e: Exception) {
            fail("Test infrastructure broken: Cannot launch MainActivity - ${e.message}")
        }
        
        assertTrue("MainActivity should be launchable", launched)
    }
}
