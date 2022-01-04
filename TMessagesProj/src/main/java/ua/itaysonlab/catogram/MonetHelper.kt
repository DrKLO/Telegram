package ua.itaysonlab.catogram

import android.R
import android.content.Context
import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.color.MaterialColors

object MonetHelper {

    @ColorInt
    @JvmStatic
    fun getAccentColor(ctx: Context): Int {
        return reqAttrFromDevice(ctx, R.attr.colorAccent)
    }

    @ColorInt
    @JvmStatic
    fun getBackgroundColor(ctx: Context): Int {
        return reqAttrFromDevice(ctx, R.attr.colorBackground)
    }

    @ColorInt
    private fun reqAttrFromDevice(ctx: Context, @AttrRes attr: Int): Int {
        return MaterialColors.getColor(getCtx(ctx), attr, Color.MAGENTA)
    }

    private fun getCtx(ctx: Context) = ContextThemeWrapper(ctx, R.style.Theme_DeviceDefault_DayNight)

}