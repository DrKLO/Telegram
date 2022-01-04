package ua.itaysonlab.catogram.translate

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.telegram.messenger.*
import org.telegram.messenger.databinding.V5TranslationMarkupBinding
import org.telegram.ui.ActionBar.Theme
import ua.itaysonlab.catogram.translate.impl.GoogleTranslateImpl

class TranslationSheetFragment(val txt: String) : BottomSheetDialogFragment() {
    private lateinit var vview: V5TranslationMarkupBinding

    override fun getTheme(): Int {
        return R.style.TransSheet
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return LinearLayout(inflater.context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            orientation = LinearLayout.VERTICAL

            vview = V5TranslationMarkupBinding.inflate(inflater, container, false)
            addView(vview.root)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val whiteBg = Theme.getColor(Theme.key_windowBackgroundWhite)
        val blackText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
        val blackColor = ColorStateList.valueOf(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        val grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        val grayBg = ColorStateList.valueOf(Theme.getColor(Theme.key_windowBackgroundGray))

        dialog!!.window!!.navigationBarColor = whiteBg
        vview.root.backgroundTintList = ColorStateList.valueOf(whiteBg)

        vview.close.setOnClickListener {
            dismiss()
        }

        vview.copyText.setOnClickListener {
            AndroidUtilities.addToClipboard(vview.trsl.text.toString())
            Toast.makeText(
                    ApplicationLoader.applicationContext,
                    LocaleController.getString("TextCopied", R.string.TextCopied),
                    Toast.LENGTH_SHORT
            ).show()
            dismiss()
        }

        vview.close.imageTintList = blackColor
        vview.copyText.imageTintList = blackColor

        vview.copyText.visibility = View.GONE
        vview.mkCt.visibility = View.INVISIBLE
        vview.mkLd.visibility = View.VISIBLE

        vview.tvTitle.setTextColor(blackText)
        vview.tvDivider.backgroundTintList = ColorStateList.valueOf(grayColor)

        vview.origTxt.setTextColor(grayColor)
        vview.trslTxt.setTextColor(grayColor)
        vview.origTxtLang.setTextColor(grayColor)
        vview.trslTxtLang.setTextColor(grayColor)

        vview.origTxt.text = LocaleController.getString("CG_Translate_Orig", R.string.CG_Translate_Orig)
        vview.trslTxt.text = LocaleController.getString("CG_Translate_Translated", R.string.CG_Translate_Translated)
        vview.tvTitle.text = LocaleController.getString("CG_Translator", R.string.CG_Translator)

        vview.origCard.backgroundTintList = grayBg
        vview.trslCard.backgroundTintList = grayBg

        vview.orig.setTextColor(blackText)
        vview.trsl.setTextColor(blackText)
        vview.orig.text = txt

        GoogleTranslateImpl.translateText(txt, false) { text ->
            vview.trsl.text = text
            vview.trslTxtLang.text = " • ${LocaleController.getString("LanguageCode", R.string.LanguageCode)}"

            vview.mkCt.visibility = View.VISIBLE
            vview.mkLd.visibility = View.INVISIBLE
            vview.copyText.visibility = View.VISIBLE
        }
    }
}