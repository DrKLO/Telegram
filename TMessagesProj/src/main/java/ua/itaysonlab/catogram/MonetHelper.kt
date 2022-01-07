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
    @JvmStatic
    fun getAccentColorDark(ctx: Context): Int {
        return reqAttrFromDeviceDark(ctx, org.telegram.messenger.R.attr.colorPrimaryDark)
    }

    @ColorInt
    @JvmStatic
    fun getBackgroundColorDark(ctx: Context): Int {
        return reqAttrFromDeviceDark(ctx, org.telegram.messenger.R.attr.colorSecondary)
    }

    @ColorInt
    private fun reqAttrFromDevice(ctx: Context, @AttrRes attr: Int): Int {
        return MaterialColors.getColor(getCtx(ctx), attr, Color.MAGENTA)
    }

    @ColorInt
    private fun reqAttrFromDeviceDark(ctx: Context, @AttrRes attr: Int): Int {
        return MaterialColors.getColor(getCtxDark(ctx), attr, Color.MAGENTA)
    }

    private fun getCtx(ctx: Context) = ContextThemeWrapper(ctx, R.style.Theme_DeviceDefault_Light)

    private fun getCtxDark(ctx: Context) = ContextThemeWrapper(ctx, org.telegram.messenger.R.style.Theme_Material3_DynamicColors_Dark)

}