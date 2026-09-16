package org.telegram.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream

abstract class EmojiPackTask : DefaultTask() {

    companion object {
        private const val NO_MASK = 0xFFFF

        /*
         * emoji.pack binary format
         * ========================
         *
         * All integer values are stored in little-endian byte order.
         *
         * File layout:
         *
         *   [4 bytes] emoji metadata block length in bytes
         *
         *   Emoji metadata block:
         *
         *       Repeated for every emoji:
         *
         *       [2 bytes] emojiIndex
         *           Computed from the source filename "x_y.png" as:
         *
         *               emojiIndex = x * 4096 + y
         *
         *       [2 bytes] maskId
         *           ID of the alpha mask associated with this emoji.
         *           0xFFFF means that the emoji has no associated mask.
         *
         *       [4 bytes] pngOffset
         *           Absolute byte offset of the PNG data from the beginning
         *           of this file.
         *
         *       [4 bytes] pngLength
         *           Length of the PNG data in bytes.
         *
         *
         *   [4 bytes] mask metadata block length in bytes
         *
         *   Mask metadata block:
         *
         *       Repeated for every alpha mask:
         *
         *       [2 bytes] maskId
         *           Mask ID derived from the source filename "n.png".
         *
         *       [4 bytes] pngOffset
         *           Absolute byte offset of the PNG data from the beginning
         *           of this file.
         *
         *       [4 bytes] pngLength
         *           Length of the PNG data in bytes.
         *
         *
         *   PNG data block:
         *
         *       [emoji PNG data...]
         *       [mask PNG data...]
         *
         *
         * Emoji metadata records are sorted by emojiIndex.
         * Mask metadata records are sorted by maskId.
         *
         * Source metadata.bin contains pairs of little-endian uint16 values:
         *
         *       [2 bytes] emojiIndex
         *       [2 bytes] maskId
         */
        private const val EMOJI_METADATA_ENTRY_SIZE =
            2 + // emojiIndex
                    2 + // maskId
                    4 + // pngOffset
                    4   // pngLength

        private const val MASK_METADATA_ENTRY_SIZE =
            2 + // maskId
                    4 + // pngOffset
                    4   // pngLength
    }

    @get:InputDirectory
    abstract val emojiDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private data class EmojiFile(
        val file: File,
        val emojiIndex: Int,
        val maskId: Int,
        var offset: Long = 0
    )

    private data class MaskFile(
        val file: File,
        val maskId: Int,
        var offset: Long = 0
    )

    @TaskAction
    fun run() {
        val emojiRoot = emojiDir.get().asFile
        val outputRoot = outputDir.get().asFile

        require(emojiRoot.isDirectory) {
            "Emoji directory does not exist: ${emojiRoot.absolutePath}"
        }

        outputRoot.mkdirs()

        val metadataFile = File(emojiRoot, "metadata.bin")
        val masksDir = File(emojiRoot, "masks")

        val maskMapping = readMaskMapping(metadataFile)

        val emojis = readEmojiFiles(
            emojiRoot = emojiRoot,
            maskMapping = maskMapping
        )

        val masks = readMaskFiles(masksDir)

        validateMaskReferences(
            emojis = emojis,
            masks = masks
        )

        val metadata1Length =
            emojis.size.toLong() * EMOJI_METADATA_ENTRY_SIZE

        val metadata2Length =
            masks.size.toLong() * MASK_METADATA_ENTRY_SIZE

        require(metadata1Length <= 0xFFFFFFFFL) {
            "Emoji metadata block is too large: $metadata1Length"
        }

        require(metadata2Length <= 0xFFFFFFFFL) {
            "Mask metadata block is too large: $metadata2Length"
        }

        /*
         * The PNG data starts after:
         *
         *   uint32 emojiMetadataLength
         *   emojiMetadata
         *   uint32 maskMetadataLength
         *   maskMetadata
         */
        var dataOffset =
            4L +
                    metadata1Length +
                    4L +
                    metadata2Length

        for (emoji in emojis) {
            emoji.offset = dataOffset

            dataOffset += emoji.file.length()

            require(dataOffset <= 0xFFFFFFFFL) {
                "Emoji pack exceeds 4 GiB"
            }
        }

        for (mask in masks) {
            mask.offset = dataOffset

            dataOffset += mask.file.length()

            require(dataOffset <= 0xFFFFFFFFL) {
                "Emoji pack exceeds 4 GiB"
            }
        }

        val outputFile = File(outputRoot, "emoji.pack")

        DataOutputStream(
            BufferedOutputStream(
                outputFile.outputStream()
            )
        ).use { out ->

            /*
             * Emoji metadata block.
             */
            out.writeUInt32LE(metadata1Length)

            for (emoji in emojis) {
                out.writeUInt16LE(emoji.emojiIndex)
                out.writeUInt16LE(emoji.maskId)
                out.writeUInt32LE(emoji.offset)
                out.writeUInt32LE(emoji.file.length())
            }

            /*
             * Mask metadata block.
             */
            out.writeUInt32LE(metadata2Length)

            for (mask in masks) {
                out.writeUInt16LE(mask.maskId)
                out.writeUInt32LE(mask.offset)
                out.writeUInt32LE(mask.file.length())
            }

            /*
             * Emoji PNG data.
             */
            for (emoji in emojis) {
                emoji.file.inputStream().buffered().use { input ->
                    input.copyTo(out)
                }
            }

            /*
             * Mask PNG data.
             */
            for (mask in masks) {
                mask.file.inputStream().buffered().use { input ->
                    input.copyTo(out)
                }
            }
        }

        logger.lifecycle(
            "Generated ${outputFile.absolutePath}"
        )
        logger.lifecycle(
            "Emoji files: ${emojis.size}"
        )
        logger.lifecycle(
            "Mask files: ${masks.size}"
        )
        logger.lifecycle(
            "Size: ${outputFile.length()} bytes"
        )
    }

