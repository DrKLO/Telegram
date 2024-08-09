package org.xatirchi.ui.delete_menu

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.view.LayoutInflater
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.DeleteMsgBinding
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.xatirchi.utils.DeletedMsg
import org.xatirchi.utils.LanguageCode

class DeleteMsg : BaseFragment() {

    lateinit var binding: DeleteMsgBinding
    lateinit var doneButton: View
    var selectedType = DeletedMsg.getCheckType()

    override fun createView(context: Context?): View {
        binding = DeleteMsgBinding.inflate(LayoutInflater.from(context))

        setActionBar()

        setData()

        binding.radio1.setOnClickListener {
            updateDoneButton(DeletedMsg.SIMPLE)
        }

        binding.radio2.setOnClickListener {
            updateDoneButton(DeletedMsg.YOU)
        }

        binding.radio3.setOnClickListener {
            updateDoneButton(DeletedMsg.I)
        }

        binding.radio4.setOnClickListener {
            updateDoneButton(DeletedMsg.YOU_AND_I)
        }

        return binding.root
    }

    private fun setActionBar() {
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(LanguageCode.getMyTitles(19))
        var backDrawable = BackDrawable(false)
        backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        actionBar.backButtonDrawable = BackDrawable(false).also { backDrawable = it }

        actionBar.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id != -1) {
                    showDialogApply()
                } else {
                    if (DeletedMsg.getCheckType() == selectedType) {
                        finishFragment()
                    } else {
                        discardData()
                    }
                }
            }
        })

        val menu = actionBar.createMenu()
        doneButton = menu.addItemWithWidth(
            1,
            R.drawable.ic_ab_done,
            AndroidUtilities.dp(56f),
            LocaleController.getString("Done", R.string.Done)
        )
        val hasChanges = false
        doneButton.setAlpha(if (hasChanges) 1.0f else 0.0f)
        doneButton.setScaleX(if (hasChanges) 1.0f else 0.0f)
        doneButton.setScaleY(if (hasChanges) 1.0f else 0.0f)
        doneButton.setEnabled(hasChanges)
    }

    override fun onBackPressed(): Boolean {
        if (DeletedMsg.getCheckType() == selectedType) {
            return true
        } else {
            discardData()
            return false
        }
    }

    private fun discardData() {
        val builder = AlertDialog.Builder(
            parentActivity
        )
        builder.setTitle("Xatirchi")
        builder.setMessage(LanguageCode.getMyTitles(39))
        builder.setPositiveButton(
            LocaleController.getString("ApplyTheme", R.string.ApplyTheme)
        ) { dialogInterface: DialogInterface?, i: Int ->
            DeletedMsg.saveCheckType(selectedType)
            finishFragment()
        }
        builder.setNegativeButton(
            LocaleController.getString("PassportDiscard", R.string.PassportDiscard)
        ) { dialog: DialogInterface?, which: Int -> finishFragment() }
        showDialog(builder.create())
    }

    private fun showDialogApply() {
        val builder = AlertDialog.Builder(
            parentActivity
        )
        builder.setMessage(LanguageCode.getMyTitles(40))
        builder.setTitle("Xatirchi")
        builder.setPositiveButton(
            LocaleController.getString("OK", R.string.OK)
        ) { dialogInterface: DialogInterface?, i: Int ->
            DeletedMsg.saveCheckType(selectedType)
            finishFragment()
        }
        builder.setNegativeButton(
            LocaleController.getString("Cancel", R.string.Cancel),
            null
        )
        showDialog(builder.create())
    }

    private fun updateDoneButton(type: Int) {
        var enabled = false
        if (DeletedMsg.getCheckType() != type) {
            enabled = true
        }
        doneButton.isEnabled = enabled
        doneButton.animate().alpha(if (enabled) 1.0f else 0.0f)
            .scaleX(if (enabled) 1.0f else 0.0f).scaleY(if (enabled) 1.0f else 0.0f)
            .setDuration(180).start()
        selectedType = type

        var check1 = false
        var check2 = false
        var check3 = false
        var check4 = false

        when (type) {
            DeletedMsg.SIMPLE -> {
                check1 = true
            }

            DeletedMsg.YOU -> {
                check2 = true
            }

            DeletedMsg.I -> {
                check3 = true
            }

            DeletedMsg.YOU_AND_I -> {
                check4 = true
            }
        }

        binding.apply {
            radio1.isChecked = check1
            radio2.isChecked = check2
            radio3.isChecked = check3
            radio4.isChecked = check4
        }

    }

    @SuppressLint("NewApi")
    private fun setData() {
        binding.simpleDeleteTv.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
        binding.yourDeleteTv.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
        binding.iDeleteTv.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
        binding.iAndYourDeleteTv.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
        binding.tv1Deck.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
        binding.tv2Deck.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
        binding.tv3Deck.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
        binding.tv4Deck.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))

        binding.simpleDeleteTv.text = LanguageCode.getMyTitles(21)
        binding.yourDeleteTv.text = LanguageCode.getMyTitles(31)
        binding.iDeleteTv.text = LanguageCode.getMyTitles(17)
        binding.iAndYourDeleteTv.text = LanguageCode.getMyTitles(15)
        binding.tv1Deck.text = LanguageCode.getMyTitles(22)
        binding.tv2Deck.text = LanguageCode.getMyTitles(20)
        binding.tv3Deck.text = LanguageCode.getMyTitles(18)
        binding.tv4Deck.text = LanguageCode.getMyTitles(15)

        when (DeletedMsg.getCheckType()) {
            DeletedMsg.SIMPLE -> {
                binding.radio1.isChecked = true
            }

            DeletedMsg.YOU -> {
                binding.radio2.isChecked = true
            }

            DeletedMsg.I -> {
                binding.radio3.isChecked = true
            }

            DeletedMsg.YOU_AND_I -> {
                binding.radio4.isChecked = true
            }
        }
    }
}