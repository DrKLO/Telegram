package org.telegram.widgetsandbox

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.app.Activity
import android.content.ContextWrapper
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Loads the real renderer bundle in a WebView and talks to it through the real transport. */
@RunWith(AndroidJUnit4::class)
class RendererWebViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val resources = SandboxResources.get(instrumentation.targetContext)

    @Test
    fun rendererAnnouncesReadyAnswersQueriesAndIsHardened() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            val diags: MutableList<String> = Collections.synchronizedList(ArrayList())
            val messages: MutableList<String> = Collections.synchronizedList(ArrayList())
            val ready = CountDownLatch(1)
            val answered = CountDownLatch(1)
            lateinit var renderer: RendererWebView
            scenario.onActivity { activity ->
                renderer = RendererWebView(activity, resources.rendererBundle, "light")
                renderer.onDiag = { k, d -> diags.add("$k: $d") }
                renderer.onMessage = { json ->
                    messages.add(json)
                    if (json.contains("\"ready\"")) ready.countDown()
                    if (json.contains("\"result\"")) answered.countDown()
                }
                activity.show(renderer.webView)
                renderer.load()
            }
            assertTrue("renderer never became ready: $diags", ready.await(15, TimeUnit.SECONDS))
            assertTrue("$diags", diags.none { it.contains("load failed") })

            // Build a tiny tree, then ask for its layout through the real transport.
            instrumentation.runOnMainSync {
                renderer.send("""{"type":"ops","ops":[{"op":"el","id":2,"tag":"html","attrs":[],"adopt":"html"},{"op":"el","id":3,"tag":"body","attrs":[],"adopt":"body"},{"op":"el","id":4,"tag":"div","attrs":[["style","width:120px;height:30px"]]},{"op":"ins","parent":3,"id":4,"before":null}]}""")
                renderer.send("""{"type":"query","qid":1,"query":{"q":"rect","id":4}}""")
            }
            assertTrue("query not answered: $messages $diags", answered.await(10, TimeUnit.SECONDS))
            val result = messages.last { it.contains("\"result\"") }
            assertTrue(result, result.contains("\"width\":120"))
            assertTrue("size report expected: $messages", messages.any { it.contains("\"size\"") })

            // The document-start script removed the network APIs from the renderer window.
            val hardened = CountDownLatch(1)
            var apis = ""
            instrumentation.runOnMainSync {
                renderer.evaluate("[typeof fetch, typeof XMLHttpRequest, typeof WebSocket, typeof RTCPeerConnection, typeof EventSource, typeof navigator.sendBeacon, typeof window.open, location.protocol, location.host].join()") {
                    apis = it ?: ""
                    hardened.countDown()
                }
            }
            assertTrue(hardened.await(5, TimeUnit.SECONDS))
            assertTrue(apis, apis.startsWith("\"undefined,undefined,undefined,undefined,undefined,undefined,undefined,https:,w-"))
            assertTrue(apis, apis.contains(RendererWebView.ORIGIN_SUFFIX))

            // The WebView barrier itself (CSP, interception, blockNetworkLoads): an
            // external image injected straight into the renderer — bypassing the
            // validator on purpose — must be refused and reported.
            instrumentation.runOnMainSync {
                renderer.send("""{"type":"ops","ops":[{"op":"el","id":5,"tag":"img","attrs":[["src","http://10.0.2.2:9999/hit/renderer-barrier"]]},{"op":"ins","parent":3,"id":5,"before":null}]}""")
            }
            val deadline = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < deadline && diags.none { it.startsWith("url-blocked") || it.contains("Content Security Policy") }) Thread.sleep(50)
            assertTrue("barrier should report the refused image: $diags", diags.any { it.startsWith("url-blocked") || it.contains("Content Security Policy") })
            val natural = CountDownLatch(1)
            var loadedState = ""
            instrumentation.runOnMainSync {
                renderer.evaluate("(function(){var i=document.images[0];return i ? [i.complete, i.naturalWidth].join() : 'no img'})()") { loadedState = it ?: ""; natural.countDown() }
            }
            assertTrue(natural.await(5, TimeUnit.SECONDS))
            assertEquals("\"true,0\"", loadedState)

            // And a navigation attempt from the renderer is cancelled.
            instrumentation.runOnMainSync { renderer.evaluate("location.href = 'http://10.0.2.2:9999/hit/renderer-nav'; 'ok'", null) }
            val navDeadline = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < navDeadline && diags.none { it.startsWith("navigation-blocked") }) Thread.sleep(50)
            assertTrue("navigation should be blocked: $diags", diags.any { it.startsWith("navigation-blocked") })
            instrumentation.runOnMainSync { renderer.destroy() }
        }
    }

    /**
     * Chromium shows its colour / date pickers and <select> popups only when it
     * can find an Activity behind the WebView's context; the themed context must
     * therefore wrap the activity, not replace it (createConfigurationContext did,
     * and the pickers silently did nothing on a real phone).
     */
    @Test
    fun themedContextKeepsTheActivityAndSetsNightMode() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for ((theme, night) in listOf("dark" to Configuration.UI_MODE_NIGHT_YES, "light" to Configuration.UI_MODE_NIGHT_NO)) {
                    val ctx = RendererWebView.themedContext(activity, theme)
                    var c: android.content.Context? = ctx
                    while (c is ContextWrapper && c !is Activity) c = c.baseContext
                    assertTrue("$theme: no Activity behind the themed context", c is Activity)
                    assertEquals(theme, night, ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                }
            }
        }
    }
}
