package org.telegram.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import org.telegram.messenger.R

class UserAreaInputView : FrameLayout {

    data class UserAreaInputDto(
        val partnerId: String,
        val rating: String
    )

    var partnerIdInput: EditText? = null
    var ratingInput: EditText? = null
    var voteBtn: Button? = null

    constructor(context: Context) : super(context) {
        initializeView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initializeView(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initializeView(context)
    }

    fun setOnVoteListener(listener: (UserAreaInputDto) -> Unit) {
        voteBtn?.setOnClickListener {
            val partnerId = partnerIdInput?.text.toString()
            val rating = ratingInput?.text.toString()
            val data = UserAreaInputDto(partnerId, rating)
            listener(data)
        }
    }

    private fun initializeView(context: Context) {
        inflate(context, R.layout.v_user_area_input, this)
        partnerIdInput = findViewById(R.id.input_partner_id)
        ratingInput = findViewById(R.id.input_rating)
        voteBtn = findViewById(R.id.btn_vote)
    }

}