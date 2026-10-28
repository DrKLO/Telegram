package org.telegram.messenger.test

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.telegram.messenger.MessagesStorage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertTrue

// How long storageQueue holds the database lock while [block] runs.
private const val LOCK_HOLD_MS = 3_000L

// The UI call must start at least this long before the lock is released, or the run proves nothing.
private const val MIN_OVERLAP_MS = 1_000L

// Far below the 5 s input dispatch ANR limit, far above emulator scheduling noise. Waiting for the
// lock shows up as roughly the whole LOCK_HOLD_MS.
private const val MAX_UI_BLOCK_MS = 500L

private const val CALIBRATION_ROWS = 2_000_000L

/**
 * Runs [block] on the UI thread while storageQueue holds the database lock with one long query,
 * and fails if [block] waited for the lock.
 */
fun assertUiDoesNotWaitForDatabase(storage: MessagesStorage, block: () -> Unit) {
    awaitStorageQueue(storage)

    // Size the query for this device, so it holds the lock for about LOCK_HOLD_MS.
    val calibrationMs = maxOf(1L, timeCountQuery(storage, CALIBRATION_ROWS))
    val rows = CALIBRATION_ROWS * LOCK_HOLD_MS / calibrationMs

    val stepStarted = CountDownLatch(1)
    val stepStartedAt = AtomicLong()
    val lockReleasedAt = AtomicLong()
    storage.storageQueue.postRunnable {
        val cursor = storage.database.queryFinalized(countQuery(rows))
        // The lock is held while the statement steps, not while it is prepared.
        stepStartedAt.set(SystemClock.elapsedRealtime())
        stepStarted.countDown()
        cursor.next()
        lockReleasedAt.set(SystemClock.elapsedRealtime())
        cursor.dispose()
    }
    assertTrue(stepStarted.await(10, TimeUnit.SECONDS), "storageQueue did not start the query")
    SystemClock.sleep(100)

    val uiStartedAt = AtomicLong()
    val uiFinishedAt = AtomicLong()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        uiStartedAt.set(SystemClock.elapsedRealtime())
        block()
        uiFinishedAt.set(SystemClock.elapsedRealtime())
    }
    awaitStorageQueue(storage, timeoutSeconds = 60)

    assumeTrue(
        "the UI call started ${lockReleasedAt.get() - uiStartedAt.get()} ms before the lock was released, too late to test",
        uiStartedAt.get() <= lockReleasedAt.get() - MIN_OVERLAP_MS
    )
    val blockedMs = uiFinishedAt.get() - uiStartedAt.get()
    assertTrue(
        blockedMs < MAX_UI_BLOCK_MS,
        "blocked the UI thread for $blockedMs ms while storageQueue held the database for ${lockReleasedAt.get() - stepStartedAt.get()} ms"
    )
}

/** Waits until everything already posted to storageQueue has run. */
fun awaitStorageQueue(storage: MessagesStorage, timeoutSeconds: Long = 30) {
    val done = CountDownLatch(1)
    storage.storageQueue.postRunnable { done.countDown() }
    assertTrue(done.await(timeoutSeconds, TimeUnit.SECONDS), "storageQueue is stuck")
}

private fun timeCountQuery(storage: MessagesStorage, rows: Long): Long {
    val elapsed = AtomicLong()
    storage.storageQueue.postRunnable {
        val start = SystemClock.elapsedRealtime()
        val cursor = storage.database.queryFinalized(countQuery(rows))
        cursor.next()
        cursor.dispose()
        elapsed.set(SystemClock.elapsedRealtime() - start)
    }
    awaitStorageQueue(storage)
    return elapsed.get()
}

private fun countQuery(rows: Long) =
    "WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM c WHERE x < $rows) SELECT count(*) FROM c"
