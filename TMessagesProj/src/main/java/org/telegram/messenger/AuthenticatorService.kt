/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger

import android.accounts.*
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

class AuthenticatorService : Service() {

    private class Authenticator(context: Context) : AbstractAccountAuthenticator(context) {
        @Throws(NetworkErrorException::class)
        override fun addAccount(
            response: AccountAuthenticatorResponse,
            accountType: String,
            authTokenType: String,
            requiredFeatures: Array<String>,
            options: Bundle
        ): Bundle? {
            return null
        }

        @Throws(NetworkErrorException::class)
        override fun getAccountRemovalAllowed(
            response: AccountAuthenticatorResponse,
            account: Account
        ): Bundle {
            return super.getAccountRemovalAllowed(response, account)
        }

        @Throws(NetworkErrorException::class)
        override fun confirmCredentials(
            response: AccountAuthenticatorResponse,
            account: Account,
            options: Bundle
        ): Bundle? {
            return null
        }

        override fun editProperties(
            response: AccountAuthenticatorResponse,
            accountType: String
        ): Bundle? {
            return null
        }

        @Throws(NetworkErrorException::class)
        override fun getAuthToken(
            response: AccountAuthenticatorResponse,
            account: Account,
            authTokenType: String,
            options: Bundle
        ): Bundle? {
            return null
        }

        override fun getAuthTokenLabel(authTokenType: String): String? {
            return null
        }

        @Throws(NetworkErrorException::class)
        override fun hasFeatures(
            response: AccountAuthenticatorResponse,
            account: Account, features: Array<String>
        ): Bundle? {
            return null
        }

        @Throws(NetworkErrorException::class)
        override fun updateCredentials(
            response: AccountAuthenticatorResponse,
            account: Account,
            authTokenType: String,
            options: Bundle
        ): Bundle? {
            return null
        }
    }

    private val authenticator: Authenticator?
        get() {
            if (Companion.authenticator == null) {
                Companion.authenticator = Authenticator(this)
            }
            return Companion.authenticator
        }

    override fun onBind(intent: Intent): IBinder? {
        return if (intent.action == AccountManager.ACTION_AUTHENTICATOR_INTENT) {
            authenticator!!.iBinder
        } else {
            null
        }
    }

    companion object {
        private var authenticator: Authenticator? = null
    }
}