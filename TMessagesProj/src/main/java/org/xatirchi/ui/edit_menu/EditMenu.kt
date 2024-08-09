package org.xatirchi.ui.edit_menu

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.EditMenuBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.xatirchi.utils.EditMessage
import org.xatirchi.utils.GhostVariable
import org.xatirchi.utils.LanguageCode

class EditMenu : BaseFragment() {

    lateinit var binding: EditMenuBinding
    lateinit var doneButton: View
    var selectedType = GhostVariable.ghostMode

    override fun createView(context: Context?): View {
        binding = EditMenuBinding.inflate(LayoutInflater.from(context))

        setActionBar()

        setData()

        binding.editRadio1.setOnClickListener {
            binding.editRadio2.isChecked = false
            updateDoneButton(true)
        }

        binding.editRadio2.setOnClickListener {
            binding.editRadio1.isChecked = false
            updateDoneButton(false)
        }

        return binding.root
    }

    private fun setActionBar() {
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(LanguageCode.getMyTitles(30))
        var backDrawable = BackDrawable(false)
        backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        actionBar.backButtonDrawable = BackDrawable(false).also { backDrawable = it }

        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id != -1) {
                    showDialogApply()
                } else {
                    if (EditMessage.editMode == selectedType) {
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
        if (EditMessage.editMode == selectedType) {
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
            EditMessage.changeEditMode(selectedType)
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
            EditMessage.changeEditMode(selectedType)
            finishFragment()
        }
        builder.setNegativeButton(
            LocaleController.getString("Cancel", R.string.Cancel),
            null
        )
        showDialog(builder.create())
    }

    private fun updateDoneButton(mode: Boolean) {
        var enabled = false
        if (EditMessage.editMode != mode) {
            enabled = true
        }
        doneButton.isEnabled = enabled
        doneButton.animate().alpha(if (enabled) 1.0f else 0.0f)
            .scaleX(if (enabled) 1.0f else 0.0f).scaleY(if (enabled) 1.0f else 0.0f)
            .setDuration(180).start()
        selectedType = mode
    }

    @SuppressLint("NewApi")
    private fun setData() {
        binding.editOn.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
        binding.editOn.setText(LanguageCode.getMyTitles(24))
        binding.editOff.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
        binding.editOff.setText(LanguageCode.getMyTitles(23))

        if (EditMessage.editMode) {
            binding.editRadio1.isChecked = true
        } else {
            binding.editRadio2.isChecked = true
        }
    }

}