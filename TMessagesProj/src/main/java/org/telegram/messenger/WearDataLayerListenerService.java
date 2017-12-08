package org.telegram.messenger;

import android.text.TextUtils;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WearDataLayerListenerService extends WearableListenerService {


	@Override
	public void onCreate() {
		super.onCreate();
		FileLog.d("WearableDataLayer service created");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		FileLog.d("WearableDataLayer service destroyed");
	}

	@Override
	public void onChannelOpened(final Channel ch) {
		//new Thread(new Runnable(){
		//	@Override
		//	public void run(){
		GoogleApiClient apiClient = new GoogleApiClient.Builder(WearDataLayerListenerService.this).addApi(Wearable.API).build();
		if (!apiClient.blockingConnect().isSuccess()) {
			FileLog.e("failed to connect google api client");
			return;
		}
		String path = ch.getPath();
		FileLog.d("wear channel path: " + path);
		try {
			if ("/getCurrentUser".equals(path)) {
				DataOutputStream out = new DataOutputStream(new BufferedOutputStream(ch.getOutputStream(apiClient).await().getOutputStream()));
				if (UserConfig.isClientActivated()) {
					final TLRPC.User user = UserConfig.getCurrentUser();
					out.writeInt(user.id);
					out.writeUTF(user.first_name);
					out.writeUTF(user.last_name);
					out.writeUTF(user.phone);
					if (user.photo != null) {
						final File photo = FileLoader.getPathToAttach(user.photo.photo_small, true);
						final CyclicBarrier barrier = new CyclicBarrier(2);
						if (!photo.exists()) {
							final NotificationCenter.NotificationCenterDelegate listener = new NotificationCenter.NotificationCenterDelegate() {
								@Override
								public void didReceivedNotification(int id, Object... args) {
									if (id == NotificationCenter.FileDidLoaded) {
										FileLog.d("file loaded: " + args[0] + " " + args[0].getClass().getName());
										if (args[0].equals(photo.getName())) {
											FileLog.e("LOADED USER PHOTO");
											try {
												barrier.await(10, TimeUnit.MILLISECONDS);
											} catch (Exception ignore) {
											}
										}
									}
								}
							};
							AndroidUtilities.runOnUIThread(new Runnable() {
								@Override
								public void run() {
									NotificationCenter.getInstance().addObserver(listener, NotificationCenter.FileDidLoaded);
									FileLoader.getInstance().loadFile(user.photo.photo_small, null, 0, 1);
								}
							});
							try {
								barrier.await(10, TimeUnit.SECONDS);
							} catch (Exception ignore) {
							}
							AndroidUtilities.runOnUIThread(new Runnable() {
								@Override
								public void run() {
									NotificationCenter.getInstance().removeObserver(listener, NotificationCenter.FileDidLoaded);
								}
							});
						}
						if (photo.exists() && photo.length() <= 50 * 1024 * 1024) {
							byte[] photoData = new byte[(int) photo.length()];
							FileInputStream photoIn = new FileInputStream(photo);
							new DataInputStream(photoIn).readFully(photoData);
							photoIn.close();
							out.writeInt(photoData.length);
							out.write(photoData);
						} else {
							out.writeInt(0);
						}
					} else {
						out.writeInt(0);
					}
				} else {
					out.writeInt(0);
				}
				out.flush();
				out.close();
			} else if ("/waitForAuthCode".equals(path)) {
				ConnectionsManager.getInstance().setAppPaused(false, false);
				final String[] code = {null};
				final CyclicBarrier barrier = new CyclicBarrier(2);
				final NotificationCenter.NotificationCenterDelegate listener = new NotificationCenter.NotificationCenterDelegate() {
					@Override
					public void didReceivedNotification(int id, Object... args) {
						if (id == NotificationCenter.didReceivedNewMessages) {
							long did = (Long) args[0];
							if (did == 777000) {
								ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
								if (arr.size() > 0) {
									MessageObject msg = arr.get(0);
									if (!TextUtils.isEmpty(msg.messageText)) {
										Matcher matcher = Pattern.compile("[0-9]+").matcher(msg.messageText);
										if (matcher.find()) {
											code[0] = matcher.group();
											try {
												barrier.await(10, TimeUnit.MILLISECONDS);
											} catch (Exception ignore) {
											}
										}
									}
								}
							}
						}
					}
				};
				AndroidUtilities.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						NotificationCenter.getInstance().addObserver(listener, NotificationCenter.didReceivedNewMessages);
					}
				});
				try {
					barrier.await(15, TimeUnit.SECONDS);
				} catch (Exception ignore) {
				}
				AndroidUtilities.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						NotificationCenter.getInstance().removeObserver(listener, NotificationCenter.didReceivedNewMessages);
					}
				});
				DataOutputStream out = new DataOutputStream(ch.getOutputStream(apiClient).await().getOutputStream());
				if (code[0] != null)
					out.writeUTF(code[0]);
				else
					out.writeUTF("");
				out.flush();
				out.close();
				ConnectionsManager.getInstance().setAppPaused(true, false);
			}
		} catch (Exception x) {
			FileLog.e("error processing wear request", x);
		}
		ch.close(apiClient).await();
		apiClient.disconnect();
		FileLog.d("WearableDataLayer channel thread exiting");
		//	}
		//}).start();
	}
}
