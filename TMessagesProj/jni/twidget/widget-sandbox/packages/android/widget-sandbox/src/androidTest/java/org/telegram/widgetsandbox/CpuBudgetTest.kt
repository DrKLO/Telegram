package org.telegram.widgetsandbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** CpuBudget.kt against the fixture shared with cpu-budget.ts, CpuBudget.swift and cpu_budget.cpp (tests/fixtures/cpu-budget.json, copied to androidTest/assets by `pnpm build:android`). */
@RunWith(AndroidJUnit4::class)
class CpuBudgetTest {
    @Test
    fun sharedFixture() {
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open("cpu-budget.json").bufferedReader().readText()
        val cases = JSONObject(json).getJSONArray("cases")
        assertTrue(cases.length() > 5)
        for (c in 0 until cases.length()) {
            val case = cases.getJSONObject(c)
            val name = case.getString("name")
            val cfg = case.getJSONObject("config")
            val b = CpuBudget(cfg.getDouble("cpuLimitMs"), cfg.getDouble("cpuQuotaMs"), cfg.getDouble("cpuQuotaWindowMs"), cfg.getDouble("cpuKillAfterMs"))
            val steps = case.getJSONArray("steps")
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val at = step.getDouble("at")
                val where = "$name: step $i @${at.toInt()}"
                when {
                    step.has("decide") -> {
                        val expect = step.getJSONObject("expect")
                        val want: CpuDecision = if (expect.getBoolean("run")) CpuDecision.Run(expect.getDouble("sliceMs")) else CpuDecision.Defer(expect.getDouble("retryAt"))
                        assertEquals(where, want, b.decide(step.getString("decide"), at))
                    }
                    step.has("record") -> b.record(step.getString("record"), step.getDouble("cpuMs"), at)
                    step.has("kill") -> assertEquals(where, step.getBoolean("kill"), b.shouldKill(at))
                    step.has("used") -> assertEquals(where, step.getDouble("used"), b.used(at), 1e-9)
                    step.has("queries") -> {
                        val args = step.getJSONArray("queries")
                        val want = if (step.isNull("expect")) null else step.getString("expect")
                        assertEquals(where, want, CpuBudget.queryLimitHit(args.getInt(0), args.getDouble(1)))
                    }
                    step.has("opsCost") -> {
                        val args = step.getJSONArray("opsCost")
                        assertEquals(where, step.getDouble("expect"), CpuBudget.opsCostMs(args.getInt(0), args.getInt(1)), 1e-9)
                    }
                    else -> fail("$where: unknown step")
                }
            }
        }
    }
}
