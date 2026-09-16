package org.telegram.tasks

import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets

@CacheableTask
abstract class TelegramStringsTask : DefaultTask() {

    companion object {
        private val GENERATED_EXCLUSIONS = setOf(
            "AppName",
            "AppNameBeta"
        )

        private val STABLE_IDS_EXCLUSIONS = setOf(
            "AppName",
            "AppNameBeta"
        )

        private const val STRING_RESOURCE_ID_BASE = 0x7F0FFFFE
    }

    @get:Input
    abstract val resourcePackageName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stringsXml: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localizationFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val stringsOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val assetsOutputDir: DirectoryProperty

    @get:OutputFile
    abstract val stableIdsFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val stringsDir = stringsOutputDir.get().asFile
        val assetsDir = assetsOutputDir.get().asFile
        val stableIds = stableIdsFile.get().asFile

        stringsDir.mkdirs()
        assetsDir.mkdirs()
        stableIds.parentFile.mkdirs()

        // Remove the location used by the previous task version, otherwise an
        // old stable-ids.txt could remain inside the generated res directory.
        stringsDir.resolve("stable-ids.txt").delete()

        val pluralForms = sortedSetOf(
            "zero",
            "one",
            "two",
            "few",
            "many",
            "other"
        )

        // The plugin runs on the application now, so the default localization
        // is composed from two values/strings.xml files.
        val defaultLocalizationFiles = stringsXml.files.toList()


        // Union of all string resources defined in all localizations.
        val allStrings = sortedSetOf<String>()

        for (file in defaultLocalizationFiles) {
            collectStrings(
                file = file,
                destination = allStrings
            )
        }

        val localizationInputFiles = localizationFiles.files.toList()

        for (file in localizationInputFiles) {
            collectStrings(
                file = file,
                destination = allStrings
            )
        }

        // plural key -> forms that exist in at least one localization.
        val foundPlurals = HashMap<String, MutableSet<String>>()

        for (name in allStrings) {
            val index = name.lastIndexOf('_')

            if (index == -1) {
                continue
            }

            val suffix = name.substring(index + 1)

            if (!pluralForms.contains(suffix)) {
                continue
            }

            val key = name.substring(0, index)

            foundPlurals
                .getOrPut(key) { sortedSetOf() }
                .add(suffix)
        }

        // Add synthetic plural roots.
        for ((key, forms) in foundPlurals) {
            allStrings.add(key)


            // Add missing plural forms.
            for (form in pluralForms) {
                if (!forms.contains(form)) {
                    //  allStrings.add("${key}_${form}")
                }
            }

        }

        // Build the final hash namespace and fail on collisions.
        val hashesByName = buildHashes(allStrings)

        // Declare the entire string namespace in generated.xml and force-discard
        // the same generated placeholders from resource shrinker's safe-mode heuristics.
        generateResourceShrinkerRules(
            stringsDir = stringsDir,
            strings = allStrings
        )

        generateStableIds(
            outputFile = stableIds,
            strings = allStrings,
            resourcePackageName.get()
        )

