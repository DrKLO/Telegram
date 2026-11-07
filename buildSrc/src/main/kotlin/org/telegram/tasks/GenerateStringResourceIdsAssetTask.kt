package org.telegram.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

abstract class GenerateStringResourceIdsAssetTask : DefaultTask() {

    @get:InputFile
    abstract val runtimeSymbolList: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private data class Entry(
        val resId: Int,
        val nameHash: Int
    )

    @TaskAction
    fun generate() {
        val input = runtimeSymbolList.get().asFile
        val output = File(
            outputDir.get().asFile,
            "string_resource_ids.bin"
        )

        output.parentFile.mkdirs()

        val entries = ArrayList<Entry>()

        input.forEachLine { line ->
            val parts = line.trim().split(Regex("\\s+"))

            // R.txt:
            // int string AppName 0x7f120123
            if (parts.size != 4) {
                return@forEachLine
            }

            if (parts[0] != "int" || parts[1] != "string") {
                return@forEachLine
            }

            val name = parts[2]
            val resId = parts[3]
                .removePrefix("0x")
                .toLong(16)
                .toInt()

            entries.add(
                Entry(
                    resId = resId,
                    nameHash = name.hashCode()
                )
            )
        }

        entries.sortBy { it.resId }

        DataOutputStream(
            FileOutputStream(output).buffered()
        ).use { out ->

            // First 4 bytes: number of entries
            out.writeInt32(entries.size)

            for (entry in entries) {
                out.writeInt32(entry.resId)
                out.writeInt32(entry.nameHash)
            }
        }

        println(
            "Generated ${entries.size} string resource IDs: " +
                    output.absolutePath
        )

        println("Size: ${output.length()} bytes")
    }

    private fun DataOutputStream.writeInt32(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }
}