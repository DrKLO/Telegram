package org.telegram.ui.login

import android.app.DialogFragment
import android.widget.EditText
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.telegram.api.LoginController
import org.telegram.api.LoginListener
import org.telegram.messenger.R
import android.content.Context.MODE_PRIVATE

import android.content.SharedPreferences





class AuthWindowFragment : DialogFragment() {
    var emailEditText: EditText? = null
    var auth: Button? = null
    var root: View? = null
    fun newInstance(): AuthWindowFragment {
        return AuthWindowFragment()
    }
    override fun onResume() {
        super.onResume()
        val params: ViewGroup.LayoutParams = dialog.window!!.attributes
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        dialog.window!!.attributes = params as WindowManager.LayoutParams
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        root = inflater.inflate(R.layout.window_auth, container, false)
        emailEditText = root?.findViewById(R.id.auth_email)
        auth = root?.findViewById(R.id.auth)
        auth?.setOnClickListener { view: View? ->
            if (emailEditText?.text.toString().trim().isEmpty()) {
                Toast.makeText(activity, "Введите E-mail!", Toast.LENGTH_SHORT).show()
            } else {
                val data = LoginPuttingData(emailEditText?.text.toString().trim())
                LoginController(activity).authUser(
                    data,
                        object : LoginListener {
                            override fun onSuccess(data: LoginData?) {
                                Toast.makeText(
                                    activity,
                                    "Успешно!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                saveId(data!!)
                            }

                            override fun onFailure(message: String) {
                                if (message.isEmpty()) {
                                    Toast.makeText(
                                        activity,
                                        "Произошла ошибка!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        activity,
                                        "Произошла ошибка: $message",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                            }
                        })
            }
        }
        return root
    }
    private fun saveId(data: LoginData) {
        val editor: SharedPreferences.Editor = activity.getSharedPreferences("user", MODE_PRIVATE).edit()
        editor.putString("id", data.id)
        editor.apply()
    }

} 