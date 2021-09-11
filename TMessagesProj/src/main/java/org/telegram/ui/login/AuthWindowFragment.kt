package org.telegram.ui.login

import android.app.DialogFragment
import android.widget.EditText
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.telegram.api.LoginController
import org.telegram.api.LoginListener
import org.telegram.database.User
import org.telegram.messenger.R
import org.json.JSONObject
import org.telegram.database.AppDatabase


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
                            override fun onSuccess(data: User?) {
                                Toast.makeText(
                                    activity,
                                    "Успешно!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                insertUserIntoDatabase(data!!)
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
    private fun insertUserIntoDatabase(data: User) {
        GlobalScope.launch {
            AppDatabase(activity).userDao().insertAll(User(id = data.id, createdAt = data.createdAt, email = data.email, partnerId = data.partnerId, updatedAt = data.updatedAt))
        }
    }

} 