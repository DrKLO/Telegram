package org.xatirchi.ui.two_step_password_screen

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.databinding.TwoStepPasswordScreenBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.xatirchi.ui.password.PasswordScreen
import org.xatirchi.utils.LanguageCode
import org.xatirchi.utils.Password

class TwoStepPasswordScreen(val dialogId: Long, var userName: String) : BaseFragment() {

    lateinit var binding: TwoStepPasswordScreenBinding

    override fun createView(context: Context?): View {
        binding = TwoStepPasswordScreenBinding.inflate(LayoutInflater.from(context))

        binding.userInfo.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))

        setActionBar()

        binding.pass1.hint = LanguageCode.getMyTitles(3)
        binding.next.text = LanguageCode.getMyTitles(4)
        binding.save.text = LanguageCode.getMyTitles(7)
        binding.userInfo.text = LanguageCode.getMyTitles(5)
        binding.newPass1.hint = LanguageCode.getMyTitles(3)
        binding.newPass2.hint = LanguageCode.getMyTitles(3)

        if (Password.getTwoStepPassword() != "") {
            binding.openPassword.visibility = View.VISIBLE
        } else {
            binding.createPassword.visibility = View.VISIBLE
        }

        binding.next.setOnClickListener {
            val enteredPassword: String = binding.pass1.text.toString()
            if (enteredPassword !== "") {
                if (Password.checkTwoStepPassword(enteredPassword)) {
                    binding.createPassword.visibility = View.VISIBLE
                    binding.openPassword.visibility = View.GONE
                    binding.save.text = LanguageCode.getMyTitles(6)
                } else {
                    Toast.makeText(context, LanguageCode.getMyTitles(36), Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.save.setOnClickListener {
            val pass1 = binding.newPass1.text.toString()
            val pass2 = binding.newPass2.text.toString()
            if (pass1 != "" && pass2 != "") {
                if (pass1 == pass2) {
                    Password.createTwoStepPassword(pass1)
                    Toast.makeText(context, LanguageCode.getMyTitles(38), Toast.LENGTH_SHORT).show()
                    finishFragment()
                }
            }
        }

        return binding.root
    }

    private fun setActionBar() {
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(LanguageCode.getMyTitles(2))
        var backDrawable = BackDrawable(false)
        backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        actionBar.backButtonDrawable = BackDrawable(false).also { backDrawable = it }

        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                finishFragment()
            }
        })
    }

    override fun onFragmentDestroy() {
        if (Password.getTwoStepPassword() != "" && dialogId != 0L) {
            presentFragment(PasswordScreen(dialogId, userName))
        } else {
            AndroidUtilities.hideKeyboard(binding.root)
        }
        binding.openPassword.visibility = View.GONE
        binding.createPassword.visibility = View.GONE
        binding.root.visibility = View.GONE
        super.onFragmentDestroy()
    }

}