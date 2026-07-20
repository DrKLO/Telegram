package com.example

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class TestGeneratorTask : DefaultTask() {
/*
    @get:InputDirectory
    var rootDir: File = File("")

    @get:InputDirectory
    var resourcesDir: File = File("")
*/
    @TaskAction
    fun read() {
        val jsonFile = project.file("src/main/resources/1.json")

        if (!jsonFile.exists()) {
            println("⚠️ File not found: ${jsonFile.absolutePath}")
            return
        }

        val content = jsonFile.readText()
        println("📄 Reading: ${jsonFile.absolutePath}")
        println("📦 Content:\n$content")
    }
}