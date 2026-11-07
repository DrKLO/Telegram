package org.telegram.widgetsandbox

import java.io.File

/** SdkPolicyFixtureTest on the JVM: the fixture's copy in src/androidTest/assets, from the module directory (Gradle's working directory for unit tests). */
class SdkPolicyTest : SdkPolicyFixtureTest() {
    override fun fixtureJson() = File("src/androidTest/assets/sdk-policy.json").readText()
}
