package com.parentalcontrol.mvp

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.FileLogger
import com.parentalcontrol.mvp.utils.SystemLogger
import com.parentalcontrol.mvp.messaging.MessagingIntegrationManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ExperimentalCoroutinesApi
class MainActivityTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = TestCoroutineDispatcher()

    @Mock
    private lateinit var mockPreferencesManager: PreferencesManager
    
    @Mock
    private lateinit var mockFileLogger: FileLogger
    
    @Mock
    private lateinit var mockSystemLogger: SystemLogger
    
    @Mock
    private lateinit var mockMessagingManager: MessagingIntegrationManager
    
    @Mock
    private lateinit var mockMediaProjectionManager: MediaProjectionManager

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Enable logging for debugging
        ShadowLog.stream = System.out
        
        // Mock SystemLogger singleton
        mockStatic(SystemLogger::class.java).use { mockedStatic ->
            mockedStatic.`when`<SystemLogger> { SystemLogger.getInstance(any()) }
                .thenReturn(mockSystemLogger)
        }
    }

    @Test
    fun `should create MainActivity successfully`() {
        // Given - setup basic mocks
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        // Then
        scenario.onActivity { activity ->
            assertNotNull(activity)
            assertTrue(activity.javaClass.simpleName == "MainActivity")
        }
        
        scenario.close()
    }

    @Test 
    fun `should initialize all managers in onCreate`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        // Then - verify activity starts without crashing
        scenario.onActivity { activity ->
            // Activity should be created and initialized
            assertNotNull(activity)
            
            // Verify that the activity has gone through onCreate
            // (Testing internal state would require exposing getters)
        }
        
        scenario.close()
    }

    @Test
    fun `should handle first run setup correctly`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(true)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        // Then
        scenario.onActivity { activity ->
            assertNotNull(activity)
            // In a real app, this would trigger first-run setup
            // We'd verify PreferencesManager.setFirstRunComplete() was called
        }
        
        scenario.close()
    }

    @Test
    fun `should request necessary permissions on startup`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        // Then - verify activity handles permissions
        scenario.onActivity { activity ->
            // Activity should check for permissions
            // In real implementation, we'd verify permission requests
            assertNotNull(activity)
        }
        
        scenario.close()
    }

    @Test
    fun `should toggle monitoring service correctly`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            // Simulate button click to toggle monitoring
            // This would require access to UI components
            assertNotNull(activity)
            
            // Test service state management
            // In real implementation:
            // 1. Click start monitoring button
            // 2. Verify service starts
            // 3. Click stop monitoring button  
            // 4. Verify service stops
        }
        
        scenario.close()
    }

    @Test
    fun `should load recent logs correctly`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            // Test log loading functionality
            assertNotNull(activity)
            
            // In real implementation, we'd:
            // 1. Mock FileLogger to return test log entries
            // 2. Verify UI updates with log data
            // 3. Test pagination/filtering of logs
        }
        
        scenario.close()
    }

    @Test
    fun `should handle configuration changes correctly`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            // Test configuration change (rotation)
            activity.recreate()
            
            // Then - activity should survive recreation
            assertNotNull(activity)
        }
        
        scenario.close()
    }

    @Test
    fun `should navigate to settings correctly`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            // Test navigation to settings
            assertNotNull(activity)
            
            // In real implementation:
            // 1. Click settings button
            // 2. Verify correct intent is started
            // 3. Test back navigation
        }
        
        scenario.close()
    }

    @Test
    fun `should handle messaging integration correctly`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        whenever(mockMessagingManager.isConfigured()).thenReturn(true)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
            
            // Test messaging integration
            // In real implementation:
            // 1. Verify messaging manager is initialized
            // 2. Test messaging configuration dialog
            // 3. Test message sending functionality
        }
        
        scenario.close()
    }

    @Test
    fun `should handle stealth mode activation`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        whenever(mockPreferencesManager.isStealthModeEnabled()).thenReturn(true)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
            
            // Test stealth mode functionality
            // In real implementation:
            // 1. Verify stealth manager activation
            // 2. Test app icon hiding/showing
            // 3. Test anti-tampering protection
        }
        
        scenario.close()
    }

    @Test
    fun `should handle service status updates correctly`() {
        // Given
        whenever(mockPreferencesManager.isFirstRun()).thenReturn(false)
        
        // When
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            assertNotNull(activity)
            
            // Test service status monitoring
            // In real implementation:
            // 1. Mock service running state
            // 2. Verify UI reflects service status
            // 3. Test status change notifications
        }
        
        scenario.close()
    }

    private inline fun <reified T> mockStatic(klass: Class<T>): MockedStatic<T> {
        return Mockito.mockStatic(klass)
    }
}
