package org.telegram.messenger.voip;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

/**
 * Created by grishka on 09.01.2018.
 */

@TargetApi(Build.VERSION_CODES.O)
public class TelegramConnectionService extends ConnectionService {

	@Override
	public void onCreate() {
		super.onCreate();
		if (BuildVars.LOGS_ENABLED) {
			FileLog.w("ConnectionService created");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (BuildVars.LOGS_ENABLED) {
			FileLog.w("ConnectionService destroyed");
		}
	}

	@Override
	public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("onCreateIncomingConnection "/*+request*/);
		}
		Bundle extras = request.getExtras();
		if (extras.getInt("call_type") == 1) { // private
			VoIPService svc = VoIPService.getSharedInstance();
			if (svc == null) {
				return null;
			}
			if (svc.isOutgoing()) {
				return null;
			}
			return svc.getConnectionAndStartCall();
		} else if (extras.getInt("call_type") == 2) { // group
			/*VoIPGroupService svc=VoIPGroupService.getSharedInstance();
			if(svc==null)
				return null;
			return svc.getConnectionAndStartCall();*/
		}
		return null;
	}

	@Override
	public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.e("onCreateIncomingConnectionFailed "/*+request*/);
		}
		if (VoIPService.getSharedInstance() != null) {
			VoIPService.getSharedInstance().callFailedFromConnectionService();
		}
	}

	@Override
	public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.e("onCreateOutgoingConnectionFailed "/*+request*/);
		}
		if (VoIPService.getSharedInstance() != null) {
			VoIPService.getSharedInstance().callFailedFromConnectionService();
		}
	}

	@Override
	public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
		if (BuildVars.LOGS_ENABLED)
			FileLog.d("onCreateOutgoingConnection "/*+request*/);
		Bundle extras = request.getExtras();
		if (extras.getInt("call_type") == 1) { // private
			VoIPService svc = VoIPService.getSharedInstance();
			if (svc == null) {
				return null;
			}
			return svc.getConnectionAndStartCall();
		} else if (extras.getInt("call_type") == 2) { // group
			/*VoIPGroupService svc=VoIPGroupService.getSharedInstance();
			if(svc==null)
				return null;
			if(!svc.isOutgoing())
				return null;
			return svc.getConnectionAndStartCall();*/
		}
		return null;
	}
}
