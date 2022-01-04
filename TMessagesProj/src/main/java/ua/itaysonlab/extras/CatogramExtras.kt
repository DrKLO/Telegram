package ua.itaysonlab.extras

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import ua.itaysonlab.catogram.CatogramConfig.controversiveNoSecureFlag
import ua.itaysonlab.catogram.CatogramConfig.noVibration
import ua.itaysonlab.catogram.CatogramConfig.drawerBlur
import android.graphics.drawable.BitmapDrawable
import android.view.WindowManager
import android.os.Vibrator
import androidx.annotation.ColorInt
import org.telegram.messenger.SharedConfig
import org.telegram.tgnet.TLRPC
import android.view.View
import android.view.Window
import org.telegram.messenger.FileLoader
import org.telegram.messenger.Utilities
import java.io.DataInputStream
import java.io.FileInputStream
import java.lang.Exception


object CatogramExtras {

    var CG_VERSION = "3.9.7"
    @JvmField
    var currentAccountBitmap: BitmapDrawable? = null

    // 80 in official
    @JvmField
    var LOAD_AVATAR_COUNT_HEADER = 100
    var LOAD_AVATAR_COUNT = 100
    fun dip2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    fun px2dip(context: Context, pxValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }

    fun setSecureFlag(window: Window) {
        if (!controversiveNoSecureFlag) window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    fun setSecureFlag(window: WindowManager.LayoutParams) {
        if (!controversiveNoSecureFlag) window.flags =
            window.flags or WindowManager.LayoutParams.FLAG_SECURE
    }

    fun clearSecureFlag(window: Window) {
        if (!controversiveNoSecureFlag) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    @JvmStatic
    fun vibrate(vibrator: Vibrator, ms: Int) {
        if (noVibration) return
        vibrator.vibrate(ms.toLong())
    }

    @JvmStatic
    fun vibrate(vibrator: Vibrator, pattern: LongArray?, repeat: Int) {
        if (noVibration) return
        vibrator.vibrate(pattern, repeat)
    }

    @JvmStatic
    fun performHapticFeedback(view: View, feedbackConstant: Int, flags: Int): Boolean {
        return if (noVibration) false else view.performHapticFeedback(
            feedbackConstant,
            flags
        )
    }

    @JvmStatic
    fun performHapticFeedback(view: View, feedbackConstant: Int): Boolean {
        return if (noVibration) false else view.performHapticFeedback(feedbackConstant)
    }

    @JvmStatic
    @get:ColorInt
    val lightStatusbarColor: Int
        get() = if (SharedConfig.noStatusBar) {
            0x00000000
        } else {
            0x0f000000
        }

    @JvmStatic
    @get:ColorInt
    val darkStatusbarColor: Int
        get() = if (SharedConfig.noStatusBar) {
            0x00000000
        } else {
            0x33000000
        }

    @JvmStatic
    fun setAccountBitmap(user: TLRPC.User) {
        if (user.photo != null) {
            try {
                val photo = FileLoader.getPathToAttach(user.photo.photo_big, true)
                val photoData = ByteArray(photo.length().toInt())
                val photoIn: FileInputStream
                photoIn = FileInputStream(photo)
                DataInputStream(photoIn).readFully(photoData)
                photoIn.close()
                var bg = BitmapFactory.decodeByteArray(photoData, 0, photoData.size)
                if (drawerBlur) bg = Utilities.blurWallpaper(bg)
                currentAccountBitmap = BitmapDrawable(Resources.getSystem(), bg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun darkenBitmap(bm: Bitmap): Bitmap {
        val canvas = Canvas(bm)
        val p = Paint(Color.RED)
        val filter: ColorFilter = LightingColorFilter(-0x808081, 0x00000000) // darken
        p.colorFilter = filter
        canvas.drawBitmap(bm, Matrix(), p)
        return bm
    }

    fun wrapEmoticon(base: String?): String {
        return if (base == null) {
            "\uD83D\uDCC1"
        } else if (base.isEmpty()) {
            //Log.d("CG-Test", Arrays.toString(base.getBytes(StandardCharsets.UTF_16BE)));
            "\uD83D\uDDC2"
        } else {
            //Log.d("CG-Test", Arrays.toString(base.getBytes(StandardCharsets.UTF_16BE)));
            base
        }
    }
}