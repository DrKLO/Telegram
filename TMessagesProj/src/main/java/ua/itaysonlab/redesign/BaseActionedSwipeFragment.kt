package ua.itaysonlab.redesign

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import org.telegram.messenger.databinding.V5SlideItemBinding
import org.telegram.ui.ActionBar.Theme

abstract class BaseActionedSwipeFragment : BottomSlideFragment() {
    var needToTint = true

    abstract fun getActions(): List<Action>

    abstract fun processActionClick(id: String)

    // null - do not show header
    open fun getHeader(parent: ViewGroup, ctx: Context): View? {
        return null
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

    override fun onCreateView(parent: ViewGroup): View {
        return LinearLayout(parent.context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))

            orientation = LinearLayout.VERTICAL

            getHeader(this, parent.context)?.let {
                it.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                addView(it)
            }

            addView(getActionsView(parent.context))
        }
    }

    data class Action(
            val id: String,
            @DrawableRes val icon: Int,
            val title: String,
    )
}