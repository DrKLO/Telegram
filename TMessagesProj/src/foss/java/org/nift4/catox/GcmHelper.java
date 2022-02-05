package org.nift4.catox;

import android.content.Context;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;

public class GcmHelper {
	public static void initPlayServices(Context c) {
		SharedConfig.pushStringStatus = "__NO_GOOGLE_PLAY_SERVICES__";
		ApplicationLoader.hasPlayServices = checkPlayServices(c);
	}

	private static boolean checkPlayServices(Context c) {
		return false;
	}

	public static void sendRegistrationToServer(String pushString) {}
}
