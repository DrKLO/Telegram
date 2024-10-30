package org.telegram.messenger.voip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by grishka on 28.07.17.
 */

public class VoIPActionsReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (VoIPService.getSharedInstance() != null) {
			VoIPService.getSharedInstance().handleNotificationAction(intent);
		} else {
			final String packageName = context.getPackageName();
			if ((packageName + ".END_CALL").equals(intent.getAction())) {
				VoIPPreNotificationService.decline(context, VoIPService.DISCARD_REASON_HANGUP);
			} else if ((packageName + ".DECLINE_CALL").equals(intent.getAction())) {
				VoIPPreNotificationService.decline(context, VoIPService.DISCARD_REASON_LINE_BUSY);
			} else if ((packageName + ".ANSWER_CALL").equals(intent.getAction())) {
				VoIPPreNotificationService.answer(context);
			} else if ((packageName + ".HIDE_CALL").equals(intent.getAction())) {
				VoIPPreNotificationService.dismiss(context);
			}
		}
	}
}
