package ua.itaysonlab.redesign.slides

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.*
import org.telegram.ui.*
import org.telegram.ui.ActionBar.Theme
import ua.itaysonlab.extras.CatogramExtras
import ua.itaysonlab.redesign.BaseActionedSwipeFragment

class DrawerSheetFragment : BaseActionedSwipeFragment() {
    override fun getActions(): List<Action> {
        return mutableListOf<Action>().apply {
            add(Action("contacts", R.drawable.menu_contacts, LocaleController.getString("Contacts", R.string.Contacts)))
            add(Action("calls", R.drawable.menu_calls, LocaleController.getString("Calls", R.string.Calls)))
            add(Action("saved", R.drawable.menu_saved_cg, LocaleController.getString("SavedMessages", R.string.SavedMessages)))
            add(Action("archive", R.drawable.msg_archive, LocaleController.getString("ArchivedChats", R.string.ArchivedChats)))
            add(Action("settings", R.drawable.menu_settings, LocaleController.getString("Settings", R.string.Settings)))
        }
    }

    override fun processActionClick(id: String) {
        when (id) {
            "contacts" -> {
                (activity as LaunchActivity).presentFragment(ContactsActivity(null))
            }
            "calls" -> {
                (activity as LaunchActivity).presentFragment(CallLogActivity())
            }
            "saved" -> {
                (activity as LaunchActivity).presentFragment(ChatActivity(Bundle().apply {
                    putInt("user_id",
                        UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId().toInt()
                    )
                }))
            }
            "archive" -> {
                (activity as LaunchActivity).presentFragment(DialogsActivity(Bundle().apply {
                    putInt("folderId", 1)
                }))
            }
            "settings" -> {
                val args = Bundle()
                args.putInt("user_id",
                    UserConfig.getInstance(UserConfig.selectedAccount).clientUserId.toInt()
                )
                args.putBoolean("expandPhoto", true)
                (activity as LaunchActivity).presentFragment(ProfileActivity(args))
            }
        }
        dismiss()
    }

    override fun getHeader(parent: ViewGroup, ctx: Context): View {
        val user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId())

        return FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(200f))
            addView(createBackground(ctx))
            addView(LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)

                setBackgroundResource(R.drawable.alpha_gradient)
                backgroundTintList = ColorStateList.valueOf(Theme.getColor(Theme.key_windowBackgroundWhite))

                setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(16f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f))

                gravity = Gravity.BOTTOM

                orientation = LinearLayout.VERTICAL

                addView(TextView(ctx).apply {
                    text = user.first_name + if (user.last_name != null) " ${user.last_name}" else ""
                    textSize = AndroidUtilities.dp(8f).toFloat()
                    setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                })

                addView(TextView(ctx).apply {
                    text = "@${user.username}"
                    textSize = AndroidUtilities.dp(4f).toFloat()
                    setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                })
            })
        }
    }

    private fun createBackground(ctx: Context): View {
        return ImageView(ctx).apply {
            setImageDrawable(CatogramExtras.currentAccountBitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }
}