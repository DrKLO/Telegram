package org.xatirchi.utils

import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException

object SvgUtils {

    fun getDrawableFromSvg(svgString: String?): Drawable? {
        try {
            val svg = SVG.getFromString(svgString)
            return PictureDrawable(svg.renderToPicture())
        } catch (e: SVGParseException) {
            e.printStackTrace()
            return null
        }
    }

}