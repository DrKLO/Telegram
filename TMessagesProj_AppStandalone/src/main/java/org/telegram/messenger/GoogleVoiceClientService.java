/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.search.verification.client.SearchActionVerificationClientService;

public class GoogleVoiceClientService extends SearchActionVerificationClientService {

    @Override
    public void performAction(Intent intent, boolean isVerified, Bundle options) {
        AndroidUtilities.googleVoiceClientService_performAction(intent, isVerified, options);
    }
}
