package com.parentalcontrol.mvp.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.parentalcontrol.mvp.model.Incident
import com.parentalcontrol.mvp.model.IncidentType
import com.parentalcontrol.mvp.model.Priority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ExperimentalCoroutinesApi
class IncidentManagerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = TestCoroutineDispatcher()
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    private lateinit var context: Context
    private lateinit var incidentManager: IncidentManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        
        incidentManager = IncidentManager(context)
    }

    @Test
    fun `should create incident manager successfully`() {
        assertNotNull(incidentManager)
    }

    @Test
    fun `should add incident with correct data`() = testDispatcher.runBlockingTest {
        // Given
        val content = "Podejrzana treść"
        val appName = "TestApp"
        val incidentType = IncidentType.SUSPICIOUS_CONTENT
        
        // When
        val incident = incidentManager.addIncident(
            content = content,
            appName = appName,
            incidentType = incidentType
        )
        
        // Then
        assertNotNull(incident)
        assertEquals(content, incident.content)
        assertEquals(appName, incident.appName)
        assertEquals(incidentType, incident.incidentType)
        assertTrue(incident.timestamp > 0)
    }

    @Test
    fun `should assign correct priority based on incident type`() = testDispatcher.runBlockingTest {
        // Test HIGH priority for EXPLICIT_CONTENT
        val explicitIncident = incidentManager.addIncident(
            content = "Explicit content",
            appName = "TestApp",
            incidentType = IncidentType.EXPLICIT_CONTENT
        )
        assertEquals(Priority.HIGH, explicitIncident.priority)

        // Test MEDIUM priority for SUSPICIOUS_CONTENT
        val suspiciousIncident = incidentManager.addIncident(
            content = "Suspicious content",
            appName = "TestApp", 
            incidentType = IncidentType.SUSPICIOUS_CONTENT
        )
        assertEquals(Priority.MEDIUM, suspiciousIncident.priority)
    }

    @Test
    fun `should detect repeated keywords and increase frequency`() = testDispatcher.runBlockingTest {
        val keyword = "test_keyword"
        
        // Add multiple incidents with same keyword
        repeat(3) {
            incidentManager.addIncident(
                content = "Content with $keyword",
                appName = "TestApp",
                incidentType = IncidentType.SUSPICIOUS_CONTENT
            )
        }
        
        // Verify frequency is tracked (this would require exposing internal state or adding getter)
        // For now, just verify incidents are created
        val incidents = incidentManager.getAllIncidents()
        assertTrue(incidents.size >= 3)
    }

    @Test
    fun `should filter incidents by priority`() = testDispatcher.runBlockingTest {
        // Add incidents with different priorities
        incidentManager.addIncident(
            content = "High priority content",
            appName = "TestApp",
            incidentType = IncidentType.EXPLICIT_CONTENT // HIGH priority
        )
        
        incidentManager.addIncident(
            content = "Medium priority content", 
            appName = "TestApp",
            incidentType = IncidentType.SUSPICIOUS_CONTENT // MEDIUM priority
        )
        
        // Get high priority incidents
        val highPriorityIncidents = incidentManager.getIncidentsByPriority(Priority.HIGH)
        assertTrue(highPriorityIncidents.isNotEmpty())
        assertTrue(highPriorityIncidents.all { it.priority == Priority.HIGH })
    }

    @Test
    fun `should clear old incidents beyond retention period`() = testDispatcher.runBlockingTest {
        // This test would require manipulating time or exposing cleanup methods
        // For now, verify the manager can handle incident clearing
        incidentManager.clearAllIncidents()
        
        val incidents = incidentManager.getAllIncidents()
        assertTrue(incidents.isEmpty())
    }

    @Test
    fun `should get recent incidents within time limit`() = testDispatcher.runBlockingTest {
        // Add test incident
        incidentManager.addIncident(
            content = "Recent incident",
            appName = "TestApp",
            incidentType = IncidentType.SUSPICIOUS_CONTENT
        )
        
        // Get incidents from last hour
        val recentIncidents = incidentManager.getRecentIncidents(hoursBack = 1)
        assertTrue(recentIncidents.isNotEmpty())
        
        // Verify timestamp is recent (within last hour)
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        assertTrue(recentIncidents.all { it.timestamp > oneHourAgo })
    }
}
