package com.example

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class TestGeneratorPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val generateSchemeTask = project.tasks.register(
            "generateScheme",
            GenerateSchemeTask::class.java
        ) {
            tlSourcesDir = File(project.rootDir, "TMessagesProj/src/main/java/org/telegram/tgnet")
            tlSourcesDirectDir = File(project.rootDir, "TMessagesProj_AppStandalone/src/main/java/org/telegram/tgnet")
            resourcesDir = project.file("tlscheme")
            outputDir = project.file("src/androidTest/kotlin")
        }

        project.afterEvaluate {
            project.tasks.matching {
                val name = it.name
                name.contains("preBuild")
            }.configureEach {
                println("🔗 Hooking generateTests before: ${this.name}")
                dependsOn(generateSchemeTask)
            }
        }

    }
}