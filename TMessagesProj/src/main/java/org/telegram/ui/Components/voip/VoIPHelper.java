package org.telegram.ui.Components.voip;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BetterRatingView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.JoinCallAlert;
import org.telegram.ui.Components.JoinCallByUrlAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.GroupCallActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class VoIPHelper {

	public static long lastCallTime = 0;

	private static final int VOIP_SUPPORT_ID = 4244000;

	public static void startCall(TLRPC.User user, boolean videoCall, boolean canVideoCall, final Activity activity, TLRPC.UserFull userFull, AccountInstance accountInstance) {
		if (accountInstance == null ? MessagesController.getInstance(UserConfig.selectedAccount).isFrozen() : accountInstance.getMessagesController().isFrozen()) {
			AccountFrozenAlert.show(accountInstance == null ? UserConfig.selectedAccount : accountInstance.getCurrentAccount());
			return;
		}
		if (userFull != null && userFull.phone_calls_private) {
			new AlertDialog.Builder(activity)
					.setTitle(LocaleController.getString(R.string.VoipFailed))
					.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable,
							ContactsController.formatName(user.first_name, user.last_name))))
					.setPositiveButton(LocaleController.getString(R.string.OK), null)
					.show();
			return;
		}
		if (ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState() != ConnectionsManager.ConnectionStateConnected) {
			boolean isAirplaneMode = Settings.System.getInt(activity.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
			AlertDialog.Builder bldr = new AlertDialog.Builder(activity)
					.setTitle(isAirplaneMode ? LocaleController.getString(R.string.VoipOfflineAirplaneTitle) : LocaleController.getString(R.string.VoipOfflineTitle))
					.setMessage(isAirplaneMode ? LocaleController.getString(R.string.VoipOfflineAirplane) : LocaleController.getString(R.string.VoipOffline))
					.setPositiveButton(LocaleController.getString(R.string.OK), null);
			if (isAirplaneMode) {
				final Intent settingsIntent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
				if (settingsIntent.resolveActivity(activity.getPackageManager()) != null) {
					bldr.setNeutralButton(LocaleController.getString(R.string.VoipOfflineOpenSettings), (dialog, which) -> activity.startActivity(settingsIntent));
				}
			}
			try {
				bldr.show();
			} catch (Exception e) {
				FileLog.e(e);
			}
			return;
		}

		if (Build.VERSION.SDK_INT >= 23) {
			int code;
			ArrayList<String> permissions = new ArrayList<>();
			if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
				permissions.add(Manifest.permission.RECORD_AUDIO);
			}
			if (videoCall && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
				permissions.add(Manifest.permission.CAMERA);
			}
			if (permissions.isEmpty()) {
				initiateCall(user, null, null, videoCall, canVideoCall, false, null, activity, null, accountInstance);
			} else {
				activity.requestPermissions(permissions.toArray(new String[0]), videoCall ? 102 : 101);
			}
		} else {
			initiateCall(user, null, null, videoCall, canVideoCall, false, null, activity, null, accountInstance);
		}
	}

	public static void startCall(TLRPC.Chat chat, TLRPC.InputPeer peer, String hash, boolean createCall, Activity activity, BaseFragment fragment, AccountInstance accountInstance) {
		startCall(chat, peer, hash, createCall, null, activity, fragment, accountInstance);
	}

	public static void startCall(TLRPC.Chat chat, TLRPC.InputPeer peer, String hash, boolean createCall, Boolean checkJoiner, Activity activity, BaseFragment fragment, AccountInstance accountInstance) {
		if (activity == null) {
			return;
		}
		if (ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState() != ConnectionsManager.ConnectionStateConnected) {
			boolean isAirplaneMode = Settings.System.getInt(activity.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
			AlertDialog.Builder bldr = new AlertDialog.Builder(activity)
					.setTitle(isAirplaneMode ? LocaleController.getString(R.string.VoipOfflineAirplaneTitle) : LocaleController.getString(R.string.VoipOfflineTitle))
					.setMessage(isAirplaneMode ? LocaleController.getString(R.string.VoipGroupOfflineAirplane) : LocaleController.getString(R.string.VoipGroupOffline))
					.setPositiveButton(LocaleController.getString(R.string.OK), null);
			if (isAirplaneMode) {
				final Intent settingsIntent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
				if (settingsIntent.resolveActivity(activity.getPackageManager()) != null) {
					bldr.setNeutralButton(LocaleController.getString(R.string.VoipOfflineOpenSettings), (dialog, which) -> activity.startActivity(settingsIntent));
				}
			}
			try {
				bldr.show();
			} catch (Exception e) {
				FileLog.e(e);
			}
			return;
		}

		initiateCall(null, chat, hash, false, false, createCall, checkJoiner, activity, fragment, accountInstance);
	}

	private static void initiateCall(TLRPC.User user, TLRPC.Chat chat, String hash, boolean videoCall, boolean canVideoCall, boolean createCall, Boolean checkJoiner, final Activity activity, BaseFragment fragment, AccountInstance accountInstance) {
		if (activity == null || user == null && chat == null) {
			return;
		}
		VoIPService voIPService = VoIPService.getSharedInstance();
		if (voIPService != null) {
			long newId = user != null ? user.id : -chat.id;
			long callerId = voIPService.getCallerId();
			if (callerId != newId || voIPService.getAccount() != accountInstance.getCurrentAccount()) {
				String newName;
				String oldName;
				int key2;
				if (voIPService.isConference()) {
					StringBuilder sb = new StringBuilder();
					if (voIPService.groupCall != null) {
						final int account = voIPService.getAccount();
						int count = 0;
						for (int i = 0; i < voIPService.groupCall.participants.size(); ++i) {
							final TLRPC.GroupCallParticipant participant = voIPService.groupCall.participants.valueAt(i);
							final long dialogId = DialogObject.getPeerDialogId(participant.peer);
							if (dialogId != UserConfig.getInstance(account).getClientUserId()) {
								count++;
								if (sb.length() > 0) sb.append(", ");
								sb.append(DialogObject.getShortName(account, dialogId));
								if (count >= 2) break;
							}
						}
						if (count < voIPService.groupCall.participants.size() - 1) {
							sb.append(LocaleController.formatPluralString("AndOther", voIPService.groupCall.participants.size() - 1 - count));
						}
					}
					if (newId > 0) {
						key2 = R.string.VoipOngoingConferenceChatAlert;
					} else {
						key2 = R.string.VoipOngoingConferenceChatAlert2;
					}
					oldName = sb.toString();
				} else if (callerId > 0) {
					TLRPC.User callUser = voIPService.getUser();
					oldName = ContactsController.formatName(callUser.first_name, callUser.last_name);
					if (newId > 0) {
						key2 = R.string.VoipOngoingAlert;
					} else {
						key2 = R.string.VoipOngoingAlert2;
					}
				} else {
					TLRPC.Chat callChat = voIPService.getChat();
					oldName = callChat.title;
					if (newId > 0) {
						key2 = R.string.VoipOngoingChatAlert2;
					} else {
						key2 = R.string.VoipOngoingChatAlert;
					}
				}
				if (user != null) {
					newName = ContactsController.formatName(user.first_name, user.last_name);
				} else {
					newName = chat.title;
				}

				new AlertDialog.Builder(activity)
						.setTitle(callerId < 0 ? LocaleController.getString(R.string.VoipOngoingChatAlertTitle) : LocaleController.getString(R.string.VoipOngoingAlertTitle))
						.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(key2, oldName, newName)))
						.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
							if (VoIPService.getSharedInstance() != null) {
								VoIPService.getSharedInstance().hangUp(() -> {
									lastCallTime = 0;
									doInitiateCall(user, chat, hash, null, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, true, true);
								});
							} else {
								doInitiateCall(user, chat, hash, null, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, true, true);
							}
						})
						.setNegativeButton(LocaleController.getString(R.string.Cancel), null)
						.show();
			} else {
				if (user != null || !(activity instanceof LaunchActivity)) {
					activity.startActivity(new Intent(activity, LaunchActivity.class).setAction(user != null ? "voip" : "voip_chat"));
				} else {
					if (!TextUtils.isEmpty(hash)) {
						voIPService.setGroupCallHash(hash);
					}
					GroupCallActivity.create((LaunchActivity) activity, AccountInstance.getInstance(UserConfig.selectedAccount), null, null, false, null);
				}
			}
		} else if (VoIPService.callIShouldHavePutIntoIntent == null) {
			doInitiateCall(user, chat, hash, null, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, checkJoiner != null ? checkJoiner : true, true);
		}
	}

	private static void doInitiateCall(TLRPC.User user, TLRPC.Chat chat, String hash, TLRPC.InputPeer peer, boolean hasFewPeers, boolean videoCall, boolean canVideoCall, boolean createCall, Activity activity, BaseFragment fragment, AccountInstance accountInstance, boolean checkJoiner, boolean checkAnonymous) {
		doInitiateCall(user, chat, hash, peer, hasFewPeers, videoCall, canVideoCall, createCall, activity, fragment, accountInstance,checkJoiner, checkAnonymous, false);
	}

	public static void joinConference(Activity activity, int account, TLRPC.InputGroupCall inputGroupCall, boolean videoCall, TLRPC.GroupCall preloadedCall) {
		joinConference(activity, account, inputGroupCall, videoCall, preloadedCall, null);
	}
	public static void joinConference(Activity activity, int account, TLRPC.InputGroupCall inputGroupCall, boolean videoCall, TLRPC.GroupCall preloadedCall, HashSet<Long> inviteUsers) {
		if (activity == null) {
			return;
		}

		if (VoIPService.getSharedInstance() != null) {
			VoIPService.getSharedInstance().hangUp(() -> {
				lastCallTime = 0;
				joinConference(activity, account, inputGroupCall, videoCall, preloadedCall, inviteUsers);
			});
			return;
		}

		lastCallTime = SystemClock.elapsedRealtime();
		Intent intent = new Intent(activity, VoIPService.class);
		intent.putExtra("chat_id", 0L);
		intent.putExtra("createGroupCall", false);
		intent.putExtra("hasFewPeers", false);
		intent.putExtra("isRtmpStream", false);
		intent.putExtra("hash", (String) null);
		intent.putExtra("is_outgoing", true);
		intent.putExtra("start_incall_activity", true);
		intent.putExtra("video_call", false);
		SerializedData data = new SerializedData(inputGroupCall.getObjectSize());
		inputGroupCall.serializeToStream(data);
		intent.putExtra("joinConference", data.toByteArray());
		if (preloadedCall != null) {
			data = new SerializedData(preloadedCall.getObjectSize());
			preloadedCall.serializeToStream(data);
			intent.putExtra("joinConferenceCall", data.toByteArray());
		}
		if (inviteUsers != null) {
			long[] array = new long[inviteUsers.size()];
			int i = 0;
			for (long id : inviteUsers) {
				array[i++] = id;
			}
			intent.putExtra("inviteUsers", array);
		}
		intent.putExtra("account", account);
		intent.putExtra("video_call", Build.VERSION.SDK_INT >= 18 && videoCall);
		intent.putExtra("can_video_call", Build.VERSION.SDK_INT >= 18 && /*canVideoCall*/ true);
		try {
			activity.startService(intent);
		} catch (Throwable e) {
			FileLog.e(e);
		}
	}

	private static void doInitiateCall(TLRPC.User user, TLRPC.Chat chat, String hash, TLRPC.InputPeer peer, boolean hasFewPeers, boolean videoCall, boolean canVideoCall, boolean createCall, Activity activity, BaseFragment fragment, AccountInstance accountInstance, boolean checkJoiner, boolean checkAnonymous, boolean isRtmpStream) {
		if (activity == null || user == null && chat == null) {
			return;
		}
		if (SystemClock.elapsedRealtime() - lastCallTime < (chat != null ? 200 : 2000)) {
			return;
		}
		if (checkJoiner && chat != null && !createCall) {
			TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(chat.id);
			if (chatFull != null && chatFull.groupcall_default_join_as != null) {
				long did = MessageObject.getPeerId(chatFull.groupcall_default_join_as);
				TLRPC.InputPeer	inputPeer = accountInstance.getMessagesController().getInputPeer(did);
				JoinCallAlert.checkFewUsers(activity, -chat.id, accountInstance, param -> {
					if (!param && hash != null) {
						JoinCallByUrlAlert alert = new JoinCallByUrlAlert(activity, chat) {
							@Override
							protected void onJoin() {
								doInitiateCall(user, chat, hash, inputPeer, true, videoCall, canVideoCall, false, activity, fragment, accountInstance, false, false);
							}
						};
						if (fragment != null) {
							fragment.showDialog(alert);
						}
					} else {
						doInitiateCall(user, chat, hash, inputPeer, !param, videoCall, canVideoCall, false, activity, fragment, accountInstance, false, false);
					}
				});
				return;
			}
		}
		if (checkJoiner && chat != null) {
			JoinCallAlert.open(activity, -chat.id, accountInstance, fragment, createCall ? JoinCallAlert.TYPE_CREATE : JoinCallAlert.TYPE_JOIN, null, (selectedPeer, hasFew, schedule, rtmp) -> {
				if (createCall && schedule) {
					GroupCallActivity.create((LaunchActivity) activity, accountInstance, chat, selectedPeer, hasFew, hash);
				} else if (!hasFew && hash != null) {
					JoinCallByUrlAlert alert = new JoinCallByUrlAlert(activity, chat) {
						@Override
						protected void onJoin() {
							doInitiateCall(user, chat, hash, selectedPeer, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, false, true, rtmp);
						}
					};
					if (fragment != null) {
						fragment.showDialog(alert);
					}
				} else {
					doInitiateCall(user, chat, hash, selectedPeer, hasFew, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, false, true, rtmp);
				}
			});
			return;
		}
		if (checkAnonymous && !hasFewPeers && peer instanceof TLRPC.TL_inputPeerUser && ChatObject.shouldSendAnonymously(chat) && (!ChatObject.isChannel(chat) || chat.megagroup)) {
			new AlertDialog.Builder(activity)
					.setTitle(ChatObject.isChannelOrGiga(chat) ? LocaleController.getString(R.string.VoipChannelVoiceChat) : LocaleController.getString(R.string.VoipGroupVoiceChat))
					.setMessage(ChatObject.isChannelOrGiga(chat) ? LocaleController.getString(R.string.VoipChannelJoinAnonymouseAlert) : LocaleController.getString(R.string.VoipGroupJoinAnonymouseAlert))
					.setPositiveButton(LocaleController.getString(R.string.VoipChatJoin), (dialog, which) -> doInitiateCall(user, chat, hash, peer, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, false, false))
					.setNegativeButton(LocaleController.getString(R.string.Cancel), null)
					.show();
			return;
		}
		if (chat != null && peer != null) {
			TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(chat.id);
			if (chatFull != null) {
				if (peer instanceof TLRPC.TL_inputPeerUser) {
					chatFull.groupcall_default_join_as = new TLRPC.TL_peerUser();
					chatFull.groupcall_default_join_as.user_id = peer.user_id;
				} else if (peer instanceof TLRPC.TL_inputPeerChat) {
					chatFull.groupcall_default_join_as = new TLRPC.TL_peerChat();
					chatFull.groupcall_default_join_as.chat_id = peer.chat_id;
				} else if (peer instanceof TLRPC.TL_inputPeerChannel) {
					chatFull.groupcall_default_join_as = new TLRPC.TL_peerChannel();
					chatFull.groupcall_default_join_as.channel_id = peer.channel_id;
				}
				if (chatFull instanceof TLRPC.TL_chatFull) {
					chatFull.flags |= 32768;
				} else {
					chatFull.flags |= 67108864;
				}
			}
		}
		if (chat != null && !createCall) {
			ChatObject.Call call = accountInstance.getMessagesController().getGroupCall(chat.id, false);
			if (call != null && call.isScheduled()) {
				GroupCallActivity.create((LaunchActivity) activity, accountInstance, chat, peer, hasFewPeers, hash);
				return;
			}
		}

		lastCallTime = SystemClock.elapsedRealtime();
		Intent intent = new Intent(activity, VoIPService.class);
		if (user != null) {
			intent.putExtra("user_id", user.id);
		} else {
			intent.putExtra("chat_id", chat.id);
			intent.putExtra("createGroupCall", createCall);
			intent.putExtra("hasFewPeers", hasFewPeers);
			intent.putExtra("isRtmpStream", isRtmpStream);
			intent.putExtra("hash", hash);
			if (peer != null) {
				intent.putExtra("peerChannelId", peer.channel_id);
				intent.putExtra("peerChatId", peer.chat_id);
				intent.putExtra("peerUserId", peer.user_id);
				intent.putExtra("peerAccessHash", peer.access_hash);
			}
		}
		intent.putExtra("is_outgoing", true);
		intent.putExtra("start_incall_activity", true);
		intent.putExtra("video_call", Build.VERSION.SDK_INT >= 18 && videoCall);
		intent.putExtra("can_video_call", Build.VERSION.SDK_INT >= 18 && canVideoCall);
		intent.putExtra("account", UserConfig.selectedAccount);
		try {
			activity.startService(intent);
		} catch (Throwable e) {
			FileLog.e(e);
		}
	}

	@TargetApi(Build.VERSION_CODES.M)
	public static void permissionDenied(final Activity activity, final Runnable onFinish, int code) {
		boolean mergedRequest = code == 102;
		if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) || mergedRequest && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
			AlertDialog.Builder dlg = new AlertDialog.Builder(activity)
					.setMessage(AndroidUtilities.replaceTags(mergedRequest ? LocaleController.getString(R.string.VoipNeedMicCameraPermissionWithHint) : LocaleController.getString(R.string.VoipNeedMicPermissionWithHint)))
					.setPositiveButton(LocaleController.getString(R.string.Settings), (dialog, which) -> {
						Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
						Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
						intent.setData(uri);
						activity.startActivity(intent);
					})
					.setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), null)
					.setOnDismissListener(dialog -> {
						if (onFinish != null)
							onFinish.run();
					})
					.setTopAnimation(mergedRequest ? R.raw.permission_request_camera : R.raw.permission_request_microphone, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground));

			dlg.show();
		}
	}

	public static File getLogsDir() {
		File logsDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "voip_logs");
		if (!logsDir.exists()) {
			logsDir.mkdirs();
		}
		return logsDir;
	}

	public static boolean canRateCall(TLRPC.TL_messageActionPhoneCall call) {
		if (!(call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) && !(call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed)) {
			SharedPreferences prefs = MessagesController.getNotificationsSettings(UserConfig.selectedAccount); // always called from chat UI
			Set<String> hashes = prefs.getStringSet("calls_access_hashes", (Set<String>) Collections.EMPTY_SET);
			for (String hash : hashes) {
				String[] d = hash.split(" ");
				if (d.length < 2) {
					continue;
				}
				if (d[0].equals(call.call_id + "")) {
					return true;
				}
			}
		}
		return false;
	}

	public static void sendCallRating(final long callID, final long accessHash, final int account, int rating) {
		final int currentAccount = UserConfig.selectedAccount;
		final TL_phone.setCallRating req = new TL_phone.setCallRating();
		req.rating = rating;
		req.comment = "";
		req.peer = new TLRPC.TL_inputPhoneCall();
		req.peer.access_hash = accessHash;
		req.peer.id = callID;
		req.user_initiative = false;
		ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
			if (response instanceof TLRPC.TL_updates) {
				TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
				MessagesController.getInstance(currentAccount).processUpdates(updates, false);
			}
		});
	}

	public static void showRateAlert(Context context, TLRPC.TL_messageActionPhoneCall call) {
		SharedPreferences prefs = MessagesController.getNotificationsSettings(UserConfig.selectedAccount); // always called from chat UI
		Set<String> hashes = prefs.getStringSet("calls_access_hashes", (Set<String>) Collections.EMPTY_SET);
		for (String hash : hashes) {
			String[] d = hash.split(" ");
			if (d.length < 2)
				continue;
			if (d[0].equals(call.call_id + "")) {
				try {
					long accessHash = Long.parseLong(d[1]);
					showRateAlert(context, null, call.video, call.call_id, accessHash, UserConfig.selectedAccount, true);
				} catch (Exception ignore) {
				}
				return;
			}
		}
	}

	public static void showRateAlert(final Context context, final Runnable onDismiss, boolean isVideo, final long callID, final long accessHash, final int account, final boolean userInitiative) {
		final File log = getLogFile(callID);
		final int[] page = {0};
		LinearLayout alertView = new LinearLayout(context);
		alertView.setOrientation(LinearLayout.VERTICAL);

		int pad = AndroidUtilities.dp(16);
		alertView.setPadding(pad, pad, pad, 0);

		TextView text = new TextView(context);
		text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		text.setGravity(Gravity.CENTER);
		text.setText(LocaleController.getString(R.string.VoipRateCallAlert));
		alertView.addView(text);

		final BetterRatingView bar = new BetterRatingView(context);
		alertView.addView(bar, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

		final LinearLayout problemsWrap = new LinearLayout(context);
		problemsWrap.setOrientation(LinearLayout.VERTICAL);

		View.OnClickListener problemCheckboxClickListener = v -> {
			CheckBoxCell check = (CheckBoxCell) v;
			check.setChecked(!check.isChecked(), true);
		};

		final String[] problems = {isVideo ? "distorted_video" : null, isVideo ? "pixelated_video" : null, "echo", "noise", "interruptions", "distorted_speech", "silent_local", "silent_remote", "dropped"};
		for (int i = 0; i < problems.length; i++) {
			if (problems[i] == null) {
				continue;
			}
			CheckBoxCell check = new CheckBoxCell(context, 1);
			check.setClipToPadding(false);
			check.setTag(problems[i]);
			String label = null;
			switch (i) {
				case 0:
					label = LocaleController.getString(R.string.RateCallVideoDistorted);
					break;
				case 1:
					label = LocaleController.getString(R.string.RateCallVideoPixelated);
					break;
				case 2:
					label = LocaleController.getString(R.string.RateCallEcho);
					break;
				case 3:
					label = LocaleController.getString(R.string.RateCallNoise);
					break;
				case 4:
					label = LocaleController.getString(R.string.RateCallInterruptions);
					break;
				case 5:
					label = LocaleController.getString(R.string.RateCallDistorted);
					break;
				case 6:
					label = LocaleController.getString(R.string.RateCallSilentLocal);
					break;
				case 7:
					label = LocaleController.getString(R.string.RateCallSilentRemote);
					break;
				case 8:
					label = LocaleController.getString(R.string.RateCallDropped);
					break;
			}
			check.setText(label, null, false, false);
			check.setOnClickListener(problemCheckboxClickListener);
			check.setTag(problems[i]);
			problemsWrap.addView(check);
		}
		alertView.addView(problemsWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -8, 0, -8, 0));
		problemsWrap.setVisibility(View.GONE);

		final EditTextBoldCursor commentBox = new EditTextBoldCursor(context);
		commentBox.setHint(LocaleController.getString(R.string.VoipFeedbackCommentHint));
		commentBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		commentBox.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		commentBox.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
		commentBox.setBackground(null);
		commentBox.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
		commentBox.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
		commentBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
		commentBox.setVisibility(View.GONE);
		alertView.addView(commentBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 8, 8, 0));

		final boolean[] includeLogs = {true};
		final CheckBoxCell checkbox = new CheckBoxCell(context, 1);
		View.OnClickListener checkClickListener = v -> {
			includeLogs[0] = !includeLogs[0];
			checkbox.setChecked(includeLogs[0], true);
		};
		checkbox.setText(LocaleController.getString(R.string.CallReportIncludeLogs), null, true, false);
		checkbox.setClipToPadding(false);
		checkbox.setOnClickListener(checkClickListener);
		alertView.addView(checkbox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -8, 0, -8, 0));

		final TextView logsText = new TextView(context);
		logsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		logsText.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
		logsText.setText(LocaleController.getString(R.string.CallReportLogsExplain));
		logsText.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
		logsText.setOnClickListener(checkClickListener);
		alertView.addView(logsText);

		checkbox.setVisibility(View.GONE);
		logsText.setVisibility(View.GONE);
		if (!log.exists()) {
			includeLogs[0] = false;
		}

		final AlertDialog alert = new AlertDialog.Builder(context)
				.setTitle(LocaleController.getString(R.string.CallMessageReportProblem))
				.setView(alertView)
				.setPositiveButton(LocaleController.getString(R.string.Send), (dialog, which) -> {
					//SendMessagesHelper.getInstance(currentAccount).sendMessage(commentBox.getText().toString(), VOIP_SUPPORT_ID, null, null, true, null, null, null);
				})
				.setNegativeButton(LocaleController.getString(R.string.Cancel), null)
				.setOnDismissListener(dialog -> {
					if (onDismiss != null)
						onDismiss.run();
				})
				.create();
		if (BuildVars.LOGS_ENABLED && log.exists()) {
			alert.setNeutralButton("Send log", (dialog, which) -> {
				Intent intent = new Intent(context, LaunchActivity.class);
				intent.setAction(Intent.ACTION_SEND);
				intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(log));
				context.startActivity(intent);
			});
		}
		alert.show();
		alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		final View btn = alert.getButton(DialogInterface.BUTTON_POSITIVE);
		btn.setEnabled(false);
		bar.setOnRatingChangeListener(rating -> {
			btn.setEnabled(rating > 0);
			/*commentBox.setHint(rating<4 ? LocaleController.getString(R.string.CallReportHint) : LocaleController.getString(R.string.VoipFeedbackCommentHint));
			commentBox.setVisibility(rating < 5 && rating > 0 ? View.VISIBLE : View.GONE);
			if (commentBox.getVisibility() == View.GONE) {
				((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(commentBox.getWindowToken(), 0);
			}
			*/
			((TextView) btn).setText((rating < 4 ? LocaleController.getString(R.string.Next) : LocaleController.getString(R.string.Send)).toUpperCase());
		});
		btn.setOnClickListener(v -> {
			int rating = bar.getRating();
			if (rating >= 4 || page[0] == 1) {
				final int currentAccount = UserConfig.selectedAccount;
				final TL_phone.setCallRating req = new TL_phone.setCallRating();
				req.rating = bar.getRating();
				ArrayList<String> problemTags = new ArrayList<>();
				for (int i = 0; i < problemsWrap.getChildCount(); i++) {
					CheckBoxCell check = (CheckBoxCell) problemsWrap.getChildAt(i);
					if (check.isChecked())
						problemTags.add("#" + check.getTag());
				}

				if (req.rating < 5) {
					req.comment = commentBox.getText().toString();
				} else {
					req.comment = "";
				}
				if (!problemTags.isEmpty() && !includeLogs[0]) {
					req.comment += " " + TextUtils.join(" ", problemTags);
				}
				req.peer = new TLRPC.TL_inputPhoneCall();
				req.peer.access_hash = accessHash;
				req.peer.id = callID;
				req.user_initiative = userInitiative;
				ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
					if (response instanceof TLRPC.TL_updates) {
						TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
						MessagesController.getInstance(currentAccount).processUpdates(updates, false);
					}
					if (includeLogs[0] && log.exists() && req.rating < 4) {
						AccountInstance accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount);
						SendMessagesHelper.prepareSendingDocument(accountInstance, log.getAbsolutePath(), log.getAbsolutePath(), null, TextUtils.join(" ", problemTags), "text/plain", VOIP_SUPPORT_ID, null, null, null, null, null, true, 0, null, null, 0, false);
						Toast.makeText(context, LocaleController.getString(R.string.CallReportSent), Toast.LENGTH_LONG).show();
					}
				});
				alert.dismiss();
			} else {
				page[0] = 1;
				bar.setVisibility(View.GONE);
				//text.setText(LocaleController.getString(R.string.CallReportHint));
				text.setVisibility(View.GONE);
				alert.setTitle(LocaleController.getString(R.string.CallReportHint));
				commentBox.setVisibility(View.VISIBLE);
				if (log.exists()) {
					checkbox.setVisibility(View.VISIBLE);
					logsText.setVisibility(View.VISIBLE);
				}
				problemsWrap.setVisibility(View.VISIBLE);
				((TextView) btn).setText(LocaleController.getString(R.string.Send).toUpperCase());
			}
		});
	}

	private static File getLogFile(long callID) {
		if (BuildVars.DEBUG_VERSION) {
			File debugLogsDir = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "logs");
			String[] logs = debugLogsDir.list();
			if (logs != null) {
				for (String log : logs) {
					if (log.endsWith("voip" + callID + ".txt")) {
						return new File(debugLogsDir, log);
					}
				}
			}
		}
		return new File(getLogsDir(), callID + ".log");
	}

	public static void showCallDebugSettings(final Context context) {
		final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
		LinearLayout ll = new LinearLayout(context);
		ll.setOrientation(LinearLayout.VERTICAL);

		TextView warning = new TextView(context);
		warning.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
		warning.setText("Please only change these settings if you know exactly what they do.");
		warning.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		ll.addView(warning, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 8));

		final TextCheckCell tcpCell = new TextCheckCell(context);
		tcpCell.setTextAndCheck("Force TCP", preferences.getBoolean("dbg_force_tcp_in_calls", false), false);
		tcpCell.setOnClickListener(v -> {
			boolean force = preferences.getBoolean("dbg_force_tcp_in_calls", false);
			SharedPreferences.Editor editor = preferences.edit();
			editor.putBoolean("dbg_force_tcp_in_calls", !force);
			editor.commit();
			tcpCell.setChecked(!force);
		});
		ll.addView(tcpCell);

		if (BuildVars.DEBUG_VERSION && BuildVars.LOGS_ENABLED) {
			final TextCheckCell dumpCell = new TextCheckCell(context);
			dumpCell.setTextAndCheck("Dump detailed stats", preferences.getBoolean("dbg_dump_call_stats", false), false);
			dumpCell.setOnClickListener(v -> {
				boolean force = preferences.getBoolean("dbg_dump_call_stats", false);
				SharedPreferences.Editor editor = preferences.edit();
				editor.putBoolean("dbg_dump_call_stats", !force);
				editor.commit();
				dumpCell.setChecked(!force);
			});
			ll.addView(dumpCell);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			final TextCheckCell connectionServiceCell = new TextCheckCell(context);
			connectionServiceCell.setTextAndCheck("Enable ConnectionService", preferences.getBoolean("dbg_force_connection_service", false), false);
			connectionServiceCell.setOnClickListener(v -> {
				boolean force = preferences.getBoolean("dbg_force_connection_service", false);
				SharedPreferences.Editor editor = preferences.edit();
				editor.putBoolean("dbg_force_connection_service", !force);
				editor.commit();
				connectionServiceCell.setChecked(!force);
			});
			ll.addView(connectionServiceCell);
		}

		new AlertDialog.Builder(context)
				.setTitle(LocaleController.getString(R.string.DebugMenuCallSettings))
				.setView(ll)
				.show();
	}

	public static int getDataSavingDefault() {
		boolean low = DownloadController.getInstance(0).lowPreset.lessCallData,
				medium = DownloadController.getInstance(0).mediumPreset.lessCallData,
				high = DownloadController.getInstance(0).highPreset.lessCallData;
		if (!low && !medium && !high) {
			return Instance.DATA_SAVING_NEVER;
		} else if (low && !medium && !high) {
			return Instance.DATA_SAVING_ROAMING;
		} else if (low && medium && !high) {
			return Instance.DATA_SAVING_MOBILE;
		} else if (low && medium && high) {
			return Instance.DATA_SAVING_ALWAYS;
		}
		if (BuildVars.LOGS_ENABLED)
			FileLog.w("Invalid call data saving preset configuration: " + low + "/" + medium + "/" + high);
		return Instance.DATA_SAVING_NEVER;
	}


	public static String getLogFilePath(String name) {
		final Calendar c = Calendar.getInstance();
		final File externalFilesDir = ApplicationLoader.applicationContext.getExternalFilesDir(null);
		return new File(externalFilesDir, String.format(Locale.US, "logs/%02d_%02d_%04d_%02d_%02d_%02d_%s.txt",
				c.get(Calendar.DATE), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR), c.get(Calendar.HOUR_OF_DAY),
				c.get(Calendar.MINUTE), c.get(Calendar.SECOND), name)).getAbsolutePath();
	}

	public static String getLogFilePath(String callId, boolean stats) {
		final File logsDir = getLogsDir();
		if (!BuildVars.DEBUG_VERSION) {
			final File[] _logs = logsDir.listFiles();
			if (_logs != null) {
				final ArrayList<File> logs = new ArrayList<>(Arrays.asList(_logs));
				while (logs.size() > 20) {
					File oldest = logs.get(0);
					for (File file : logs) {
						if (file.getName().endsWith(".log") && file.lastModified() < oldest.lastModified()) {
							oldest = file;
						}
					}
					oldest.delete();
					logs.remove(oldest);
				}
			}
		}
		if (stats) {
			return new File(logsDir, callId + "_stats.log").getAbsolutePath();
		} else {
			return new File(logsDir, callId + ".log").getAbsolutePath();
		}
	}

    public static void showGroupCallAlert(BaseFragment fragment, TLRPC.Chat currentChat, TLRPC.InputPeer peer, boolean recreate, AccountInstance accountInstance) {
		if (fragment == null || fragment.getParentActivity() == null) {
			return;
		}
		JoinCallAlert.checkFewUsers(fragment.getParentActivity(), -currentChat.id, accountInstance, param -> startCall(currentChat, peer, null, true, fragment.getParentActivity(), fragment, accountInstance));
    }
}
