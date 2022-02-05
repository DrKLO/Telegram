package org.nift4.catox;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.messaging.FirebaseMessaging;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GcmPushListenerService;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

public class GcmHelper {
	public static void initPlayServices(Context c) {
		AndroidUtilities.runOnUIThread(() -> {
			if (ApplicationLoader.hasPlayServices = checkPlayServices(c)) {
				final String currentPushString = SharedConfig.pushString;
				if (!TextUtils.isEmpty(currentPushString)) {
					if (BuildVars.DEBUG_PRIVATE_VERSION && BuildVars.LOGS_ENABLED) {
						FileLog.d("GCM regId = " + currentPushString);
					}
				} else {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.d("GCM Registration not found.");
					}
				}
				Utilities.globalQueue.postRunnable(() -> {
					try {
						SharedConfig.pushStringGetTimeStart = SystemClock.elapsedRealtime();
						FirebaseMessaging.getInstance().getToken()
								.addOnCompleteListener(task -> {
									SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
									if (!task.isSuccessful()) {
										if (BuildVars.LOGS_ENABLED) {
											FileLog.d("Failed to get regid");
										}
										SharedConfig.pushStringStatus = "__FIREBASE_FAILED__";
										GcmPushListenerService.sendRegistrationToServer(null);
										return;
									}
									String token = task.getResult();
									if (!TextUtils.isEmpty(token)) {
										GcmPushListenerService.sendRegistrationToServer(token);
									}
								});
					} catch (Throwable e) {
						FileLog.e(e);
					}
				});
			} else {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.d("No valid Google Play Services APK found.");
				}
				SharedConfig.pushStringStatus = "__NO_GOOGLE_PLAY_SERVICES__";
				GcmPushListenerService.sendRegistrationToServer(null);
			}
		}, 1000);
	}

	private static boolean checkPlayServices(Context c) {
		try {
			int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c);
			return resultCode == ConnectionResult.SUCCESS;
		} catch (Exception e) {
			FileLog.e(e);
		}
		return true;
	}

	public static void sendRegistrationToServer(String pushString) {
		GcmPushListenerService.sendRegistrationToServer(pushString);
	}
}
