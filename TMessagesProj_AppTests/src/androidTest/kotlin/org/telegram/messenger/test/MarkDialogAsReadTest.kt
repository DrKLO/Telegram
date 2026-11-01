package org.telegram.messenger.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkDialogAsReadTest {

    private val account = UserConfig.selectedAccount
    private val controller get() = MessagesController.getInstance(account)
    private val storage get() = MessagesStorage.getInstance(account)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val cleanups = ArrayList<() -> Unit>()

    @Before
    fun setUp() {
        instrumentation.runOnMainSync {
            MessagesController.getInstance(account)
            NotificationsController.getInstance(account)
        }
        awaitStorageQueue(storage)
    }

    @After
    fun tearDown() {
        cleanups.asReversed().forEach { it() }
    }

    // markDialogAsRead() runs on the UI thread while the chat list draws. If it waits for the
    // database while storageQueue runs a long query, the app gets an input dispatch ANR.
    @Test
    fun markDialogAsReadDoesNotWaitForDatabase() {
        // A channel that has never been loaded, so no cache can answer for it.
        val dialogId = -randomChatId()
        removeReadTaskAfterTest(dialogId, 0)

        assertUiDoesNotWaitForDatabase(storage) {
            controller.markDialogAsRead(dialogId, 100, 100, 0, false, 0, 0, false, 0)
        }
    }

    // In a mono-forum, the thread id is the peer whose direct messages are being read.
    @Test
    fun monoForumReadTaskTargetsThePeer() {
        val chat = putChannel(monoforum = true)
        val peerId = Random.nextLong(1_000_000_000L, 2_000_000_000L)

        assertEquals(peerId, monoForumPeerIdOfReadTask(-chat.id, peerId))
    }

    @Test
    fun forumTopicReadTaskHasNoMonoForumPeer() {
        val chat = putChannel(monoforum = false)
        val topicId = Random.nextLong(1L, 1_000_000L)

        assertEquals(0L, monoForumPeerIdOfReadTask(-chat.id, topicId))
    }

    /** Marks a thread as read without sending the request, and returns its pending read task's monoForumPeerId. */
    private fun monoForumPeerIdOfReadTask(dialogId: Long, threadId: Long): Long {
        removeReadTaskAfterTest(dialogId, threadId)
        instrumentation.runOnMainSync {
            controller.markDialogAsRead(dialogId, 100, 0, 0, false, threadId, 0, false, 0)
        }
        return onStageQueue {
            val task = threadsReadTasksMap()["${dialogId}_$threadId"]
            assertNotNull(task, "no read task was created")
            task.javaClass.getDeclaredField("monoForumPeerId").apply { isAccessible = true }.getLong(task)
        }
    }

    /** Puts a channel into memory and into the database, as for a chat the app has loaded. */
    private fun putChannel(monoforum: Boolean): TLRPC.Chat {
        val chat = TLRPC.TL_channel().apply {
            id = randomChatId()
            access_hash = Random.nextLong()
            title = "test"
            photo = TLRPC.TL_chatPhotoEmpty()
            this.monoforum = monoforum
        }
        instrumentation.runOnMainSync { controller.putChat(chat, false) }
        storage.putUsersAndChats(null, arrayListOf<TLRPC.Chat>(chat), true, true)
        awaitStorageQueue(storage)
        // putUsersAndChats() logs and swallows write errors, so check the chat is really stored.
        assertEquals(monoforum, storage.getChatSync(chat.id)?.monoforum, "test chat was not stored")

        cleanups += {
            privateField<MutableMap<Long, TLRPC.Chat>>(controller, "chats").remove(chat.id)
            storage.storageQueue.postRunnable {
                storage.database.executeFast("DELETE FROM chats WHERE uid = ${chat.id}").stepThis().dispose()
            }
            awaitStorageQueue(storage)
        }
        return chat
    }

    /** Read tasks are sent 5 s after they are created. Remove them before that, so nothing reaches the network. */
    private fun removeReadTaskAfterTest(dialogId: Long, threadId: Long) {
        cleanups += {
            controller.dialogs_read_inbox_max.remove(dialogId)
            MessagesStorage::class.java.getDeclaredMethod("isForumCacheInvalidate", Long::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(storage, dialogId)
            onStageQueue {
                if (threadId != 0L) {
                    privateField<MutableList<Any?>>(controller, "repliesReadTasks").remove(threadsReadTasksMap().remove("${dialogId}_$threadId"))
                } else {
                    // androidx.collection.LongSparseArray, which isn't on the test classpath.
                    val map = privateField<Any>(controller, "readTasksMap")
                    val task = map.javaClass.getMethod("get", Long::class.javaPrimitiveType).invoke(map, dialogId)
                    map.javaClass.getMethod("remove", Long::class.javaPrimitiveType).invoke(map, dialogId)
                    privateField<MutableList<Any?>>(controller, "readTasks").remove(task)
                }
            }
        }
    }

    private fun threadsReadTasksMap() = privateField<MutableMap<String, Any>>(controller, "threadsReadTasksMap")

    /** Runs [block] on stageQueue, which owns the read task collections, and returns its result. */
    private fun <T> onStageQueue(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        val done = CountDownLatch(1)
        Utilities.stageQueue.postRunnable {
            result.set(runCatching(block))
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS), "stageQueue is stuck")
        return result.get().getOrThrow()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T

    // Far above real channel ids, so it can't collide with a chat in the cache.
    private fun randomChatId() = Random.nextLong(900_000_000_000L, 999_000_000_000L)
}
