package org.telegram.tasks

import com.google.gson.stream.JsonReader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

abstract class GenerateLottieMetadataAssetFileTask : DefaultTask() {

    /** One or more raw resource directories. */
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rawResourceDirs: ConfigurableFileCollection

    /** SingleArtifact.RUNTIME_SYMBOL_LIST for the current variant. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeSymbolList: RegularFileProperty

    /** Generated assets root. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private data class Metadata(val fps: Int, val frameCount: Int, val monocolor: Boolean)

    private data class Entry(val resId: Int, val metadata: Metadata) {
        val packed: Long
            get() = (resId.toLong() shl 32) or
                    ((metadata.fps.toLong() and 0xFFL) shl 24) or
                    (if (metadata.monocolor) 1L shl 23 else 0L) or
                    (metadata.frameCount.toLong() and 0x7FFFFFL)
    }

    @TaskAction
    fun run() {
        val rawIds = readRawResourceIds(runtimeSymbolList.get().asFile)

        val parsed = rawResourceDirs.files
            .asSequence()
            .flatMap { input ->
                when {
                    input.isDirectory -> input.walkTopDown().filter(File::isFile)
                    input.isFile -> sequenceOf(input)
                    else -> emptySequence()
                }
            }
            .mapNotNull { file ->
                val info = parseLottie(file) ?: return@mapNotNull null
                val name = file.nameWithoutExtension
                val fps = info.fps.roundToInt()

                if (fps !in 0..FPS_MAX) {
                    throw GradleException("R.raw.$name: fps=$fps does not fit into 8 bits (0..$FPS_MAX)")
                }
                if (info.frameCount !in 0..FRAMES_MAX) {
                    throw GradleException(
                        "R.raw.$name: frameCount=${info.frameCount} does not fit into 23 bits (0..$FRAMES_MAX)"
                    )
                }

                name to Metadata(fps, info.frameCount, false)
            }
            .groupBy({ it.first }, { it.second })

        val entries = parsed.map { (name, variants) ->
            val distinct = variants.distinct()
            if (distinct.size > 1) {
                logger.warn("R.raw.$name: conflicting metadata across qualifiers, using the first")
            }

            val resId = rawIds[name]
                ?: throw GradleException("R.raw.$name is missing from ${runtimeSymbolList.get().asFile}")
            val metadata = variants.first()
            logger.lifecycle(
                "R.raw.%s (0x%08x) — fps=%d, frames=%d, mono=%b"
                    .format(name, resId, metadata.fps, metadata.frameCount, metadata.monocolor)
            )
            Entry(resId, metadata)
        }.sortedBy(Entry::packed) // Same ordering as Arrays.sort(long[]) in the original code.

        val root = outputDir.get().asFile
        root.deleteRecursively()
        root.mkdirs()
        writeEntries(File(root, OUTPUT_FILE_NAME), entries)
    }

    private fun readRawResourceIds(symbolList: File): Map<String, Int> {
        val result = HashMap<String, Int>()
        symbolList.useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split(WHITESPACE)
                if (parts.size >= 4 && parts[0] == "int" && parts[1] == "raw") {
                    val value = parts[3].removePrefix("0x").toLongOrNull(16)
                        ?: throw GradleException("Invalid raw resource id in $symbolList: $line")
                    result[parts[2]] = value.toInt()
                }
            }
        }
        return result
    }

    private fun writeEntries(file: File, entries: List<Entry>) {
        BufferedOutputStream(FileOutputStream(file)).use { output ->
            entries.forEach { entry ->
                var value = entry.packed
                repeat(Long.SIZE_BYTES) {
                    output.write((value and 0xFFL).toInt())
                    value = value ushr 8
                }
            }
        }
    }

    private data class LottieInfo(val fps: Double, val frameCount: Int)

    private fun parseLottie(file: File): LottieInfo? = try {
        file.inputStream().buffered().use { raw ->
            raw.mark(2)
            val gzip = raw.read() == 0x1f && raw.read() == 0x8b
            raw.reset()
            val stream = if (gzip) GZIPInputStream(raw) else raw

            var fr: Double? = null
            var ip: Double? = null
            var op: Double? = null

            JsonReader(stream.reader(Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "fr" -> fr = reader.nextDouble()
                        "ip" -> ip = reader.nextDouble()
                        "op" -> op = reader.nextDouble()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }

            val frameRate = fr
            val inPoint = ip
            val outPoint = op
            if (frameRate == null || inPoint == null || outPoint == null || frameRate <= 0.0) {
                return null
            }
            LottieInfo(frameRate, (outPoint - inPoint).roundToInt())
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val OUTPUT_FILE_NAME = "lottie_meta.bin"
        const val FPS_MAX = 0xFF
        const val FRAMES_MAX = 0x7FFFFF
        val WHITESPACE = Regex("\\s+")
    }
}
