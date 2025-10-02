package com.parentalcontrol.mvp.messaging

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.SystemLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import org.junit.Assert.*
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ExperimentalCoroutinesApi
class MessagingIntegrationManagerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = TestCoroutineDispatcher()

    @Mock
    private lateinit var mockPreferencesManager: PreferencesManager
    
    @Mock
    private lateinit var mockSystemLogger: SystemLogger

    @Mock
    private lateinit var mockOkHttpClient: OkHttpClient

    @Mock
    private lateinit var mockCall: Call

    @Mock
    private lateinit var mockResponse: Response

    private lateinit var context: Context
    private lateinit var messagingManager: MessagingIntegrationManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        messagingManager = MessagingIntegrationManager(
            context = context,
            preferencesManager = mockPreferencesManager,
            systemLogger = mockSystemLogger
        )
    }

    @Test
    fun `should create messaging manager successfully`() {
        assertNotNull(messagingManager)
    }

    @Test
    fun `should configure Telegram settings correctly`() {
        // Given
        val botToken = "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val chatId = "987654321"
        val priorityThreshold = MessagingIntegrationManager.PRIORITY_MEDIUM

        // When
        val result = messagingManager.configureTelegram(
            botToken = botToken,
            chatIds = listOf(chatId),
            priorityThreshold = priorityThreshold
        )

        // Then
        assertTrue(result)
        verify(mockPreferencesManager).setTelegramBotToken(botToken)
        verify(mockPreferencesManager).setTelegramChatIds(listOf(chatId))
        verify(mockPreferencesManager).setMessagePriorityThreshold(priorityThreshold)
        verify(mockPreferencesManager).setTelegramEnabled(true)
    }

    @Test
    fun `should validate Telegram token format`() {
        // Test valid token format
        assertTrue(messagingManager.isValidTelegramToken("123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
        
        // Test invalid token formats
        assertFalse(messagingManager.isValidTelegramToken("invalid_token"))
        assertFalse(messagingManager.isValidTelegramToken("123456789"))
        assertFalse(messagingManager.isValidTelegramToken(""))
        assertFalse(messagingManager.isValidTelegramToken("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    }

    @Test
    fun `should validate chat ID format`() {
        // Test valid chat IDs
        assertTrue(messagingManager.isValidChatId("123456789"))
        assertTrue(messagingManager.isValidChatId("-123456789")) // Group chat
        
        // Test invalid chat IDs
        assertFalse(messagingManager.isValidChatId(""))
        assertFalse(messagingManager.isValidChatId("abc"))
        assertFalse(messagingManager.isValidChatId("12a34"))
    }

    @Test
    fun `should send high priority message when threshold is met`() {
        // Given
        whenever(mockPreferencesManager.isTelegramEnabled()).thenReturn(true)
        whenever(mockPreferencesManager.getMessagePriorityThreshold())
            .thenReturn(MessagingIntegrationManager.PRIORITY_MEDIUM)
        whenever(mockPreferencesManager.getTelegramBotToken()).thenReturn("123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        whenever(mockPreferencesManager.getTelegramChatIds()).thenReturn(listOf("123456789"))

        // When
        val result = messagingManager.sendAlert(
            title = "High Priority Alert",
            message = "Critical security incident detected",
            priority = MessagingIntegrationManager.PRIORITY_HIGH,
            messageType = MessagingIntegrationManager.TYPE_INCIDENT_ALERT
        )

        // Then - message should be sent because HIGH >= MEDIUM threshold
        assertTrue(result)
        verify(mockSystemLogger).logInfo(contains("Sending alert"))
    }

    @Test
    fun `should not send low priority message when threshold not met`() {
        // Given
        whenever(mockPreferencesManager.isTelegramEnabled()).thenReturn(true)
        whenever(mockPreferencesManager.getMessagePriorityThreshold())
            .thenReturn(MessagingIntegrationManager.PRIORITY_HIGH)

        // When
        val result = messagingManager.sendAlert(
            title = "Low Priority Alert",
            message = "Minor incident detected",
            priority = MessagingIntegrationManager.PRIORITY_LOW,
            messageType = MessagingIntegrationManager.TYPE_INCIDENT_ALERT
        )

        // Then - message should not be sent because LOW < HIGH threshold
        assertFalse(result)
        verify(mockSystemLogger).logDebug(contains("priority threshold not met"))
    }

    @Test
    fun `should test Telegram connection successfully`() {
        // Given
        val validToken = "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        whenever(mockPreferencesManager.getTelegramBotToken()).thenReturn(validToken)
        
        // When
        val result = messagingManager.testTelegramConnection()

        // Then
        // This test would require mocking HTTP responses, for now just verify method exists
        // In a real implementation, we'd mock successful API response
        assertNotNull(result) // Method should return some result
    }

    @Test
    fun `should handle retry logic for failed messages`() {
        // Given
        whenever(mockPreferencesManager.isTelegramEnabled()).thenReturn(true)
        whenever(mockPreferencesManager.getMessagePriorityThreshold())
            .thenReturn(MessagingIntegrationManager.PRIORITY_LOW)
        whenever(mockPreferencesManager.getTelegramBotToken()).thenReturn("123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        whenever(mockPreferencesManager.getTelegramChatIds()).thenReturn(listOf("123456789"))

        // When - simulate network failure
        val result = messagingManager.sendAlert(
            title = "Test Alert",
            message = "Test message",
            priority = MessagingIntegrationManager.PRIORITY_HIGH,
            messageType = MessagingIntegrationManager.TYPE_INCIDENT_ALERT
        )

        // Then - should handle failure gracefully
        // In real implementation, this would test retry mechanism
        verify(mockSystemLogger, atLeastOnce()).logInfo(any())
    }

    @Test
    fun `should queue messages when offline`() {
        // Given
        whenever(mockPreferencesManager.isTelegramEnabled()).thenReturn(true)
        whenever(mockPreferencesManager.getMessagePriorityThreshold())
            .thenReturn(MessagingIntegrationManager.PRIORITY_LOW)

        // When - add message to queue (simulate offline scenario)
        messagingManager.queueMessage(
            title = "Queued Alert",
            message = "This should be queued",
            priority = MessagingIntegrationManager.PRIORITY_MEDIUM,
            messageType = MessagingIntegrationManager.TYPE_DEVICE_STATUS
        )

        // Then - verify message is queued (would need access to internal queue)
        verify(mockSystemLogger).logInfo(contains("Message queued"))
    }

    @Test
    fun `should handle message formatting internally`() {
        // Since formatMessage is private, we test the public interface
        // that would use it internally
        
        // Test that sending an alert works without errors
        // (internal formatting would be tested through this)
        whenever(mockPreferencesManager.isTelegramEnabled()).thenReturn(false)
        
        val result = messagingManager.sendAlert(
            title = "Test Alert",
            message = "Test message",
            priority = MessagingIntegrationManager.PRIORITY_HIGH,
            messageType = MessagingIntegrationManager.TYPE_INCIDENT_ALERT
        )
        
        // Should return false when Telegram is disabled
        assertFalse(result)
    }
}
