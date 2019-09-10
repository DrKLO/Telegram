package org.telegram.messenger;

import android.text.TextUtils;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WearDataLayerListenerService extends WearableListenerService {

	private int currentAccount = UserConfig.selectedAccount;
	private static boolean watchConnected;

	@Override
	public void onCreate() {
		super.onCreate();
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("WearableDataLayer service created");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("WearableDataLayer service destroyed");
		}
	}

	@Override
	public void onChannelOpened(final Channel ch) {
		//new Thread(new Runnable(){
		//	@Override
		//	public void run(){
		GoogleApiClient apiClient = new GoogleApiClient.Builder(WearDataLayerListenerService.this).addApi(Wearable.API).build();
		if (!apiClient.blockingConnect().isSuccess()) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("failed to connect google api client");
			}
			return;
		}
		String path = ch.getPath();
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("wear channel path: " + path);
		}
		try {
			if ("/getCurrentUser".equals(path)) {
				DataOutputStream out = new DataOutputStream(new BufferedOutputStream(ch.getOutputStream(apiClient).await().getOutputStream()));
				if (UserConfig.getInstance(currentAccount).isClientActivated()) {
					final TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
					out.writeInt(user.id);
					out.writeUTF(user.first_name);
					out.writeUTF(user.last_name);
					out.writeUTF(user.phone);
					if (user.photo != null) {
						final File photo = FileLoader.getPathToAttach(user.photo.photo_small, true);
						final CyclicBarrier barrier = new CyclicBarrier(2);
						if (!photo.exists()) {
							final NotificationCenter.NotificationCenterDelegate listener = (id, account, args) -> {
								if (id == NotificationCenter.fileDidLoad) {
									if (BuildVars.LOGS_ENABLED) {
										FileLog.d("file loaded: " + args[0] + " " + args[0].getClass().getName());
									}
									if (args[0].equals(photo.getName())) {
										if (BuildVars.LOGS_ENABLED) {
											FileLog.e("LOADED USER PHOTO");
										}
										try {
											barrier.await(10, TimeUnit.MILLISECONDS);
										} catch (Exception ignore) {
										}
									}
								}
							};
							AndroidUtilities.runOnUIThread(() -> {
								NotificationCenter.getInstance(currentAccount).addObserver(listener, NotificationCenter.fileDidLoad);
								FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForUser(user, false), user, null, 1, 1);
							});
							try {
								barrier.await(10, TimeUnit.SECONDS);
							} catch (Exception ignore) {
							}
							AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).removeObserver(listener, NotificationCenter.fileDidLoad));
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
				ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
				final String[] code = {null};
				final CyclicBarrier barrier = new CyclicBarrier(2);
				final NotificationCenter.NotificationCenterDelegate listener = (id, account, args) -> {
					if (id == NotificationCenter.didReceiveNewMessages) {
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
				};
				AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).addObserver(listener, NotificationCenter.didReceiveNewMessages));
				try {
					barrier.await(30, TimeUnit.SECONDS);
				} catch (Exception ignore) {
				}
				AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).removeObserver(listener, NotificationCenter.didReceiveNewMessages));
				DataOutputStream out = new DataOutputStream(ch.getOutputStream(apiClient).await().getOutputStream());
				if (code[0] != null) {
					out.writeUTF(code[0]);
				} else {
					out.writeUTF("");
				}
				out.flush();
				out.close();
				ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
			} else if ("/getChatPhoto".equals(path)) {
				try (DataInputStream in = new DataInputStream(ch.getInputStream(apiClient).await().getInputStream()); DataOutputStream out = new DataOutputStream(ch.getOutputStream(apiClient).await().getOutputStream())) {
					String _req = in.readUTF();
					JSONObject req = new JSONObject(_req);
					int chatID = req.getInt("chat_id");
					int accountID = req.getInt("account_id");
					int currentAccount = -1;
					for (int i = 0; i < UserConfig.getActivatedAccountsCount(); i++) {
						if (UserConfig.getInstance(i).getClientUserId() == accountID) {
							currentAccount = i;
							break;
						}
					}
					if (currentAccount != -1) {
						TLRPC.FileLocation location = null;
						if (chatID > 0) {
							TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(chatID);
							if (user != null && user.photo != null)
								location = user.photo.photo_small;
						} else {
							TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-chatID);
							if (chat != null && chat.photo != null)
								location = chat.photo.photo_small;
						}
						if (location != null) {
							File file = FileLoader.getPathToAttach(location, true);
							if (file.exists() && file.length() < 102400) {
								out.writeInt((int) file.length());
								FileInputStream fin = new FileInputStream(file);
								byte[] buf = new byte[10240];
								int read;
								while ((read = fin.read(buf)) > 0) {
									out.write(buf, 0, read);
								}
								fin.close();
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
				} catch (Exception ignore) {
				}
			}
		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("error processing wear request", x);
			}
		}
		ch.close(apiClient).await();
		apiClient.disconnect();
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("WearableDataLayer channel thread exiting");
		}
		//	}
		//}).start();
	}

	@Override
	public void onMessageReceived(final MessageEvent messageEvent) {
		if ("/reply".equals(messageEvent.getPath())) {
			AndroidUtilities.runOnUIThread(() -> {
				try {
					ApplicationLoader.postInitApplication();
					String data = new String(messageEvent.getData(), "UTF-8");
					JSONObject r = new JSONObject(data);
					CharSequence text = r.getString("text");
					if (text == null || text.length() == 0) {
						return;
					}
					long dialog_id = r.getLong("chat_id");
					int max_id = r.getInt("max_id");
					int currentAccount = -1;
					int accountID = r.getInt("account_id");
					for (int i = 0; i < UserConfig.getActivatedAccountsCount(); i++) {
						if (UserConfig.getInstance(i).getClientUserId() == accountID) {
							currentAccount = i;
							break;
						}
					}
					if (dialog_id == 0 || max_id == 0 || currentAccount == -1) {
						return;
					}
					SendMessagesHelper.getInstance(currentAccount).sendMessage(text.toString(), dialog_id, null, null, true, null, null, null, true, 0);
					MessagesController.getInstance(currentAccount).markDialogAsRead(dialog_id, max_id, max_id, 0, false, 0, true, 0);
				} catch (Exception x) {
					if (BuildVars.LOGS_ENABLED)
						FileLog.e(x);
				}
			});
		}
	}

	public static void sendMessageToWatch(final String path, final byte[] data, String capability) {
		Wearable.getCapabilityClient(ApplicationLoader.applicationContext)
				.getCapability(capability, CapabilityClient.FILTER_REACHABLE)
				.addOnCompleteListener(task -> {
					CapabilityInfo info = task.getResult();
					if (info != null) {
						MessageClient mc = Wearable.getMessageClient(ApplicationLoader.applicationContext);
						Set<Node> nodes = info.getNodes();
						for (Node node : nodes) {
							mc.sendMessage(node.getId(), path, data);
						}
					}
				});
	}

	@Override
	public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
		if ("remote_notifications".equals(capabilityInfo.getName())) {
			watchConnected = false;
			for (Node node : capabilityInfo.getNodes()) {
				if (node.isNearby())
					watchConnected = true;
			}
		}
	}

	public static void updateWatchConnectionState() {
		try {
			Wearable.getCapabilityClient(ApplicationLoader.applicationContext)
					.getCapability("remote_notifications", CapabilityClient.FILTER_REACHABLE)
					.addOnCompleteListener(task -> {
						watchConnected = false;
						try {
							CapabilityInfo capabilityInfo = task.getResult();
							if (capabilityInfo == null)
								return;
							for (Node node : capabilityInfo.getNodes()) {
								if (node.isNearby())
									watchConnected = true;
							}
						} catch (Exception ignore) {
						}
					});
		} catch (Throwable ignore) {

		}
	}

	public static boolean isWatchConnected() {
		return watchConnected;
	}
}
