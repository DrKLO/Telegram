package ua.itaysonlab.redesign.sheet

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.telegram.messenger.R
import org.telegram.messenger.databinding.V5SlideItemBinding
import org.telegram.ui.ActionBar.Theme
import ua.itaysonlab.redesign.BaseActionedSwipeFragment

abstract class BaseActionedSheetFragment : BottomSheetDialogFragment() {
    var needToTint = true

    abstract fun getActions(): List<BaseActionedSwipeFragment.Action>

    abstract fun processActionClick(id: String)

    // null - do not show header
    open fun getHeader(parent: ViewGroup, ctx: Context): View? {
        return null
    }

    override fun getTheme(): Int {
        return R.style.TransSheet
    }

    private fun getActionsView(ctx: Context): View {
        return LinearLayout(ctx).apply {
            val inflater = LayoutInflater.from(ctx)
            orientation = LinearLayout.VERTICAL

            getActions().forEach { action ->
                addView(V5SlideItemBinding.inflate(inflater, this, false).apply {
                    if (action.icon == -1) {
                        actionIv.visibility = View.GONE
                    } else {
                        actionIv.setImageResource(action.icon)
                        if (needToTint) actionIv.imageTintList = ColorStateList.valueOf(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                    }

                    actionTv.text = action.title
                    actionTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                    root.setOnClickListener {
                        processActionClick(action.id)
                        dismiss()
                    }
                }.root)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return LinearLayout(inflater.context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))

            orientation = LinearLayout.VERTICAL

            getHeader(this, inflater.context)?.let {
                it.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                addView(it)
            }

            addView(getActionsView(inflater.context))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog!!.window!!.navigationBarColor = Theme.getColor(Theme.key_windowBackgroundWhite)
    }

    data class Action(
            val id: String,
            @DrawableRes val icon: Int,
            val title: String,
    )
}