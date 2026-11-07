package org.telegram.widgetsandbox

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The validator's time counts against the JS quota on the real stack (docs/decisions.md, "The validator's time counts"):
 * WidgetHost hands the validator's measured CPU time to the engine, which reports it as `stats.validatorMs`. The JVM
 * half is ValidatorCpuTest; the twins are desktop `widget_host_charges_the_validators_time` and ValidatorTimeTests.swift.
 */
@RunWith(AndroidJUnit4::class)
class ValidatorTimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun widgetHostChargesTheValidatorsTime() {
        val html = "<div id=d></div><script>var d = document.getElementById('d');" +
            "for (var i = 0; i < 300; i++) d.style.setProperty('--v' + (i % 7), 'var(--x, ' + i + 'px) calc(' + i + 'px + 1em)');" +
            "TelegramWidget.ready();</script>"
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            val latest = AtomicReference(WidgetDiagnostics())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, html, WidgetTheme(), 360.0)
                host.onChange = { latest.set(it) }
                activity.show(host.view)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline && latest.get().stats.validatorMs <= 0.0 && latest.get().dead == null) Thread.sleep(50)
            val d = latest.get()
            assertNull(d.dead)
            assertTrue("validatorMs ${d.stats.validatorMs}", d.stats.validatorMs > 0.0 && d.stats.validatorMs < 2000.0)
            instrumentation.runOnMainSync { host.destroy() }
        }
    }

    @Test
    fun aChargeDefersTimersLikeJsTime() {
        val html = "<script>setInterval(function () { console.log('tick'); }, 20);</script>"
        val resources = SandboxResources.get(instrumentation.targetContext)
        fun ticksAfter(charge: Double): Int {
            val d = RecordingDelegate()
            val config = SandboxConfig(innerWidth = 400.0, innerHeight = 300.0, devicePixelRatio = 2.0, theme = "light", cpuLimitMs = 2000.0, cpuQuotaMs = 100.0)
            val engine = SandboxEngine(html, config, resources)
            engine.delegate = d
            engine.start()
            assertTrue(d.loaded.await(20, TimeUnit.SECONDS))
            val ticks = { d.logs.count { it.second == "tick" } }
            val before = ticks()
            if (charge > 0) Thread { engine.chargeValidator(charge) }.apply { start(); join() }
            Thread.sleep(600)
            val after = ticks() - before
            engine.terminate()
            assertNull(d.died?.takeIf { it != "terminated" })
            return after
        }
        val free = ticksAfter(0.0)
        val charged = ticksAfter(300.0)
        assertTrue("free $free", free > 10)
        assertTrue("charged $charged (300 ms of validation against a 100 ms quota)", charged < 3)
    }
}