    private fun readEmojiFiles(
        emojiRoot: File,
        maskMapping: Map<Int, Int>
    ): List<EmojiFile> {

        val regex = Regex("""^(\d+)_(\d+)\.png$""")

        val result = emojiRoot
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.filter { it.extension.equals("png", ignoreCase = true) }
            ?.map { file ->
                val match = regex.matchEntire(file.name)
                    ?: error(
                        "Invalid emoji filename: ${file.absolutePath}. " +
                                "Expected x_y.png"
                    )

                val x = match.groupValues[1].toInt()
                val y = match.groupValues[2].toInt()

                require(y in 0 until 4096) {
                    "Invalid emoji y=$y in ${file.name}; expected 0..4095"
                }

                val emojiIndexLong =
                    x.toLong() * 4096L + y.toLong()

                require(emojiIndexLong in 0..0xFFFFL) {
                    "emojiIndex does not fit uint16: " +
                            "$emojiIndexLong for ${file.name}"
                }

                val emojiIndex = emojiIndexLong.toInt()

                val maskId =
                    maskMapping[emojiIndex] ?: NO_MASK

                require(maskId in 0..0xFFFF) {
                    "maskId does not fit uint16: $maskId"
                }

                EmojiFile(
                    file = file,
                    emojiIndex = emojiIndex,
                    maskId = maskId
                )
            }
            ?.sortedBy { it.emojiIndex }
            ?.toList()
            ?: emptyList()

        val duplicates = result
            .groupBy { it.emojiIndex }
            .filterValues { it.size > 1 }

        require(duplicates.isEmpty()) {
            buildString {
                append("Duplicate emojiIndex values:\n")

                duplicates.forEach { (index, files) ->
                    append("  ")
                    append(index)
                    append(": ")
                    append(
                        files.joinToString {
                            it.file.name
                        }
                    )
                    append('\n')
                }
            }
        }

        return result
    }

    private fun readMaskFiles(
        masksDir: File
    ): List<MaskFile> {

        if (!masksDir.exists()) {
            return emptyList()
        }

        require(masksDir.isDirectory) {
            "Masks path is not a directory: ${masksDir.absolutePath}"
        }

        val regex = Regex("""^(\d+)\.png$""")

        val result = masksDir
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.filter { it.extension.equals("png", ignoreCase = true) }
            ?.map { file ->
                val match = regex.matchEntire(file.name)
                    ?: error(
                        "Invalid mask filename: ${file.absolutePath}. " +
                                "Expected n.png"
                    )

                val maskId =
                    match.groupValues[1].toInt()

                require(maskId in 0 until NO_MASK) {
                    "maskId must be in 0..65534: " +
                            "$maskId in ${file.name}"
                }

                MaskFile(
                    file = file,
                    maskId = maskId
                )
            }
            ?.sortedBy { it.maskId }
            ?.toList()
            ?: emptyList()

        val duplicates = result
            .groupBy { it.maskId }
            .filterValues { it.size > 1 }

        require(duplicates.isEmpty()) {
            buildString {
                append("Duplicate maskId values:\n")

                duplicates.forEach { (id, files) ->
                    append("  ")
                    append(id)
                    append(": ")
                    append(
                        files.joinToString {
                            it.file.name
                        }
                    )
                    append('\n')
                }
            }
        }

        return result
    }

    private fun readMaskMapping(
        metadataFile: File
    ): Map<Int, Int> {

        if (!metadataFile.exists()) {
            return emptyMap()
        }

        val bytes = metadataFile.readBytes()

        require(bytes.size % 4 == 0) {
            "Invalid metadata.bin size: ${bytes.size}; expected multiple of 4"
        }

        val result =
            HashMap<Int, Int>(bytes.size / 4)

        var offset = 0

        while (offset < bytes.size) {
            val emojiIndex =
                (bytes[offset].toInt() and 0xFF) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 8)

            val maskId =
                (bytes[offset + 2].toInt() and 0xFF) or
                        ((bytes[offset + 3].toInt() and 0xFF) shl 8)

            val previous =
                result.put(emojiIndex, maskId)

            require(previous == null) {
                "Duplicate emojiIndex=$emojiIndex in metadata.bin"
            }

            offset += 4
        }

        return result
    }

    private fun validateMaskReferences(
        emojis: List<EmojiFile>,
        masks: List<MaskFile>
    ) {
        val availableMasks =
            masks.asSequence()
                .map { it.maskId }
                .toHashSet()

        for (emoji in emojis) {
            if (
                emoji.maskId != NO_MASK &&
                emoji.maskId !in availableMasks
            ) {
                error(
                    "Emoji ${emoji.file.name} references " +
                            "missing mask ${emoji.maskId}.png"
                )
            }
        }
    }

    private fun OutputStream.writeUInt16LE(
        value: Int
    ) {
        require(value in 0..0xFFFF)

        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun OutputStream.writeUInt32LE(
        value: Long
    ) {
        require(value in 0..0xFFFFFFFFL)

        write((value and 0xFF).toInt())
        write(((value ushr 8) and 0xFF).toInt())
        write(((value ushr 16) and 0xFF).toInt())
        write(((value ushr 24) and 0xFF).toInt())
    }
}