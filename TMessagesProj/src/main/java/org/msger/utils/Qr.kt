package org.msger.utils

import android.util.Base64
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.tgnet.TLObject
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.CameraScanActivity
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AlertsCreator

object Qr {
    fun openCameraScanActivity(parentFragment: BaseFragment){
        CameraScanActivity.showAsSheet(
            parentFragment,
            false,
            CameraScanActivity.TYPE_QR_LOGIN,
            object : CameraScanActivity.CameraScanActivityDelegate {

                private var response: TLObject? = null
                private var error: TLRPC.TL_error? = null

                override fun didFindQr(link: String) {
                    if (response is TLRPC.TL_authorization) {
                        val authorization = response as TLRPC.TL_authorization
                        if (authorization.password_pending) {
//                            passwordSessions.add(0, authorization)
//                            repeatLoad = 4
//                            loadSessions(false)
                        } else {
//                            sessions.add(0, authorization)
                        }
//                        updateRows()
//                        listAdapter.notifyDataSetChanged()
//                        undoView.showWithAction(0, UndoView.ACTION_QR_SESSION_ACCEPTED, response)
                    } else if (error != null) {
                        AndroidUtilities.runOnUIThread {
                            val text = if (error?.text == "AUTH_TOKEN_EXCEPTION") {
                                LocaleController.getString(R.string.AccountAlreadyLoggedIn)
                            } else {
                                LocaleController.getString(R.string.ErrorOccurred) + "\n" + error?.text
                            }
                            AlertsCreator.showSimpleAlert(
                                parentFragment,
                                LocaleController.getString(R.string.AuthAnotherClient),
                                text
                            )
                        }
                    }
                }

                override fun processQr(link: String, onLoadEnd: Runnable): Boolean {
                    response = null
                    error = null
                    AndroidUtilities.runOnUIThread({
                        try {
                            var code = link.removePrefix("tg://login?token=")
                            code = code.replace("/", "_").replace("+", "-")
                            val token = Base64.decode(code, Base64.URL_SAFE)
                            val req = TLRPC.TL_auth_acceptLoginToken().apply {
                                this.token = token
                            }

                            parentFragment.connectionsManager.sendRequest(req) { res, err ->
                                AndroidUtilities.runOnUIThread {
                                    response = res
                                    error = err
                                    onLoadEnd.run()
                                }
                            }
                        } catch (e: Exception) {
                            AndroidUtilities.runOnUIThread {
                                AlertsCreator.showSimpleAlert(
                                    parentFragment,
                                    LocaleController.getString(R.string.AuthAnotherClient),
                                    LocaleController.getString(R.string.ErrorOccurred)
                                )
                            }
                            onLoadEnd.run()
                        }
                    }, 750)
                    return true
                }
            }
        )
    }
}