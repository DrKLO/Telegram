package org.telegram.widgetsandbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

/** GesturePolicyFixtureTest on the device: the fixture from the test APK's assets, read with the platform's org.json. */
@RunWith(AndroidJUnit4::class)
class GesturePolicyTest : GesturePolicyFixtureTest() {
    override fun fixtureJson() = InstrumentationRegistry.getInstrumentation().context.assets.open("gesture-policy.json").bufferedReader().readText()
}
