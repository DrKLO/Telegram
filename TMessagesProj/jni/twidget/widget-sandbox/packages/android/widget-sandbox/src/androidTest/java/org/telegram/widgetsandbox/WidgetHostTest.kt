package org.telegram.widgetsandbox

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** End-to-end on the real stack: engine thread → validator thread → WebView. */
@RunWith(AndroidJUnit4::class)
class WidgetHostTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val blockedKinds = listOf("url-blocked", "network-blocked", "navigation-blocked", "tag-dropped", "script-skipped", "css-dropped")

    private fun blocked(d: WidgetDiagnostics) = blockedKinds.sumOf { d.diags[it]?.count ?: 0 }

    @Test
    fun validatorDiagnosticsReachTheHost() {
        // Two external URLs in a stylesheet the widget builds at runtime: the
        // validator must report them (url-blocked / css-dropped) and ship none.
        val html = """
            <p>x</p><script>
            var s = document.createElement('style');
            s.textContent = '@import url(http://10.0.2.2:9999/i); p { background: url(http://10.0.2.2:9999/b) }';
            document.head.appendChild(s);
            </script>
        """.trimIndent()
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            val latestRef = java.util.concurrent.atomic.AtomicReference(WidgetDiagnostics())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, html, WidgetTheme(), 360.0)
                host.onChange = { latestRef.set(it) }
                activity.show(host.view)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline && !(latestRef.get().loaded && latestRef.get().diags.keys.any { it == "url-blocked" || it == "css-dropped" })) Thread.sleep(50)
            assertTrue("widget never loaded: ${latestRef.get().diags.mapValues { it.value.count }}", latestRef.get().loaded)
            assertTrue("${latestRef.get().diags.mapValues { it.value.details }}", latestRef.get().diags.keys.any { it == "url-blocked" || it == "css-dropped" })
            assertNull(latestRef.get().dead)
            assertTrue("height ${latestRef.get().height}", latestRef.get().height >= 24)
            instrumentation.runOnMainSync { host.destroy() }
        }
    }

    /** CSSOM attacks from the corpus: blocked inside the shim (reported through the engine) and/or by the validator; both paths must land in diagnostics. */
    @Test
    fun cssomAttacksAreReportedAsBlocked() {
        val cases = listOf(
            "api-css-adopted" to "<p>adoptedStyleSheets</p><script>try { var s = new CSSStyleSheet(); s.replaceSync('body { background: url(http://10.0.2.2:9999/hit/api-adopted) } @import url(http://10.0.2.2:9999/hit/api-adopted-import);'); document.adoptedStyleSheets = [s]; } catch (e) { console.log('blocked:', e.name); }</script>",
            "api-css-insertrule" to "<style>p { color: #333; }</style><p>insertRule</p><script>try { document.styleSheets[0].insertRule('body { background: url(http://10.0.2.2:9999/hit/api-insertrule) }', 0); } catch (e) { console.log('blocked:', e.name); } try { document.styleSheets[0].insertRule('@import url(http://10.0.2.2:9999/hit/api-insertrule-import);', 0); } catch (e) { console.log('blocked:', e.name); } try { document.querySelector('style').sheet.addRule('p', 'background: url(http://10.0.2.2:9999/hit/api-addrule)'); } catch (e) { console.log('blocked:', e.name); }</script>",
        )
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            for ((name, html) in cases) {
                lateinit var host: WidgetHost
                val latestRef = java.util.concurrent.atomic.AtomicReference(WidgetDiagnostics())
                scenario.onActivity { activity ->
                    host = WidgetHost(activity, html, WidgetTheme(), 360.0)
                    host.onChange = { latestRef.set(it) }
                    activity.show(host.view)
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6)
                while (System.nanoTime() < deadline && !(latestRef.get().loaded && blocked(latestRef.get()) > 0)) Thread.sleep(50)
                Thread.sleep(500)
                println("$name: loaded=${latestRef.get().loaded} diags=${latestRef.get().diags.mapValues { it.value.count }} logs=${latestRef.get().logs.map { it.second }}")
                assertTrue(name, latestRef.get().loaded)
                assertTrue("$name: ${latestRef.get().diags.mapValues { it.value.details }}", blocked(latestRef.get()) > 0)
                instrumentation.runOnMainSync { host.destroy() }
            }
        }
    }

    /** The View trigger of the off-screen pause: detaching the view from the window stops frames and tells the widget it is hidden; re-attaching resumes. */
    @Test
    fun detachingTheViewFromTheWindowPausesTheWidget() {
        val html = "<p id=\"s\"></p><script>var n = 0; function step() { n++; requestAnimationFrame(step); } requestAnimationFrame(step);" +
            "function show() { document.getElementById('s').textContent += document.visibilityState + ','; } document.addEventListener('visibilitychange', show); show();</script>"
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            val latestRef = java.util.concurrent.atomic.AtomicReference(WidgetDiagnostics())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, html, WidgetTheme(), 360.0)
                host.onChange = { latestRef.set(it) }
                activity.show(host.view)
            }
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline && !(latestRef.get().loaded && latestRef.get().stats.tasks > 20)) Thread.sleep(50)
            assertTrue("frames should be flowing while attached: ${latestRef.get().stats.tasks}", latestRef.get().stats.tasks > 20)

            scenario.onActivity { activity -> activity.container.removeView(host.view) }
            Thread.sleep(400) // the visibility task and an in-flight frame
            val atHidden = latestRef.get().stats.tasks
            Thread.sleep(1000)
            assertEquals("no frames while detached", atHidden, latestRef.get().stats.tasks)

            scenario.onActivity { activity -> activity.show(host.view) }
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline && latestRef.get().stats.tasks < atHidden + 10) Thread.sleep(50)
            assertTrue("frames resume after re-attach: ${latestRef.get().stats.tasks} vs $atHidden", latestRef.get().stats.tasks > atHidden + 10)
            val result = java.util.concurrent.atomic.AtomicReference<String>()
            val done = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync { host.evaluateInRenderer("document.getElementById('s').textContent") { result.set(it); done.countDown() } }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals("\"visible,hidden,visible,\"", result.get())
            assertNull(latestRef.get().dead)
            instrumentation.runOnMainSync { host.destroy() }
        }
    }

    /** A killed widget is not torn down: the WebView keeps the last render, the renderer marks the document frozen and shows the badge. */
    @Test
    fun killedWidgetKeepsLastRender() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            val latestRef = java.util.concurrent.atomic.AtomicReference(WidgetDiagnostics())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, "<p id=\"keep\">still here</p><script>for(;;){}</script>", WidgetTheme(), 360.0, WidgetHost.Options(cpuLimitMs = 300.0))
                host.onChange = { latestRef.set(it) }
                activity.show(host.view)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (System.nanoTime() < deadline && latestRef.get().dead == null) Thread.sleep(50)
            assertTrue("${latestRef.get().dead}", latestRef.get().dead?.contains("CPU limit") == true)
            Thread.sleep(500)
            val result = java.util.concurrent.atomic.AtomicReference<String>()
            val done = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                host.evaluateInRenderer("document.body.textContent + '|' + document.documentElement.dataset.twFrozen + '|' + (document.querySelector('[data-tw-badge]') || {}).textContent") { result.set(it); done.countDown() }
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals("\"still hereWidget stopped|fatal|Widget stopped\"", result.get())
            assertTrue("view still attached", host.view.isAttachedToWindow)
            instrumentation.runOnMainSync { host.destroy() }
        }
    }

    /**
     * A counted cap passed under strict stops the widget like memory or CPU does (docs/decisions.md, "Strict limits from
     * the start"): a 40 000-character text node set by a timer is refused with its whole batch, the reason is the
     * validator's, the renderer keeps the last render frozen with the badge, and nothing the widget does later arrives.
     * Under compat the same text (up to 2 MB) is drawn.
     */
    @Test
    fun aTextOverTheCapStopsTheWidget() {
        val html = "<p id=\"p\">kept</p><script>setTimeout(function () { document.getElementById('p').textContent = 'x'.repeat(40000); }, 50);" +
            "setTimeout(function () { document.getElementById('p').textContent = 'never'; }, 300);</script>"
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            val latestRef = AtomicReference(WidgetDiagnostics())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, html, WidgetTheme(), 360.0)
                host.onChange = { latestRef.set(it) }
                activity.show(host.view)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (System.nanoTime() < deadline && latestRef.get().dead == null) Thread.sleep(50)
            assertEquals("Text limit exceeded: a text value of 40000 characters (max 32768)", latestRef.get().dead)
            assertEquals(1, latestRef.get().diags["limit"]?.count)
            Thread.sleep(800) // the second timer's time: the engine is gone, nothing more is drawn
            val result = AtomicReference<String>()
            val done = CountDownLatch(1)
            instrumentation.runOnMainSync {
                host.evaluateInRenderer("document.body.textContent + '|' + document.documentElement.dataset.twFrozen") { result.set(it); done.countDown() }
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals("\"keptWidget stopped|fatal\"", result.get())
            instrumentation.runOnMainSync { host.destroy() }

            latestRef.set(WidgetDiagnostics())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, html, WidgetTheme(), 360.0, WidgetHost.Options(policy = "compat"))
                host.onChange = { latestRef.set(it) }
                activity.show(host.view)
            }
            val deadline2 = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (System.nanoTime() < deadline2 && latestRef.get().stats.tasks < 4) Thread.sleep(50)
            Thread.sleep(500)
            assertNull(latestRef.get().dead)
            assertNull(latestRef.get().diags["limit"])
            val compat = AtomicReference<String>()
            val compatDone = CountDownLatch(1)
            instrumentation.runOnMainSync { host.evaluateInRenderer("document.body.textContent") { compat.set(it); compatDone.countDown() } }
            assertTrue(compatDone.await(5, TimeUnit.SECONDS))
            assertEquals("\"never\"", compat.get())
            instrumentation.runOnMainSync { host.destroy() }
        }
    }

    /** The paint limit (docs/decisions.md "Heavy boxes"): the WebView renderer stops a widget with 17 heavy boxes, compat counts nothing. */
    @Test
    fun seventeenHeavyBoxesStopTheWidget() {
        val html = "<style>.b{display:inline-block;width:12px;height:12px;margin:2px;box-shadow:0 2px 6px rgba(0,0,0,.4)}</style>" + "<div class=b></div>".repeat(17)
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            for (policy in listOf("strict", "compat")) {
                lateinit var host: WidgetHost
                val latestRef = AtomicReference(WidgetDiagnostics())
                scenario.onActivity { activity ->
                    host = WidgetHost(activity, html, WidgetTheme(), 360.0, WidgetHost.Options(policy = policy))
                    host.onChange = { latestRef.set(it) }
                    activity.show(host.view)
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while (System.nanoTime() < deadline && latestRef.get().dead == null && !(policy == "compat" && latestRef.get().firstContentMs != null)) Thread.sleep(50)
                Thread.sleep(800)
                if (policy == "strict") {
                    assertEquals(WidgetPaintLimit.PAINT.message(17), latestRef.get().dead)
                    assertEquals(1, latestRef.get().diags["paint-limit"]?.count)
                } else {
                    assertNull(latestRef.get().dead)
                }
                instrumentation.runOnMainSync { host.destroy() }
            }
        }
    }

    /** Memory and CPU limits are configurable from the public host API (not the case on iOS yet). */
    @Test
    fun limitsAreConfigurableFromTheHostApi() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            val latestRef = java.util.concurrent.atomic.AtomicReference(WidgetDiagnostics())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, "<p>cpu</p><script>for(;;){}</script>", WidgetTheme(), 360.0, WidgetHost.Options(cpuLimitMs = 150.0))
                host.onChange = { latestRef.set(it) }
                activity.show(host.view)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline && latestRef.get().dead == null) Thread.sleep(50)
            assertTrue("${latestRef.get().dead}", latestRef.get().dead?.contains("150 ms") == true)
            instrumentation.runOnMainSync { host.destroy() }
            latestRef.set(WidgetDiagnostics())

            scenario.onActivity { activity ->
                host = WidgetHost(activity, "<p>mem</p><script>var a=[]; for(;;) a.push(new Array(100000).fill(1));</script>", WidgetTheme(), 360.0, WidgetHost.Options(memoryLimitBytes = 24L shl 20, cpuLimitMs = 10_000.0))
                host.onChange = { latestRef.set(it) }
                activity.show(host.view)
            }
            val deadline2 = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline2 && latestRef.get().dead == null) Thread.sleep(50)
            assertTrue("${latestRef.get().dead}", latestRef.get().dead?.contains("Memory limit exceeded (24 MB)") == true)
            instrumentation.runOnMainSync { host.destroy() }
        }
    }

    /** Create and tear down 25 full widgets (three threads + a WebView each) in a row. */
    @Test
    fun repeatedCreateAndTeardownOfFullWidgets() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            for (i in 0 until 25) {
                lateinit var host: WidgetHost
                val latestRef = java.util.concurrent.atomic.AtomicReference(WidgetDiagnostics())
                scenario.onActivity { activity ->
                    host = WidgetHost(activity, "<p id=\"p\">w$i</p><script>document.getElementById('p').textContent += ' ' + document.getElementById('p').getBoundingClientRect().width;</script>", WidgetTheme(if (i % 2 == 0) "light" else "dark"), 360.0)
                    host.onChange = { latestRef.set(it) }
                    activity.show(host.view)
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (System.nanoTime() < deadline && !latestRef.get().loaded && latestRef.get().dead == null) Thread.sleep(25)
                assertTrue("widget $i never loaded: ${latestRef.get().diags.mapValues { it.value.details }}", latestRef.get().loaded)
                assertNull(latestRef.get().dead)
                instrumentation.runOnMainSync { host.destroy() }
            }
        }
    }

    /**
     * A runtime theme toggle is complete without any WebView-level dark mode:
     * the validator resolves `color-scheme: light dark`, `light-dark()` and
     * `prefers-color-scheme` against the host theme and re-emits the
     * stylesheets, so even the UA's own form-control colours follow. (The
     * WebView's internal prefers-color-scheme stays whatever it was at
     * creation; nothing observable by a widget depends on it.)
     */
    @Test
    // Profile 1 has no form controls, so only the document's own styles follow the theme.
    fun themeToggleReachesComputedStyles() {
        val html = "<style>:root{color-scheme: light dark} body{background: light-dark(#ffffff, #000000)} @media (prefers-color-scheme: dark){p{color:#abcdef}}</style><p>theme</p>"
        val probe = "(function(){ return [getComputedStyle(document.documentElement).colorScheme, getComputedStyle(document.body).backgroundColor, getComputedStyle(document.querySelector('p')).color].join('|') })()"
        fun eval(host: WidgetHost): String {
            val done = CountDownLatch(1)
            val out = AtomicReference("")
            instrumentation.runOnMainSync { host.evaluateInRenderer(probe) { out.set(it); done.countDown() } }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            return out.get().trim('"')
        }
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            val latestRef = AtomicReference(WidgetDiagnostics())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, html, WidgetTheme(), 360.0)
                host.onChange = { latestRef.set(it) }
                activity.show(host.view)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline && !latestRef.get().loaded) Thread.sleep(50)
            Thread.sleep(300)
            assertEquals("light|rgb(255, 255, 255)|rgb(0, 0, 0)", eval(host))
            instrumentation.runOnMainSync { host.setTheme(WidgetTheme("dark")) }
            Thread.sleep(500)
            assertEquals("dark|rgb(0, 0, 0)|rgb(171, 205, 239)", eval(host))
            // The pre-v1 string form still compiles and forwards to setTheme(WidgetTheme).
            @Suppress("DEPRECATION")
            instrumentation.runOnMainSync { host.setTheme("light") }
            Thread.sleep(500)
            assertEquals("light|rgb(255, 255, 255)|rgb(0, 0, 0)", eval(host))
            instrumentation.runOnMainSync { host.destroy() }
        }
    }
}
