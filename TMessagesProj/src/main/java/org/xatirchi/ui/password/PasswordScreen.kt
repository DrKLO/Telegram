package org.xatirchi.ui.password

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.databinding.PasswordScreenBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.xatirchi.utils.Language
import org.xatirchi.utils.LanguageCode
import org.xatirchi.utils.MyPassword
import org.xatirchi.utils.Password
import org.xatirchi.utils.Password.checkPassword

class PasswordScreen(var dialogId: Long, var userName: String) : BaseFragment() {

    lateinit var binding: PasswordScreenBinding
    private var changeStatus = false
    private var oldPassword: MyPassword? = null

    override fun createView(context: Context?): View {
        binding = PasswordScreenBinding.inflate(LayoutInflater.from(context))

        setActionBar()

        binding.root.setOnClickListener {

        }

        binding.userName.text = userName
        binding.newPass1.hint = LanguageCode.getMyTitles(13)
        binding.newPass2.hint = LanguageCode.getMyTitles(13)
        binding.save.text = LanguageCode.getMyTitles(7)
        binding.pass1.hint = LanguageCode.getMyTitles(11)
        binding.next.text = LanguageCode.getMyTitles(4)
        binding.changePassword.text = LanguageCode.getMyTitles(9)
        binding.removePassword.text = LanguageCode.getMyTitles(10)
        binding.userName.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))

        binding.userInfo.text = if (LanguageCode.languageCode == "uz"){
            "$userName uchun kirish kodini kiriting\n! Eslatma ushbu parol faqat\n$userName uchun amal qiladi"
        }else if (LanguageCode.languageCode == "ru"){
            "Введите код доступа для $userName!\n Примечание: этот пароль действителен\nтолько для $userName"
        }else{
            "Enter the access code for $userName \nNote: This password is only valid for $userName"
        }
        binding.userInfo.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))

        if (Password.myPassword(dialogId)) {
            binding.passwordMenu.visibility = View.VISIBLE
        } else {
            binding.createPassword.visibility = View.VISIBLE
        }

        binding.changePassword.setOnClickListener {
            changeStatus = true
            binding.passwordMenu.visibility = View.GONE
            binding.openPassword.visibility = View.VISIBLE
            binding.next.text = LanguageCode.getMyTitles(4)
        }

        binding.removePassword.setOnClickListener {
            changeStatus = false
            binding.passwordMenu.visibility = View.GONE
            binding.openPassword.visibility = View.VISIBLE
            binding.next.text = LanguageCode.getMyTitles(10)
        }

        binding.next.setOnClickListener {
            val enteredPassword: String = binding.pass1.text.toString()
            if (enteredPassword !== "") {
                if (checkPassword(MyPassword(enteredPassword, dialogId)) || Password.checkTwoStepPassword(enteredPassword)) {
                    if (changeStatus) {
                        binding.createPassword.visibility = View.VISIBLE
                        binding.openPassword.visibility = View.GONE
                        binding.save.text = LanguageCode.getMyTitles(6)
                        oldPassword = MyPassword(enteredPassword, dialogId)
                    } else {
                        Password.removePassword(MyPassword(enteredPassword, dialogId))
                        Toast.makeText(context, LanguageCode.getMyTitles(35), Toast.LENGTH_LONG).show()
                        finishFragment()
                    }
                } else {
                    Toast.makeText(context, LanguageCode.getMyTitles(36), Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.save.setOnClickListener {
            val pass1 = binding.newPass1.text.toString()
            val pass2 = binding.newPass2.text.toString()
            if (pass1 != "" && pass2 != "") {
                if (pass1 == pass2) {
                    val newPassword = MyPassword(pass1, dialogId)
                    if (changeStatus) {
                        if (oldPassword != null) {
                            Password.changePassword(oldPassword!!, newPassword)
                            Toast.makeText(context, LanguageCode.getMyTitles(37), Toast.LENGTH_LONG).show()
                            finishFragment()
                        }
                    } else {
                        Password.createPassword(newPassword)
                        Toast.makeText(context, LanguageCode.getMyTitles(38), Toast.LENGTH_LONG).show()
                        finishFragment()
                    }
                }
            }
        }

        return binding.root
    }

    private fun back(): Boolean {
        if (!Password.myPassword(dialogId)) {
            return true
        } else if (binding.createPassword.visibility == View.VISIBLE || binding.openPassword.visibility == View.VISIBLE) {
            binding.createPassword.visibility = View.GONE
            binding.openPassword.visibility = View.GONE
            binding.passwordMenu.visibility = View.VISIBLE
            binding.pass1.setText("")
            binding.newPass1.setText("")
            binding.newPass2.setText("")
            changeStatus = false
            AndroidUtilities.hideKeyboard(binding.root)
            return false
        } else {
            return true
        }
    }

    override fun onBackPressed(): Boolean {
        return back()
    }

    private fun setActionBar() {
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(LanguageCode.getMyTitles(8))
        var backDrawable = BackDrawable(false)
        backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        actionBar.backButtonDrawable = BackDrawable(false).also { backDrawable = it }

        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (back()) {
                    finishFragment()
                }
            }
        })
    }

    override fun onFragmentDestroy() {
        binding.passwordMenu.visibility = View.GONE
        binding.openPassword.visibility = View.GONE
        binding.createPassword.visibility = View.GONE
        binding.root.visibility = View.GONE
        super.onFragmentDestroy()
    }
}