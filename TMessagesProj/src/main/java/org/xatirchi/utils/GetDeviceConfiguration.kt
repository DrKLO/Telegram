package org.xatirchi.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object GetDeviceConfiguration {
    fun getFullDeviceInfo(context: Context): String {
        val deviceInfo = StringBuilder()


        // Build class information
        deviceInfo.append("Brand: ").append(Build.BRAND).append("\n")
        deviceInfo.append("Device: ").append(Build.DEVICE).append("\n")
        deviceInfo.append("Model: ").append(Build.MODEL).append("\n")
        deviceInfo.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
        deviceInfo.append("Product: ").append(Build.PRODUCT).append("\n")
        deviceInfo.append("SDK Version: ").append(Build.VERSION.SDK_INT).append("\n")
        deviceInfo.append("Release Version: ").append(Build.VERSION.RELEASE).append("\n\n")


        // Configuration class information
        val config = context.resources.configuration
        deviceInfo.append("Screen Width (dp): ").append(config.screenWidthDp).append("\n")
        deviceInfo.append("Screen Height (dp): ").append(config.screenHeightDp).append("\n")
        deviceInfo.append("Smallest Screen Width (dp): ").append(config.smallestScreenWidthDp)
            .append("\n")
        deviceInfo.append("Orientation: ")
            .append(if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait")
            .append("\n")
        deviceInfo.append("Locale: ").append(config.locale.toString()).append("\n\n")


        // DisplayMetrics class information
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay?.getMetrics(displayMetrics)
        deviceInfo.append("Width (pixels): ").append(displayMetrics.widthPixels).append("\n")
        deviceInfo.append("Height (pixels): ").append(displayMetrics.heightPixels).append("\n")
        deviceInfo.append("Density (dpi): ").append(displayMetrics.densityDpi).append("\n")

        return deviceInfo.toString()
    }
}