        generateLocalizations(
            assetsDir = assetsDir,
            defaultLocalizationFiles = defaultLocalizationFiles,
            localizationInputFiles = localizationInputFiles,
            hashesByName = hashesByName
        )
    }

    private fun collectStrings(
        file: File,
        destination: MutableSet<String>
    ) {
        val strings = XmlParser().parse(file)

        for (string in strings["string"] as NodeList) {
            val node = string as Node
            val name = node["@name"].toString()

            destination.add(name)
        }
    }

    private fun buildHashes(
        allStrings: Set<String>
    ): Map<String, Int> {
        val namesByHash = HashMap<Int, String>(allStrings.size)
        val hashesByName = HashMap<String, Int>(allStrings.size)

        for (name in allStrings) {
            val hash = name.hashCode()

            val previous = namesByHash.put(hash, name)

            if (previous != null && previous != name) {
                error(
                    buildString {
                        appendLine("String hash collision:")

                        append("  ")
                        append(formatHash(hash))
                        append(" - R.string.")
                        appendLine(previous)

                        append("  ")
                        append(formatHash(hash))
                        append(" - R.string.")
                        appendLine(name)
                    }
                )
            }

            hashesByName[name] = hash
        }

        return hashesByName
    }

    private fun generateStableIds(
        outputFile: File,
        strings: Set<String>,
        packageName: String
    ) {
        val stableStrings = strings
            .asSequence()
            .filterNot { STABLE_IDS_EXCLUSIONS.contains(it) }
            .sorted()
            .toList()

        if (stableStrings.size > 0x10000) {
            error(
                "Too many stable string resources: ${stableStrings.size}. " +
                        "A single Android resource type supports at most 65536 entry IDs."
            )
        }

        outputFile
            .bufferedWriter(StandardCharsets.UTF_8)
            .use { output ->
                for ((index, name) in stableStrings.withIndex()) {
                    val resId = STRING_RESOURCE_ID_BASE - index
                    output.append(packageName)
                    output.append(":string/")
                    output.append(name)
                    output.append(" = ")
                    output.appendLine(formatHash(resId))
                }
            }
    }

    private fun generateResourceShrinkerRules(
        stringsDir: File,
        strings: Set<String>
    ) {
        val rawDir = stringsDir.resolve("raw")
        rawDir.mkdirs()

        val discard = strings
            .asSequence()
            .filterNot { GENERATED_EXCLUSIONS.contains(it) }
            .joinToString(", ") { "@string/$it" }

        rawDir.resolve("strings_discard.xml")
            .bufferedWriter(StandardCharsets.UTF_8)
            .use { xml ->
                xml.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                xml.appendLine("""<!-- AUTOGENERATED, DO NOT MODIFY -->""")
                xml.appendLine("<resources xmlns:tools=\"http://schemas.android.com/tools\"")
                xml.append("    tools:discard=\"")
                xml.append(discard)
                xml.appendLine("\" />")
            }
    }

    private fun generateLocalizations(
        assetsDir: File,
        defaultLocalizationFiles: List<File>,
        localizationInputFiles: List<File>,
        hashesByName: Map<String, Int>
    ): Map<String, String> {
        assetsDir
            .listFiles { file ->
                file.isFile && file.name.startsWith("localization_")
            }
            ?.forEach { file ->
                if (!file.delete()) {
                    error("Unable to delete old localization asset: ${file.absolutePath}")
                }
            }

        val localizations = sortedMapOf<String, String>()
        val tagsByAssetName = HashMap<String, String>()

        fun addLocalization(files: List<File>, languageTag: String) {
            val assetName = getLocalizationAssetName(languageTag)

            val previousTag = tagsByAssetName.put(assetName, languageTag)
            if (previousTag != null && previousTag != languageTag) {
                error(
                    "Localization asset name collision: " +
                            "\"$previousTag\" and \"$languageTag\" -> $assetName"
                )
            }

            val previousAsset = localizations.put(languageTag, assetName)
            if (previousAsset != null) {
                error("Duplicate localization language tag: $languageTag")
            }

            generateLocalization(
                inputFiles = files,
                outputFile = assetsDir.resolve(assetName),
                hashesByName = hashesByName
            )
        }

        addLocalization(
            files = defaultLocalizationFiles,
            languageTag = "en"
        )

        val localizedFilesByTag = linkedMapOf<String, MutableList<File>>()

        for (file in localizationInputFiles) {
            localizedFilesByTag
                .getOrPut(getLanguageTag(file)) { ArrayList() }
                .add(file)
        }

        for ((languageTag, files) in localizedFilesByTag) {
            addLocalization(
                files = files,
                languageTag = languageTag
            )
        }

        return localizations
    }

    private data class LocalizationEntry(
        val name: String,
        val hash: Int,
        val value: String
    )

    private fun generateLocalization(
        inputFiles: List<File>,
        outputFile: File,
        hashesByName: Map<String, Int>
    ) {
        val entriesByName = LinkedHashMap<String, LocalizationEntry>()

        for (inputFile in inputFiles) {
            val strings = XmlParser().parse(inputFile)

            for (string in strings["string"] as NodeList) {
                val node = string as Node
                val name = node["@name"].toString()

                val hash = hashesByName[name]
                    ?: error(
                        "Internal error: string is missing from merged namespace: " +
                                "R.string.$name in ${inputFile.absolutePath}"
                    )

                // Same semantics as an overlay: a later strings.xml wins.
                entriesByName[name] = LocalizationEntry(
                    name = name,
                    hash = hash,
                    value = normalizeXmlString(node.text())
                )
            }
        }

        val entries = entriesByName.values
            .sortedWith { first, second ->
                Integer.compareUnsigned(first.hash, second.hash)
            }

        BufferedOutputStream(outputFile.outputStream()).use { output ->
            writeInt32(output, entries.size)

            for (entry in entries) {
                writeInt32(output, entry.hash)
                writeString(
                    output = output,
                    value = entry.value,
                    name = entry.name,
                    inputFiles = inputFiles
                )
            }
        }
    }

    private fun normalizeXmlString(
        value: String
    ): String {
        return value
            .replace("\\n", "\n")
            .replace("\\", "")
            .replace("&lt;", "<")
    }

    private fun writeString(
        output: OutputStream,
        value: String,
        name: String,
        inputFiles: List<File>
    ) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val length = bytes.size

        if (length >= 1 shl 24) {
            error(
                "Localization string is too long: " +
                        "R.string.$name in ${inputFiles.joinToString { it.absolutePath }}: " +
                        "$length UTF-8 bytes"
            )
        }

        val headerSize: Int

        if (length < 254) {
            output.write(length)
            headerSize = 1
        } else {
            output.write(254)
            output.write(length and 0xFF)
            output.write((length shr 8) and 0xFF)
            output.write((length shr 16) and 0xFF)

            headerSize = 4
        }

        output.write(bytes)

        var totalSize = headerSize + length

        while (totalSize % 4 != 0) {
            output.write(0)
            totalSize++
        }
    }

    private fun writeInt32(
        output: OutputStream,
        value: Int
    ) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 24) and 0xFF)
    }

    private fun getLocalizationAssetName(
        languageTag: String
    ): String {
        val normalized = buildString(languageTag.length) {
            for (char in languageTag) {
                when {
                    char in 'A'..'Z' -> append(char.lowercaseChar())
                    char in 'a'..'z' || char in '0'..'9' || char == '_' -> append(char)
                    else -> append('_')
                }
            }
        }

        return "localization_$normalized.bin"
    }

    private fun getLanguageTag(
        file: File
    ): String {
        val directoryName = file.parentFile.name

        if (!directoryName.startsWith("values-")) {
            error("Invalid localization directory: ${file.absolutePath}")
        }

        val qualifier = directoryName.substring("values-".length)

        if (qualifier.isEmpty()) {
            error("Unable to determine language code: ${file.absolutePath}")
        }

        if (qualifier.startsWith("b+")) {
            return qualifier.substring(2).replace('+', '-')
        }

        val regionIndex = qualifier.indexOf("-r")

        if (regionIndex != -1) {
            val language = qualifier.substring(0, regionIndex)
            val region = qualifier.substring(regionIndex + 2)
            return "$language-$region"
        }

        return qualifier
    }

    private fun formatHash(
        hash: Int
    ): String {
        val unsigned = hash.toLong() and 0xFFFFFFFFL

        return "0x" +
                unsigned
                    .toString(16)
                    .uppercase()
                    .padStart(8, '0')
    }
}