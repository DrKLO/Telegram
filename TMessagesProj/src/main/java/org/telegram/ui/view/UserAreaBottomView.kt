package org.telegram.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import org.telegram.messenger.R

class UserAreaBottomView : FrameLayout {

    var textGetSecretKey: TextView? = null
    var textInfo: TextView? = null
    var textSupport: TextView? = null

    constructor(context: Context) : super(context) {
        initializeView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initializeView(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initializeView(context)
    }

    fun setOnGetSecretKeyListener(l: OnClickListener) = textGetSecretKey?.setOnClickListener(l)

    fun setOnInfoListener(l: OnClickListener) = textInfo?.setOnClickListener(l)

    fun setOnSupportListener(l: OnClickListener) = textSupport?.setOnClickListener(l)

    private fun initializeView(context: Context) {
        inflate(context, R.layout.v_user_area_bottom, this)
        textGetSecretKey = findViewById(R.id.text_get_secret_key)
        textInfo = findViewById(R.id.text_info)
        textSupport = findViewById(R.id.text_support)
    }

}