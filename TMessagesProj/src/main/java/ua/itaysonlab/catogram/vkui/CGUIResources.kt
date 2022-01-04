package ua.itaysonlab.catogram.vkui

import android.annotation.SuppressLint
import android.content.res.*
import android.graphics.Movie
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.util.Log
import org.xmlpull.v1.XmlPullParserException
import ua.itaysonlab.catogram.CatogramConfig
import ua.itaysonlab.catogram.vkui.icon_replaces.BaseIconReplace
import java.io.IOException
import java.io.InputStream

@Suppress("DEPRECATION")
class CGUIResources(private val wrapped: Resources) : Resources(wrapped.assets, wrapped.displayMetrics, wrapped.configuration) {
    var activeReplacement: BaseIconReplace = CatogramConfig.getIconReplacement()
    fun reloadReplacements() {
        activeReplacement = CatogramConfig.getIconReplacement()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Throws(NotFoundException::class)
    override fun getDrawable(id: Int): Drawable? {
        return wrapped.getDrawable(activeReplacement.wrap(id))
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Throws(NotFoundException::class)
    override fun getDrawable(id: Int, theme: Theme?): Drawable? {
        return wrapped.getDrawable(activeReplacement.wrap(id), theme)
    }

    @Throws(NotFoundException::class)
    override fun getDrawableForDensity(id: Int, density: Int): Drawable? {
        return wrapped.getDrawableForDensity(activeReplacement.wrap(id), density)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable? {
        return wrapped.getDrawableForDensity(activeReplacement.wrap(id), density, theme)
    }

    private fun logAnId(str: String, id: Int) {
        Log.d("CGUIResources", "[$str] >> id: $id {name: ${getResourceName(id)}}")
    }

    //

    @Throws(NotFoundException::class)
    override fun getText(id: Int): CharSequence {
        return wrapped.getText(id)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Throws(NotFoundException::class)
    override fun getFont(id: Int): Typeface {
        return wrapped.getFont(id)
    }

    @Throws(NotFoundException::class)
    override fun getQuantityText(id: Int, quantity: Int): CharSequence {
        return wrapped.getQuantityText(id, quantity)
    }

    @Throws(NotFoundException::class)
    override fun getString(id: Int): String {
        return wrapped.getString(id)
    }

    @Throws(NotFoundException::class)
    override fun getString(id: Int, vararg formatArgs: Any?): String {
        return wrapped.getString(id, *formatArgs)
    }

    @Throws(NotFoundException::class)
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String {
        return wrapped.getQuantityString(id, quantity, *formatArgs)
    }

    @Throws(NotFoundException::class)
    override fun getQuantityString(id: Int, quantity: Int): String {
        return wrapped.getQuantityString(id, quantity)
    }

    override fun getText(id: Int, def: CharSequence?): CharSequence? {
        return wrapped.getText(id, def)
    }

    @Throws(NotFoundException::class)
    override fun getTextArray(id: Int): Array<CharSequence?> {
        return wrapped.getTextArray(id)
    }

    @Throws(NotFoundException::class)
    override fun getStringArray(id: Int): Array<String?> {
        return wrapped.getStringArray(id)
    }

    @Throws(NotFoundException::class)
    override fun getIntArray(id: Int): IntArray {
        return wrapped.getIntArray(id)
    }

    @Throws(NotFoundException::class)
    override fun obtainTypedArray(id: Int): TypedArray {
        return wrapped.obtainTypedArray(id)
    }

    @Throws(NotFoundException::class)
    override fun getDimension(id: Int): Float {
        return wrapped.getDimension(id)
    }

    @Throws(NotFoundException::class)
    override fun getDimensionPixelOffset(id: Int): Int {
        return wrapped.getDimensionPixelOffset(id)
    }

    @Throws(NotFoundException::class)
    override fun getDimensionPixelSize(id: Int): Int {
        return wrapped.getDimensionPixelSize(id)
    }

    override fun getFraction(id: Int, base: Int, pbase: Int): Float {
        return wrapped.getFraction(id, base, pbase)
    }

    @Throws(NotFoundException::class)
    override fun getMovie(id: Int): Movie? {
        return wrapped.getMovie(id)
    }

    @Throws(NotFoundException::class)
    override fun getColor(id: Int): Int {
        return wrapped.getColor(id)
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Throws(NotFoundException::class)
    override fun getColor(id: Int, theme: Theme?): Int {
        return wrapped.getColor(id, theme)
    }

    @Throws(NotFoundException::class)
    override fun getColorStateList(id: Int): ColorStateList {
        return wrapped.getColorStateList(id)
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Throws(NotFoundException::class)
    override fun getColorStateList(id: Int, theme: Theme?): ColorStateList {
        return wrapped.getColorStateList(id, theme)
    }

    @Throws(NotFoundException::class)
    override fun getBoolean(id: Int): Boolean {
        return wrapped.getBoolean(id)
    }

    @Throws(NotFoundException::class)
    override fun getInteger(id: Int): Int {
        return wrapped.getInteger(id)
    }

    @Throws(NotFoundException::class)
    override fun getLayout(id: Int): XmlResourceParser {
        return wrapped.getLayout(id)
    }

    @Throws(NotFoundException::class)
    override fun getAnimation(id: Int): XmlResourceParser {
        return wrapped.getAnimation(id)
    }

    @Throws(NotFoundException::class)
    override fun getXml(id: Int): XmlResourceParser {
        return wrapped.getXml(id)
    }

    @Throws(NotFoundException::class)
    override fun openRawResource(id: Int): InputStream {
        return wrapped.openRawResource(id)
    }

    @Throws(NotFoundException::class)
    override fun openRawResource(id: Int, value: TypedValue?): InputStream {
        return wrapped.openRawResource(id, value)
    }

    @Throws(NotFoundException::class)
    override fun openRawResourceFd(id: Int): AssetFileDescriptor? {
        return wrapped.openRawResourceFd(id)
    }

    @Throws(NotFoundException::class)
    override fun getValue(id: Int, outValue: TypedValue?, resolveRefs: Boolean) {
        wrapped.getValue(id, outValue, resolveRefs)
    }

    @Throws(NotFoundException::class)
    override fun getValueForDensity(id: Int, density: Int, outValue: TypedValue?, resolveRefs: Boolean) {
        wrapped.getValueForDensity(id, density, outValue, resolveRefs)
    }

    @Throws(NotFoundException::class)
    override fun getValue(name: String?, outValue: TypedValue?, resolveRefs: Boolean) {
        wrapped.getValue(name, outValue, resolveRefs)
    }

    override fun updateConfiguration(config: Configuration?, metrics: DisplayMetrics?) {
        wrapped?.updateConfiguration(config, metrics)
    }

    override fun getDisplayMetrics(): DisplayMetrics? {
        return wrapped.displayMetrics
    }

    override fun getConfiguration(): Configuration? {
        return wrapped.configuration
    }

    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
        return wrapped.getIdentifier(name, defType, defPackage)
    }

    @Throws(NotFoundException::class)
    override fun getResourceName(resid: Int): String? {
        return wrapped.getResourceName(resid)
    }

    @Throws(NotFoundException::class)
    override fun getResourcePackageName(resid: Int): String? {
        return wrapped.getResourcePackageName(resid)
    }

    @Throws(NotFoundException::class)
    override fun getResourceTypeName(resid: Int): String? {
        return wrapped.getResourceTypeName(resid)
    }

    @Throws(NotFoundException::class)
    override fun getResourceEntryName(resid: Int): String? {
        return wrapped.getResourceEntryName(resid)
    }

    @Throws(IOException::class, XmlPullParserException::class)
    override fun parseBundleExtras(parser: XmlResourceParser?, outBundle: Bundle?) {
        wrapped.parseBundleExtras(parser, outBundle)
    }

    @Throws(XmlPullParserException::class)
    override fun parseBundleExtra(tagName: String?, attrs: AttributeSet?, outBundle: Bundle?) {
        wrapped.parseBundleExtra(tagName, attrs, outBundle)
    }

    override fun obtainAttributes(set: AttributeSet?, attrs: IntArray?): TypedArray {
        return wrapped.obtainAttributes(set, attrs)
    }
}