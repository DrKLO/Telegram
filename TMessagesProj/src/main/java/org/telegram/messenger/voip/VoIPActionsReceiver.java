package org.telegram.messenger.voip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by grishka on 28.07.17.
 */

public class VoIPActionsReceiver extends BroadcastReceiver{
	@Override
	public void onReceive(Context context, Intent intent){
		if(VoIPBaseService.getSharedInstance()!=null)
			VoIPBaseService.getSharedInstance().handleNotificationAction(intent);
	}
}
