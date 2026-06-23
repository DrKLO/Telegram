/*
 * This is the source code of Tajgram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.Tajgram.messenger;

import com.google.android.search.verification.client.SearchActionVerificationClientActivity;
import com.google.android.search.verification.client.SearchActionVerificationClientService;

public class GoogleVoiceClientActivity extends SearchActionVerificationClientActivity {

    public Class<? extends SearchActionVerificationClientService> getServiceClass() {
        return GoogleVoiceClientService.class;
    }
}
