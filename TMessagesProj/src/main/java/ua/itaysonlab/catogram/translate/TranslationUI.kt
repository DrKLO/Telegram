package ua.itaysonlab.catogram.translate

import android.widget.LinearLayout
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet

object TranslationUI {
    fun callTranslationBottomSheet(fragment: BaseFragment) {
        val builder = BottomSheet.Builder(fragment.parentActivity)

        builder.apply {
            setTitle("Translator")

            val linearLayout = LinearLayout(fragment.parentActivity)
            linearLayout.orientation = LinearLayout.VERTICAL
            //linearLayout.addView()

            customView = linearLayout
        }
    }
}