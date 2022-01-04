package ua.itaysonlab.catogram.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MessageObject
import java.io.File


class StickerDownloader(val message: MessageObject) {
    fun download(): Pair<String, String> {
        val file = FileLoader.getPathToAttach(message.document, true)
        val stickerPngName = file.name.toString().replace(".webp", ".png")
        val stickerPngPath = file.path.toString().replace(".webp", ".png")
        val stickerBitmap = BitmapFactory.decodeStream(file.inputStream())

        File(stickerPngPath).outputStream().use { out ->
            stickerBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        stickerBitmap.recycle()

        return Pair(stickerPngName, stickerPngPath)
    }
}