package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ShortcutResultReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final int currentAccount = intent.getIntExtra("account", UserConfig.selectedAccount);
        final String req_id = intent.getStringExtra("req_id");

        Utilities.Callback<Boolean> callback = MediaDataController.getInstance(currentAccount).shortcutCallbacks.remove(req_id);
        if (callback != null) {
            AndroidUtilities.runOnUIThread(() -> {
                callback.run(true);
            });
        }
    }

}
