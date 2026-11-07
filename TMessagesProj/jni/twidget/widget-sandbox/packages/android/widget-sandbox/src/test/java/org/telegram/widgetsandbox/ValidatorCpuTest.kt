package org.telegram.widgetsandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The validator's time counts against the JS quota (docs/decisions.md, "The validator's time counts"): [CpuMeter] as the
 * validator runs it, and its report recorded the way [SandboxEngine.chargeValidator] records it. The twins: desktop
 * `validator_time_counts_against_the_js_quota`, ValidatorTimeTests.swift; end to end on a device: ValidatorTimeTest.
 */
class ValidatorCpuTest {
    private class Clock { var nanos = 0L }

    @Test
    fun theMeterReportsEachOutermostCallOnce() {
        val clock = Clock()
        val meter = CpuMeter { clock.nanos }
        val reports = mutableListOf<Double>()
        assertEquals("no sink: nothing is measured", 7, meter.measure { clock.nanos += 5_000_000; 7 })
        meter.sink = { reports.add(it) }
        val out = meter.measure {
            clock.nanos += 5_000_000
            meter.measure { clock.nanos += 3_000_000 } // a call inside a measured one is part of it
            "ok"
        }
        assertEquals("ok", out)
        assertEquals(listOf(8.0), reports)
        try {
            meter.measure { clock.nanos += 2_000_000; throw IllegalStateException("x") }
        } catch (e: IllegalStateException) {
        }
        assertEquals("a call that throws still counts", listOf(8.0, 2.0), reports)
        meter.measure { clock.nanos += 1_000_000 }
        assertEquals(listOf(8.0, 2.0, 1.0), reports)
        meter.sink = null
        meter.measure { clock.nanos += 1_000_000 }
        assertEquals(3, reports.size)
    }

    /** 300 ms of validation against a 100 ms quota: the window fills, one more quota is the debt, timers wait, events run. */
    @Test
    fun aChargeDefersTimersLikeJsTime() {
        val budget = CpuBudget(500.0, 100.0, 1000.0, 0.0)
        val clock = Clock()
        val meter = CpuMeter { clock.nanos }
        var now = 0.0
        meter.sink = { ms -> budget.record("validator", ms, now) }
        assertEquals(CpuDecision.Run(500.0), budget.decide("timer", now))
        now = 10.0
        meter.measure { clock.nanos += 300_000_000 }
        assertEquals(200.0, budget.used(now), 1e-9)
        assertTrue(budget.decide("timer", now) is CpuDecision.Defer)
        assertTrue(budget.decide("frame", now) is CpuDecision.Defer)
        assertEquals(CpuDecision.Run(500.0), budget.decide("event", now))
        // The window's bucket leaves after a second and the debt moves in: still spent until the second window passes.
        assertTrue(budget.decide("timer", 1050.0) is CpuDecision.Defer)
        assertEquals(CpuDecision.Run(500.0), budget.decide("timer", 2100.0))
    }
}
