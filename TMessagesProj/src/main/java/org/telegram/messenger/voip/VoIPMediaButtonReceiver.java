package org.telegram.messenger.voip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/**
 * Created by grishka on 21.11.16.
 */

public class VoIPMediaButtonReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
			if (VoIPService.getSharedInstance() == null) {
				return;
			}
			KeyEvent ev = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			VoIPService.getSharedInstance().onMediaButtonEvent(ev);
		}
	}
}
