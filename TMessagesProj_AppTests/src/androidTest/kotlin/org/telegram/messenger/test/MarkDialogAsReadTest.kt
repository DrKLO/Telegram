package org.telegram.messenger.test

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.UserConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.test.assertTrue

class MarkDialogAsReadTest {

    // markDialogAsRead() runs on the UI thread while the chat list draws. It must not wait for
    // the database: if storageQueue is busy with a long query, the UI thread blocks and the app
    // gets an input dispatch ANR.
    @Test
    fun markDialogAsReadDoesNotWaitForDatabase() {
        val account = UserConfig.selectedAccount
        val storage = MessagesStorage.getInstance(account)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            MessagesController.getInstance(account)
            NotificationsController.getInstance(account)
        }

        // Wait until the database is open.
        val opened = CountDownLatch(1)
        storage.storageQueue.postRunnable { opened.countDown() }
        assertTrue(opened.await(30, TimeUnit.SECONDS), "database did not open")

        // A channel that has never been loaded, so no cache can answer for it.
        val dialogId = -(1_000_000_000L + Random.nextLong(1_000_000_000L))

        // Hold the database lock with one long query, like a slow query on storageQueue would.
        val lockTaken = CountDownLatch(1)
        val lockReleased = CountDownLatch(1)
        val lockHeldMs = AtomicLong()
        storage.storageQueue.postRunnable {
            val start = SystemClock.elapsedRealtime()
            lockTaken.countDown()
            val cursor = storage.database.queryFinalized(
                "WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM c WHERE x < $LOCK_QUERY_ROWS) SELECT count(*) FROM c"
            )
            cursor.next()
            cursor.dispose()
            lockHeldMs.set(SystemClock.elapsedRealtime() - start)
            lockReleased.countDown()
        }
        assertTrue(lockTaken.await(10, TimeUnit.SECONDS), "storageQueue did not start the query")
        SystemClock.sleep(100)

        val blockedMs = AtomicLong()
        instrumentation.runOnMainSync {
            val start = SystemClock.elapsedRealtime()
            MessagesController.getInstance(account).markDialogAsRead(dialogId, 100, 100, 0, false, 0, 0, true, 0)
            blockedMs.set(SystemClock.elapsedRealtime() - start)
        }

        assertTrue(lockReleased.await(60, TimeUnit.SECONDS), "lock query did not finish")
        assumeTrue("lock was held for only ${lockHeldMs.get()} ms, too short to test", lockHeldMs.get() >= MIN_LOCK_MS)
        assertTrue(
            blockedMs.get() < MAX_UI_BLOCK_MS,
            "markDialogAsRead blocked the UI thread for ${blockedMs.get()} ms while storageQueue held the database for ${lockHeldMs.get()} ms"
        )
    }

    private companion object {
        const val LOCK_QUERY_ROWS = 50_000_000L
        const val MIN_LOCK_MS = 2_000L
        const val MAX_UI_BLOCK_MS = 500L
    }
}
