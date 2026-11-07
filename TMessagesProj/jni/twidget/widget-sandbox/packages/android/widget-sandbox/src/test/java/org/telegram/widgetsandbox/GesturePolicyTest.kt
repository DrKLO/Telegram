package org.telegram.widgetsandbox

import java.io.File

/** GesturePolicyFixtureTest on the JVM: the fixture's copy in src/androidTest/assets, from the module directory (Gradle's working directory for unit tests). */
class GesturePolicyTest : GesturePolicyFixtureTest() {
    override fun fixtureJson() = File("src/androidTest/assets/gesture-policy.json").readText()
}
