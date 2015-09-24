/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.telegram.messenger.volley.toolbox;

import org.telegram.messenger.volley.AuthFailureError;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * An Authenticator that uses {@link AccountManager} to get auth
 * tokens of a specified type for a specified account.
 */
public class AndroidAuthenticator implements Authenticator {
    private final AccountManager mAccountManager;
    private final Account mAccount;
    private final String mAuthTokenType;
    private final boolean mNotifyAuthFailure;

    /**
     * Creates a new authenticator.
     * @param context Context for accessing AccountManager
     * @param account Account to authenticate as
     * @param authTokenType Auth token type passed to AccountManager
     */
    public AndroidAuthenticator(Context context, Account account, String authTokenType) {
        this(context, account, authTokenType, false);
    }

    /**
     * Creates a new authenticator.
     * @param context Context for accessing AccountManager
     * @param account Account to authenticate as
     * @param authTokenType Auth token type passed to AccountManager
     * @param notifyAuthFailure Whether to raise a notification upon auth failure
     */
    public AndroidAuthenticator(Context context, Account account, String authTokenType,
            boolean notifyAuthFailure) {
        this(AccountManager.get(context), account, authTokenType, notifyAuthFailure);
    }

    // Visible for testing. Allows injection of a mock AccountManager.
    AndroidAuthenticator(AccountManager accountManager, Account account,
            String authTokenType, boolean notifyAuthFailure) {
        mAccountManager = accountManager;
        mAccount = account;
        mAuthTokenType = authTokenType;
        mNotifyAuthFailure = notifyAuthFailure;
    }

    /**
     * Returns the Account being used by this authenticator.
     */
    public Account getAccount() {
        return mAccount;
    }

    // TODO: Figure out what to do about notifyAuthFailure
    @SuppressWarnings("deprecation")
    @Override
    public String getAuthToken() throws AuthFailureError {
        AccountManagerFuture<Bundle> future = mAccountManager.getAuthToken(mAccount,
                mAuthTokenType, mNotifyAuthFailure, null, null);
        Bundle result;
        try {
            result = future.getResult();
        } catch (Exception e) {
            throw new AuthFailureError("Error while retrieving auth token", e);
        }
        String authToken = null;
        if (future.isDone() && !future.isCancelled()) {
            if (result.containsKey(AccountManager.KEY_INTENT)) {
                Intent intent = result.getParcelable(AccountManager.KEY_INTENT);
                throw new AuthFailureError(intent);
            }
            authToken = result.getString(AccountManager.KEY_AUTHTOKEN);
        }
        if (authToken == null) {
            throw new AuthFailureError("Got null auth token for type: " + mAuthTokenType);
        }

        return authToken;
    }

    @Override
    public void invalidateAuthToken(String authToken) {
        mAccountManager.invalidateAuthToken(mAccount.type, authToken);
    }
}
