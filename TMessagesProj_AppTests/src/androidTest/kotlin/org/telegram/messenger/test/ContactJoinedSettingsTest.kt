package org.telegram.messenger.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Covers the "Contact joined Telegram" setting: notification filtering and
 * syncing the setting with the server.
 */
class ContactJoinedSettingsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var controller: MessagesController

    private var savedPrefExists = false
    private var savedPref = true
    private var savedEnableJoined = true
    private var savedSignUpLoaded = false

    @Before
    fun setUp() {
        controller = MessagesController.getInstance(ACCOUNT)
        // MessagesController reads the stored setting asynchronously on creation;
        // wait for it so it does not overwrite the state set by a test.
        drainQueues()

        val prefs = MessagesController.getNotificationsSettings(ACCOUNT)
        savedPrefExists = prefs.contains(PREF_KEY)
        savedPref = prefs.getBoolean(PREF_KEY, true)
        savedEnableJoined = controller.enableJoined
        savedSignUpLoaded = UserConfig.getInstance(ACCOUNT).notificationsSignUpSettingsLoaded
    }

    @After
    fun tearDown() {
        onMain {
            val editor = MessagesController.getNotificationsSettings(ACCOUNT).edit()
            if (savedPrefExists) {
                editor.putBoolean(PREF_KEY, savedPref)
            } else {
                editor.remove(PREF_KEY)
            }
            editor.commit()
            controller.enableJoined = savedEnableJoined

            val userConfig = UserConfig.getInstance(ACCOUNT)
            userConfig.notificationsSignUpSettingsLoaded = savedSignUpLoaded
            userConfig.saveConfig(false)
        }
    }

    // region isContactJoinedMessage

    @Test
    fun isContactJoinedMessage_detectsSignUpActions() {
        Assert.assertTrue(MessagesController.isContactJoinedMessage(signUpMessage()))
        Assert.assertTrue(MessagesController.isContactJoinedMessage(legacyJoinedMessage()))
    }

    @Test
    fun isContactJoinedMessage_rejectsOtherMessages() {
        Assert.assertFalse(MessagesController.isContactJoinedMessage(null))
        Assert.assertFalse(MessagesController.isContactJoinedMessage(textMessage()))
        val otherService = signUpMessage().apply { action = TLRPC.TL_messageActionHistoryClear() }
        Assert.assertFalse(MessagesController.isContactJoinedMessage(otherService))
    }

    // endregion

    // region notification filter

    @Test
    fun notificationFilter_skipsSignUpWhenSettingDisabled() {
        val message = signUpMessage().apply { silent = false }
        Assert.assertTrue(NotificationsController.isMutedContactJoinedMessage(message, false))
    }

    @Test
    fun notificationFilter_skipsSignUpWhenServerMarksSilent() {
        val message = signUpMessage().apply { silent = true }
        Assert.assertTrue(NotificationsController.isMutedContactJoinedMessage(message, true))
    }

    @Test
    fun notificationFilter_showsSignUpWhenEnabledAndNotSilent() {
        val message = signUpMessage().apply { silent = false }
        Assert.assertFalse(NotificationsController.isMutedContactJoinedMessage(message, true))
    }

    @Test
    fun notificationFilter_neverSkipsRegularMessages() {
        for (silent in booleanArrayOf(false, true)) {
            for (allow in booleanArrayOf(false, true)) {
                val message = textMessage().apply { this.silent = silent }
                Assert.assertFalse(
                    "silent=$silent allow=$allow",
                    NotificationsController.isMutedContactJoinedMessage(message, allow)
                )
            }
        }
    }

    // endregion

    // region server sync

    @Test
    fun serverSettings_notSilentMeansEnabled() {
        onMain {
            setSetting(false)
            UserConfig.getInstance(ACCOUNT).notificationsSignUpSettingsLoaded = false

            controller.processSignUpNotificationsSettings(TLRPC.TL_boolFalse(), null)

            assertSetting(true)
            Assert.assertTrue(UserConfig.getInstance(ACCOUNT).notificationsSignUpSettingsLoaded)
        }
    }

    @Test
    fun serverSettings_silentMeansDisabled() {
        onMain {
            setSetting(true)

            controller.processSignUpNotificationsSettings(TLRPC.TL_boolTrue(), null)

            assertSetting(false)
            Assert.assertTrue(UserConfig.getInstance(ACCOUNT).notificationsSignUpSettingsLoaded)
        }
    }

    @Test
    fun serverSettings_refreshesSettingsScreen() {
        onMain {
            setSetting(true)

            val updates = countNotifications(NotificationCenter.notificationsSettingsUpdated) {
                controller.processSignUpNotificationsSettings(TLRPC.TL_boolTrue(), null)
            }

            Assert.assertEquals(1, updates)
        }
    }

    @Test
    fun serverSettings_errorKeepsLocalChoiceAndAllowsRetry() {
        onMain {
            setSetting(false)
            UserConfig.getInstance(ACCOUNT).notificationsSignUpSettingsLoaded = false

            controller.processSignUpNotificationsSettings(null, networkError())
            assertSetting(false)
            Assert.assertFalse(UserConfig.getInstance(ACCOUNT).notificationsSignUpSettingsLoaded)

            controller.processSignUpNotificationsSettings(TLRPC.TL_boolFalse(), networkError())
            assertSetting(false)

            controller.processSignUpNotificationsSettings(TLRPC.TL_messageActionEmpty(), null)
            assertSetting(false)
            Assert.assertFalse(UserConfig.getInstance(ACCOUNT).notificationsSignUpSettingsLoaded)
        }
    }

    @Test
    fun toggleResult_successKeepsNewValue() {
        onMain {
            setSetting(false)

            val updates = countNotifications(NotificationCenter.notificationsSettingsUpdated) {
                controller.onContactSignUpNotificationSet(false, null)
            }

            assertSetting(false)
            Assert.assertEquals(0, updates)
        }
    }

    @Test
    fun toggleResult_failureRevertsAndRefreshesScreen() {
        onMain {
            setSetting(false)

            val updates = countNotifications(NotificationCenter.notificationsSettingsUpdated) {
                controller.onContactSignUpNotificationSet(false, networkError())
            }

            assertSetting(true)
            Assert.assertEquals(1, updates)
        }
    }

    @Test
    fun toggleResult_staleFailureDoesNotOverrideNewerChoice() {
        onMain {
            // The user turned it off and on again; the first request fails afterwards.
            setSetting(true)

            val updates = countNotifications(NotificationCenter.notificationsSettingsUpdated) {
                controller.onContactSignUpNotificationSet(false, networkError())
            }

            assertSetting(true)
            Assert.assertEquals(0, updates)
        }
    }

    // endregion

    private fun drainQueues() {
        val latch = CountDownLatch(1)
        Utilities.globalQueue.postRunnable { latch.countDown() }
        Assert.assertTrue("globalQueue did not drain", latch.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            }
        }
        failure?.let { throw it }
    }

    private fun countNotifications(id: Int, block: () -> Unit): Int {
        var count = 0
        val observer = NotificationCenter.NotificationCenterDelegate { receivedId, account, _ ->
            if (receivedId == id && account == ACCOUNT) {
                count++
            }
        }
        val group = NotificationCenter.getInstance(ACCOUNT).createObserversGroup(observer).add(id)
        try {
            block()
        } finally {
            group.removeAllObservers()
        }
        return count
    }

    private fun setSetting(enabled: Boolean) {
        MessagesController.getNotificationsSettings(ACCOUNT).edit().putBoolean(PREF_KEY, enabled).commit()
        controller.enableJoined = enabled
    }

    private fun assertSetting(expected: Boolean) {
        Assert.assertEquals("stored preference", expected, MessagesController.getNotificationsSettings(ACCOUNT).getBoolean(PREF_KEY, true))
        Assert.assertEquals("in-memory flag", expected, controller.enableJoined)
    }

    private fun signUpMessage(): TLRPC.Message = serviceMessage(TLRPC.TL_messageActionContactSignUp())

    private fun legacyJoinedMessage(): TLRPC.Message = serviceMessage(TLRPC.TL_messageActionUserJoined())

    private fun serviceMessage(serviceAction: TLRPC.MessageAction) = TLRPC.TL_messageService().apply {
        id = nextMessageId++
        date = now()
        peer_id = TLRPC.TL_peerUser().apply { user_id = USER_ID }
        from_id = TLRPC.TL_peerUser().apply { user_id = USER_ID }
        action = serviceAction
    }

    private fun textMessage(): TLRPC.Message = TLRPC.TL_message().apply {
        id = nextMessageId++
        date = now()
        peer_id = TLRPC.TL_peerUser().apply { user_id = USER_ID }
        from_id = TLRPC.TL_peerUser().apply { user_id = USER_ID }
        message = "hi"
    }

    private fun networkError() = TLRPC.TL_error().apply {
        code = 500
        text = "INTERNAL"
    }

    private fun now() = (System.currentTimeMillis() / 1000).toInt()

    companion object {
        private val ACCOUNT = UserConfig.MAX_ACCOUNT_COUNT - 1
        private const val PREF_KEY = "EnableContactJoined"

        // Far outside real id ranges so it never collides with cached users.
        private const val USER_ID = 9_000_000_001L

        private var nextMessageId = 1_000_000
    }
}
