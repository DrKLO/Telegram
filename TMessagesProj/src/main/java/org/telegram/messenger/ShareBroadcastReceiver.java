/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ShareBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String url = intent.getDataString();
        if (url != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, url);
            Intent chooserIntent = Intent.createChooser(shareIntent, LocaleController.getString("ShareLink", R.string.ShareLink));
            chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooserIntent);
        }
    }
}
