package org.telegram.messenger;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import org.telegram.ui.LaunchActivity;
import org.telegram.ui.WearAuthSheet;

public class WearAuthListenerService extends WearableListenerService {

    public static final String PATH_OFFER = "/tg-wear-auth/offer";
    public static final String PATH_CANCEL = "/tg-wear-auth/cancel";

    @Override
    public void onMessageReceived(@NonNull MessageEvent event) {
        final String path = event.getPath();
        final String node = event.getSourceNodeId();
        final byte[] data = event.getData();
        if (PATH_OFFER.equals(path)) {
            try {
                Intent intent = new Intent(this, LaunchActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            } catch (Exception e) {
                FileLog.e("wear-auth: failed to pop LaunchActivity", e);
            }
        }
        AndroidUtilities.runOnUIThread(() -> {
            switch (path) {
                case PATH_OFFER:
                    FileLog.d("wear-auth: offer from " + node + " (" + data.length + " bytes)");
                    WearAuthSheet.onOfferReceived(data, node);
                    break;
                case PATH_CANCEL:
                    FileLog.d("wear-auth: cancel from " + node);
                    WearAuthSheet.onCancelReceived();
                    break;
                default:
                    FileLog.d("wear-auth: unexpected path " + path);
                    break;
            }
        });
    }
}
