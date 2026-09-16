package org.telegram.plugin

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.telegram.tasks.localization.GenerateLocalizationUtilsJavaTask

class TelegramBuildPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val androidComponents =
            project.extensions.findByType(AndroidComponentsExtension::class.java)
                ?: error("Apply com.android.application/library before org.telegram.build-plugin")

        androidComponents.onVariants { variant ->
            val suffix = variant.name.replaceFirstChar { it.uppercase() }

            val task = project.tasks.register<GenerateLocalizationUtilsJavaTask>(
                "generate${suffix}LocalizationUtilsJava"
            ) {
                localizationFiles.from(
                    project.fileTree("src/main/res") {
                        include("values-*/strings.xml")
                    }
                )

                javaOutputDir.set(
                    project.layout.buildDirectory.dir(
                        "generated/generateLocalizationUtilsJava/${variant.name}"
                    )
                )
            }

            variant.sources.java?.addGeneratedSourceDirectory(
                task,
                GenerateLocalizationUtilsJavaTask::javaOutputDir
            )
        }
    }
}