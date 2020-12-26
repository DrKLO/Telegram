/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package org.telegram.messenger.voip;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.Nullable;

import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.VoIPFeedbackActivity;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@SuppressLint("NewApi")
public class VoIPService extends VoIPBaseService {

	public static final int CALL_MIN_LAYER = 65;

	public static final int STATE_HANGING_UP = 10;
	public static final int STATE_EXCHANGING_KEYS = 12;
	public static final int STATE_WAITING = 13;
	public static final int STATE_REQUESTING = 14;
	public static final int STATE_WAITING_INCOMING = 15;
	public static final int STATE_RINGING = 16;
	public static final int STATE_BUSY = 17;

	private TLRPC.User user;
	private int callReqId;

	private byte[] g_a;
	private byte[] a_or_b;
	private byte[] g_a_hash;
	private byte[] authKey;
	private long keyFingerprint;
	private boolean forceRating;

	public static TLRPC.PhoneCall callIShouldHavePutIntoIntent;

	public static NativeInstance.AudioLevelsCallback audioLevelsCallback;

	private boolean needSendDebugLog;
	private boolean needRateCall;
	private long lastTypingTimeSend;

	private boolean endCallAfterRequest;
	private ArrayList<TLRPC.PhoneCall> pendingUpdates = new ArrayList<>();
	private Runnable delayedStartOutgoingCall;

	private boolean startedRinging;

	public boolean isFrontFaceCamera() {
		return isFrontFaceCamera;
	}

	public boolean mutedByAdmin() {
		ChatObject.Call call = VoIPService.getSharedInstance().groupCall;
		if (call != null) {
			TLRPC.TL_groupCallParticipant participant = call.participants.get(UserConfig.getInstance(currentAccount).getClientUserId());
			if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(VoIPService.getSharedInstance().getChat())) {
				return true;
			}
		}
		return false;
	}

	private static class ProxyVideoSink implements VideoSink {
		private VideoSink target;
		private VideoSink background;

		@Override
		synchronized public void onFrame(VideoFrame frame) {
			if (target == null) {
				return;
			}

			target.onFrame(frame);
			if (background != null) {
				background.onFrame(frame);
			}
		}

		synchronized public void setTarget(VideoSink target) {
			this.target = target;
		}

		synchronized public void setBackground(VideoSink background) {
			this.background = background;
		}

		synchronized public void swap() {
			if (target != null && background != null) {
				target = background;
				background = null;
			}
		}
	}

	private ProxyVideoSink localSink;
	private ProxyVideoSink remoteSink;

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@SuppressLint({"MissingPermission", "InlinedApi"})
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (sharedInstance != null) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("Tried to start the VoIP service when it's already started");
			}
			return START_NOT_STICKY;
		}

		currentAccount = intent.getIntExtra("account", -1);
		if (currentAccount == -1) {
			throw new IllegalStateException("No account specified when starting VoIP service");
		}
		int userID = intent.getIntExtra("user_id", 0);
		int chatID = intent.getIntExtra("chat_id", 0);
		createGroupCall = intent.getBooleanExtra("createGroupCall", false);
		isOutgoing = intent.getBooleanExtra("is_outgoing", false);
		videoCall = intent.getBooleanExtra("video_call", false);
		isVideoAvailable = intent.getBooleanExtra("can_video_call", false);
		notificationsDisabled = intent.getBooleanExtra("notifications_disabled", false);
		if (userID != 0) {
			user = MessagesController.getInstance(currentAccount).getUser(userID);
		}
		if (chatID != 0) {
			chat = MessagesController.getInstance(currentAccount).getChat(chatID);
		}
		localSink = new ProxyVideoSink();
		remoteSink = new ProxyVideoSink();
		try {
			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			isHeadsetPlugged = am.isWiredHeadsetOn();
		} catch (Exception e) {
			FileLog.e(e);
		}
		if (chat != null && !createGroupCall) {
			ChatObject.Call call = MessagesController.getInstance(currentAccount).getGroupCall(chat.id, false);
			if (call == null) {
				FileLog.w("VoIPService: trying to open group call without call " + chat.id);
				stopSelf();
				return START_NOT_STICKY;
			}
		}

		if (videoCall) {
			videoCapturer = NativeInstance.createVideoCapturer(localSink, isFrontFaceCamera);
			videoState = Instance.VIDEO_STATE_ACTIVE;
			if (!isBtHeadsetConnected && !isHeadsetPlugged) {
				setAudioOutput(0);
			}
		}

		if (user == null && chat == null) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.w("VoIPService: user == null AND chat == null");
			}
			stopSelf();
			return START_NOT_STICKY;
		}
		sharedInstance = this;
		synchronized (sync) {
			if (setModeRunnable != null) {
				Utilities.globalQueue.cancelRunnable(setModeRunnable);
				setModeRunnable = null;
			}
		}

		if (isOutgoing) {
			if (user != null) {
				dispatchStateChanged(STATE_REQUESTING);
				if (USE_CONNECTION_SERVICE) {
					TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
					Bundle extras = new Bundle();
					Bundle myExtras = new Bundle();
					extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, addAccountToTelecomManager());
					myExtras.putInt("call_type", 1);
					extras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, myExtras);
					ContactsController.getInstance(currentAccount).createOrUpdateConnectionServiceContact(user.id, user.first_name, user.last_name);
					tm.placeCall(Uri.fromParts("tel", "+99084" + user.id, null), extras);
				} else {
					delayedStartOutgoingCall = () -> {
						delayedStartOutgoingCall = null;
						startOutgoingCall();
					};
					AndroidUtilities.runOnUIThread(delayedStartOutgoingCall, 2000);
				}
			} else {
				micMute = true;
				startGroupCall(0, null);
				if (!isBtHeadsetConnected && !isHeadsetPlugged) {
					setAudioOutput(0);
				}
			}
			if (intent.getBooleanExtra("start_incall_activity", false)) {
				Intent intent1 = new Intent(this, LaunchActivity.class).setAction(user != null ? "voip" : "voip_chat").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				if (chat != null) {
					intent1.putExtra("currentAccount", currentAccount);
				}
				startActivity(intent1);
			}
		} else {
			NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeInCallActivity);
			privateCall = callIShouldHavePutIntoIntent;
			videoCall = privateCall != null && privateCall.video;
			if (videoCall) {
				isVideoAvailable = true;
			}
			if (videoCall && (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)) {
				videoCapturer = NativeInstance.createVideoCapturer(localSink, isFrontFaceCamera);
				videoState = Instance.VIDEO_STATE_ACTIVE;
			} else {
				videoState = Instance.VIDEO_STATE_INACTIVE;
			}
			if (videoCall && !isBtHeadsetConnected && !isHeadsetPlugged) {
				setAudioOutput(0);
			}
			callIShouldHavePutIntoIntent = null;
			if (USE_CONNECTION_SERVICE) {
				acknowledgeCall(false);
				showNotification();
			} else {
				acknowledgeCall(true);
			}
		}
		initializeAccountRelatedThings();
		AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.voipServiceCreated));
		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (callIShouldHavePutIntoIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationsController.checkOtherNotificationsChannel();
			Notification.Builder bldr = new Notification.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
					.setContentTitle(LocaleController.getString("VoipOutgoingCall", R.string.VoipOutgoingCall))
					.setShowWhen(false);
			if (groupCall != null) {
				bldr.setSmallIcon(isMicMute() ? R.drawable.voicechat_muted : R.drawable.voicechat_active);
			} else {
				bldr.setSmallIcon(R.drawable.notification);
			}
			startForeground(ID_ONGOING_CALL_NOTIFICATION, bldr.build());
		}
	}

	@Override
	protected void updateServerConfig() {
		final SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
		Instance.setGlobalServerConfig(preferences.getString("voip_server_config", "{}"));
		ConnectionsManager.getInstance(currentAccount).sendRequest(new TLRPC.TL_phone_getCallConfig(), (response, error) -> {
			if (error == null) {
				String data = ((TLRPC.TL_dataJSON) response).data;
				Instance.setGlobalServerConfig(data);
				preferences.edit().putString("voip_server_config", data).commit();
			}
		});
	}

	@Override
	protected void onTgVoipPreStop() {
		/*if(BuildConfig.DEBUG){
			String debugLog=controller.getDebugLog();
			TLRPC.TL_phone_saveCallDebug req=new TLRPC.TL_phone_saveCallDebug();
			req.debug=new TLRPC.TL_dataJSON();
			req.debug.data=debugLog;
			req.peer=new TLRPC.TL_inputPhoneCall();
			req.peer.access_hash=call.access_hash;
			req.peer.id=call.id;
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate(){
				@Override
				public void run(TLObject response, TLRPC.TL_error error){
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("Sent debug logs, response=" + response);
                    }
				}
			});
		}*/
	}

	@Override
	protected void onTgVoipStop(Instance.FinalState finalState) {
		if (user == null) {
			return;
		}
		if (needRateCall || forceRating || finalState.isRatingSuggested) {
			startRatingActivity();
			needRateCall = false;
		}
		if (needSendDebugLog && finalState.debugLog != null) {
			TLRPC.TL_phone_saveCallDebug req = new TLRPC.TL_phone_saveCallDebug();
			req.debug = new TLRPC.TL_dataJSON();
			req.debug.data = finalState.debugLog;
			req.peer = new TLRPC.TL_inputPhoneCall();
			req.peer.access_hash = privateCall.access_hash;
			req.peer.id = privateCall.id;
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.d("Sent debug logs, response = " + response);
				}
			});
			needSendDebugLog = false;
		}
	}

	public static VoIPService getSharedInstance() {
		return sharedInstance instanceof VoIPService ? ((VoIPService) sharedInstance) : null;
	}

	public TLRPC.User getUser() {
		return user;
	}

	public TLRPC.Chat getChat() {
		return chat;
	}

	public int getCallerId() {
		if (user != null) {
			return user.id;
		} else {
			return -chat.id;
		}
	}

	public void hangUp() {
		hangUp(0, null);
	}

	public void hangUp(int discard) {
		hangUp(discard, null);
	}

	public void hangUp(Runnable onDone) {
		hangUp(0, onDone);
	}

	public void hangUp(int discard, Runnable onDone) {
		declineIncomingCall(currentState == STATE_RINGING || (currentState == STATE_WAITING && isOutgoing) ? DISCARD_REASON_MISSED : DISCARD_REASON_HANGUP, onDone);
		if (groupCall != null) {
			if (discard == 2) {
				return;
			}
			if (discard == 1) {
				TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
				if (chatFull != null) {
					chatFull.flags &=~ 2097152;
					chatFull.call = null;
					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.groupCallUpdated, chat.id, groupCall.call.id, false);
				}
				TLRPC.TL_phone_discardGroupCall req = new TLRPC.TL_phone_discardGroupCall();
				req.call = groupCall.getInputGroupCall();
				ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
					if (response instanceof TLRPC.TL_updates) {
						TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
						MessagesController.getInstance(currentAccount).processUpdates(updates, false);
					}
				});
			} else {
				TLRPC.TL_phone_leaveGroupCall req = new TLRPC.TL_phone_leaveGroupCall();
				req.call = groupCall.getInputGroupCall();
				req.source = mySource;
				ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
					if (response instanceof TLRPC.TL_updates) {
						TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
						MessagesController.getInstance(currentAccount).processUpdates(updates, false);
					}
				});
			}
		}
	}

	private void startOutgoingCall() {
		if (USE_CONNECTION_SERVICE && systemCallConnection != null) {
			systemCallConnection.setDialing();
		}
		configureDeviceForCall();
		showNotification();
		startConnectingSound();
		dispatchStateChanged(STATE_REQUESTING);
		AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall));
		final byte[] salt = new byte[256];
		Utilities.random.nextBytes(salt);

		TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
		req.random_length = 256;
		final MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
		req.version = messagesStorage.getLastSecretVersion();
		callReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
			callReqId = 0;
			if (endCallAfterRequest) {
				callEnded();
				return;
			}
			if (error == null) {
				TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
				if (response instanceof TLRPC.TL_messages_dhConfig) {
					if (!Utilities.isGoodPrime(res.p, res.g)) {
						callFailed();
						return;
					}
					messagesStorage.setSecretPBytes(res.p);
					messagesStorage.setSecretG(res.g);
					messagesStorage.setLastSecretVersion(res.version);
					messagesStorage.saveSecretParams(messagesStorage.getLastSecretVersion(), messagesStorage.getSecretG(), messagesStorage.getSecretPBytes());
				}
				final byte[] salt1 = new byte[256];
				for (int a = 0; a < 256; a++) {
					salt1[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
				}

				BigInteger i_g_a = BigInteger.valueOf(messagesStorage.getSecretG());
				i_g_a = i_g_a.modPow(new BigInteger(1, salt1), new BigInteger(1, messagesStorage.getSecretPBytes()));
				byte[] g_a = i_g_a.toByteArray();
				if (g_a.length > 256) {
					byte[] correctedAuth = new byte[256];
					System.arraycopy(g_a, 1, correctedAuth, 0, 256);
					g_a = correctedAuth;
				}

				TLRPC.TL_phone_requestCall reqCall = new TLRPC.TL_phone_requestCall();
				reqCall.user_id = MessagesController.getInstance(currentAccount).getInputUser(user);
				reqCall.protocol = new TLRPC.TL_phoneCallProtocol();
				reqCall.video = videoCall;
				reqCall.protocol.udp_p2p = true;
				reqCall.protocol.udp_reflector = true;
				reqCall.protocol.min_layer = CALL_MIN_LAYER;
				reqCall.protocol.max_layer = Instance.getConnectionMaxLayer();
				reqCall.protocol.library_versions.addAll(Instance.AVAILABLE_VERSIONS);
				VoIPService.this.g_a = g_a;
				reqCall.g_a_hash = Utilities.computeSHA256(g_a, 0, g_a.length);
				reqCall.random_id = Utilities.random.nextInt();

				ConnectionsManager.getInstance(currentAccount).sendRequest(reqCall, (response12, error12) -> AndroidUtilities.runOnUIThread(() -> {
					if (error12 == null) {
						privateCall = ((TLRPC.TL_phone_phoneCall) response12).phone_call;
						a_or_b = salt1;
						dispatchStateChanged(STATE_WAITING);
						if (endCallAfterRequest) {
							hangUp();
							return;
						}
						if (pendingUpdates.size() > 0 && privateCall != null) {
							for (TLRPC.PhoneCall call : pendingUpdates) {
								onCallUpdated(call);
							}
							pendingUpdates.clear();
						}
						timeoutRunnable = () -> {
							timeoutRunnable = null;
							TLRPC.TL_phone_discardCall req1 = new TLRPC.TL_phone_discardCall();
							req1.peer = new TLRPC.TL_inputPhoneCall();
							req1.peer.access_hash = privateCall.access_hash;
							req1.peer.id = privateCall.id;
							req1.reason = new TLRPC.TL_phoneCallDiscardReasonMissed();
							ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (response1, error1) -> {
								if (BuildVars.LOGS_ENABLED) {
									if (error1 != null) {
										FileLog.e("error on phone.discardCall: " + error1);
									} else {
										FileLog.d("phone.discardCall " + response1);
									}
								}
								AndroidUtilities.runOnUIThread(VoIPService.this::callFailed);
							}, ConnectionsManager.RequestFlagFailOnServerErrors);
						};
						AndroidUtilities.runOnUIThread(timeoutRunnable, MessagesController.getInstance(currentAccount).callReceiveTimeout);
					} else {
						if (error12.code == 400 && "PARTICIPANT_VERSION_OUTDATED".equals(error12.text)) {
							callFailed(Instance.ERROR_PEER_OUTDATED);
						} else if (error12.code == 403) {
							callFailed(Instance.ERROR_PRIVACY);
						} else if (error12.code == 406) {
							callFailed(Instance.ERROR_LOCALIZED);
						} else {
							if (BuildVars.LOGS_ENABLED) {
								FileLog.e("Error on phone.requestCall: " + error12);
							}
							callFailed();
						}
					}
				}), ConnectionsManager.RequestFlagFailOnServerErrors);
			} else {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("Error on getDhConfig " + error);
				}
				callFailed();
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors);
	}

	private void acknowledgeCall(final boolean startRinging) {
		if (privateCall instanceof TLRPC.TL_phoneCallDiscarded) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.w("Call " + privateCall.id + " was discarded before the service started, stopping");
			}
			stopSelf();
			return;
		}
		if (Build.VERSION.SDK_INT >= 19 && XiaomiUtilities.isMIUI() && !XiaomiUtilities.isCustomPermissionGranted(XiaomiUtilities.OP_SHOW_WHEN_LOCKED)) {
			if (((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode()) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("MIUI: no permission to show when locked but the screen is locked. ¯\\_(ツ)_/¯");
				}
				stopSelf();
				return;
			}
		}
		TLRPC.TL_phone_receivedCall req = new TLRPC.TL_phone_receivedCall();
		req.peer = new TLRPC.TL_inputPhoneCall();
		req.peer.id = privateCall.id;
		req.peer.access_hash = privateCall.access_hash;
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			if (sharedInstance == null) {
				return;
			}
			if (BuildVars.LOGS_ENABLED) {
				FileLog.w("receivedCall response = " + response);
			}
			if (error != null) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("error on receivedCall: " + error);
				}
				stopSelf();
			} else {
				if (USE_CONNECTION_SERVICE) {
					ContactsController.getInstance(currentAccount).createOrUpdateConnectionServiceContact(user.id, user.first_name, user.last_name);
					TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
					Bundle extras = new Bundle();
					extras.putInt("call_type", 1);
					tm.addNewIncomingCall(addAccountToTelecomManager(), extras);
				}
				if (startRinging) {
					startRinging();
				}
			}
		}), ConnectionsManager.RequestFlagFailOnServerErrors);
	}

	protected void startRinging() {
		if (currentState == STATE_WAITING_INCOMING) {
			return;
		}
		if (USE_CONNECTION_SERVICE && systemCallConnection != null) {
			systemCallConnection.setRinging();
		}
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("starting ringing for call " + privateCall.id);
		}
		dispatchStateChanged(STATE_WAITING_INCOMING);
		if (!notificationsDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			showIncomingNotification(ContactsController.formatName(user.first_name, user.last_name), null, user, privateCall.video, 0);
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("Showing incoming call notification");
			}
		} else {
			startRingtoneAndVibration(user.id);
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("Starting incall activity for incoming call");
			}
			try {
				PendingIntent.getActivity(VoIPService.this, 12345, new Intent(VoIPService.this, LaunchActivity.class).setAction("voip"), 0).send();
			} catch (Exception x) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("Error starting incall activity", x);
				}
			}
		}
	}

	@Override
	public void startRingtoneAndVibration() {
		if (!startedRinging) {
			startRingtoneAndVibration(user.id);
			startedRinging = true;
		}
	}

	@Override
	protected boolean isRinging() {
		return currentState == STATE_WAITING_INCOMING;
	}

	public boolean isJoined() {
		return currentState != STATE_WAIT_INIT && currentState != STATE_CREATING;
	}

	public void acceptIncomingCall() {
		MessagesController.getInstance(currentAccount).ignoreSetOnline = false;
		stopRinging();
		showNotification();
		configureDeviceForCall();
		startConnectingSound();
		dispatchStateChanged(STATE_EXCHANGING_KEYS);
		AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall));
		final MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
		TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
		req.random_length = 256;
		req.version = messagesStorage.getLastSecretVersion();
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
			if (error == null) {
				TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
				if (response instanceof TLRPC.TL_messages_dhConfig) {
					if (!Utilities.isGoodPrime(res.p, res.g)) {
						if (BuildVars.LOGS_ENABLED) {
							FileLog.e("stopping VoIP service, bad prime");
						}
						callFailed();
						return;
					}

					messagesStorage.setSecretPBytes(res.p);
					messagesStorage.setSecretG(res.g);
					messagesStorage.setLastSecretVersion(res.version);
					MessagesStorage.getInstance(currentAccount).saveSecretParams(messagesStorage.getLastSecretVersion(), messagesStorage.getSecretG(), messagesStorage.getSecretPBytes());
				}
				byte[] salt = new byte[256];
				for (int a = 0; a < 256; a++) {
					salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
				}
				if (privateCall == null) {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.e("call is null");
					}
					callFailed();
					return;
				}
				a_or_b = salt;
				BigInteger g_b = BigInteger.valueOf(messagesStorage.getSecretG());
				BigInteger p = new BigInteger(1, messagesStorage.getSecretPBytes());
				g_b = g_b.modPow(new BigInteger(1, salt), p);
				g_a_hash = privateCall.g_a_hash;

				byte[] g_b_bytes = g_b.toByteArray();
				if (g_b_bytes.length > 256) {
					byte[] correctedAuth = new byte[256];
					System.arraycopy(g_b_bytes, 1, correctedAuth, 0, 256);
					g_b_bytes = correctedAuth;
				}

				TLRPC.TL_phone_acceptCall req1 = new TLRPC.TL_phone_acceptCall();
				req1.g_b = g_b_bytes;
				req1.peer = new TLRPC.TL_inputPhoneCall();
				req1.peer.id = privateCall.id;
				req1.peer.access_hash = privateCall.access_hash;
				req1.protocol = new TLRPC.TL_phoneCallProtocol();
				req1.protocol.udp_p2p = req1.protocol.udp_reflector = true;
				req1.protocol.min_layer = CALL_MIN_LAYER;
				req1.protocol.max_layer = Instance.getConnectionMaxLayer();
				req1.protocol.library_versions.addAll(Instance.AVAILABLE_VERSIONS);
				ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
					if (error1 == null) {
						if (BuildVars.LOGS_ENABLED) {
							FileLog.w("accept call ok! " + response1);
						}
						privateCall = ((TLRPC.TL_phone_phoneCall) response1).phone_call;
						if (privateCall instanceof TLRPC.TL_phoneCallDiscarded) {
							onCallUpdated(privateCall);
						}
					} else {
						if (BuildVars.LOGS_ENABLED) {
							FileLog.e("Error on phone.acceptCall: " + error1);
						}
						callFailed();
					}
				}), ConnectionsManager.RequestFlagFailOnServerErrors);
			} else {
				callFailed();
			}
		});
	}

	public void declineIncomingCall() {
		declineIncomingCall(DISCARD_REASON_HANGUP, null);
	}

	public void requestVideoCall() {
		if (tgVoip == null) {
			return;
		}
		tgVoip.setupOutgoingVideo(localSink, isFrontFaceCamera);
	}

	public void switchCamera() {
		if (tgVoip == null || switchingCamera) {
			if (videoCapturer != 0 && !switchingCamera) {
				NativeInstance.switchCameraCapturer(videoCapturer, !isFrontFaceCamera);
			}
			return;
		}
		switchingCamera = true;
		tgVoip.switchCamera(!isFrontFaceCamera);
	}

	public void setVideoState(int videoState) {
		if (tgVoip == null) {
			if (videoCapturer != 0) {
				this.videoState = videoState;
				NativeInstance.setVideoStateCapturer(videoCapturer, videoState);
			} else if (videoState == Instance.VIDEO_STATE_ACTIVE && currentState != STATE_BUSY && currentState != STATE_ENDED) {
				videoCapturer = NativeInstance.createVideoCapturer(localSink, isFrontFaceCamera);
				this.videoState = Instance.VIDEO_STATE_ACTIVE;
			}
			return;
		}
		this.videoState = videoState;
		tgVoip.setVideoState(videoState);
		checkIsNear();
	}

	public int getVideoState() {
		return videoState;
	}

	public void setSinks(VideoSink local, VideoSink remote) {
		localSink.setTarget(local);
		remoteSink.setTarget(remote);
	}

	public void setBackgroundSinks(VideoSink local, VideoSink remote) {
		localSink.setBackground(local);
		remoteSink.setBackground(remote);
	}

	public void swapSinks() {
		localSink.swap();
		remoteSink.swap();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		setSinks(null, null);
		if (onDestroyRunnable != null) {
			onDestroyRunnable.run();
		}
	}

	@Override
	protected Class<? extends Activity> getUIActivityClass() {
		return LaunchActivity.class;
	}

	public boolean isHangingUp() {
		return currentState == STATE_HANGING_UP;
	}

	public void declineIncomingCall(int reason, final Runnable onDone) {
		stopRinging();
		callDiscardReason = reason;
		if (currentState == STATE_REQUESTING) {
			if (delayedStartOutgoingCall != null) {
				AndroidUtilities.cancelRunOnUIThread(delayedStartOutgoingCall);
				callEnded();
			} else {
				dispatchStateChanged(STATE_HANGING_UP);
				endCallAfterRequest = true;
				AndroidUtilities.runOnUIThread(() -> {
					if (currentState == STATE_HANGING_UP) {
						callEnded();
					}
				}, 5000);
			}
			return;
		}
		if (currentState == STATE_HANGING_UP || currentState == STATE_ENDED) {
			return;
		}
		dispatchStateChanged(STATE_HANGING_UP);
		if (privateCall == null) {
			onDestroyRunnable = onDone;
			callEnded();
			if (callReqId != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(callReqId, false);
				callReqId = 0;
			}
			return;
		}
		TLRPC.TL_phone_discardCall req = new TLRPC.TL_phone_discardCall();
		req.peer = new TLRPC.TL_inputPhoneCall();
		req.peer.access_hash = privateCall.access_hash;
		req.peer.id = privateCall.id;
		req.duration = (int) (getCallDuration() / 1000);
		req.connection_id = tgVoip != null ? tgVoip.getPreferredRelayId() : 0;
		switch (reason) {
			case DISCARD_REASON_DISCONNECT:
				req.reason = new TLRPC.TL_phoneCallDiscardReasonDisconnect();
				break;
			case DISCARD_REASON_MISSED:
				req.reason = new TLRPC.TL_phoneCallDiscardReasonMissed();
				break;
			case DISCARD_REASON_LINE_BUSY:
				req.reason = new TLRPC.TL_phoneCallDiscardReasonBusy();
				break;
			case DISCARD_REASON_HANGUP:
			default:
				req.reason = new TLRPC.TL_phoneCallDiscardReasonHangup();
				break;
		}
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
			if (error != null) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("error on phone.discardCall: " + error);
				}
			} else {
				if (response instanceof TLRPC.TL_updates) {
					TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
					MessagesController.getInstance(currentAccount).processUpdates(updates, false);
				}
				if (BuildVars.LOGS_ENABLED) {
					FileLog.d("phone.discardCall " + response);
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors);
		onDestroyRunnable = onDone;
		callEnded();
	}

	public void onSignalingData(TLRPC.TL_updatePhoneCallSignalingData data) {
		if (user == null || tgVoip == null || tgVoip.isGroup() || getCallID() != data.phone_call_id) {
			return;
		}
		tgVoip.onSignalingDataReceive(data.data);
	}

	public void onGroupCallParticipantsUpdate(TLRPC.TL_updateGroupCallParticipants update) {
		if (chat == null || groupCall == null || groupCall.call.id != update.call.id) {
			return;
		}
		ArrayList<Integer> toRemove = null;
		int selfId = UserConfig.getInstance(currentAccount).clientUserId;
		for (int a = 0, N = update.participants.size(); a < N; a++) {
			TLRPC.TL_groupCallParticipant participant = update.participants.get(a);
			if (participant.left) {
				if (toRemove == null) {
					toRemove = new ArrayList<>();
				}
				toRemove.add(participant.source);
			} else if (participant.user_id == selfId && participant.source != mySource) {
				hangUp(2);
				return;
			}
		}
		if (toRemove != null) {
			int[] ssrcs = new int[toRemove.size()];
			for (int a = 0, N = toRemove.size(); a < N; a++) {
				ssrcs[a] = toRemove.get(a);
			}
			tgVoip.removeSsrcs(ssrcs);
		}
	}

	public void onGroupCallUpdated(TLRPC.GroupCall call) {
		if (chat == null) {
			return;
		}
		if (groupCall == null || groupCall.call.id != call.id) {
			return;
		}
		if (groupCall.call instanceof TLRPC.TL_groupCallDiscarded) {
			hangUp(2);
			return;
		}
		if (currentState == STATE_WAIT_INIT && call.params != null) {
			TLRPC.TL_dataJSON json = call.params;
			try {
				JSONObject object = new JSONObject(json.data);
				object = object.getJSONObject("transport");
				String ufrag = object.getString("ufrag");
				String pwd = object.getString("pwd");
				JSONArray array = object.getJSONArray("fingerprints");
				Instance.Fingerprint[] fingerprints = new Instance.Fingerprint[array.length()];
				for (int a = 0; a < fingerprints.length; a++) {
					JSONObject item = array.getJSONObject(a);
					fingerprints[a] = new Instance.Fingerprint(
							item.getString("hash"),
							item.getString("setup"),
							item.getString("fingerprint")
					);
				}
				array = object.getJSONArray("candidates");
				Instance.Candidate[] candidates = new Instance.Candidate[array.length()];
				for (int a = 0; a < candidates.length; a++) {
					JSONObject item = array.getJSONObject(a);
					candidates[a] = new Instance.Candidate(
							item.optString("port", ""),
							item.optString("protocol", ""),
							item.optString("network", ""),
							item.optString("generation", ""),
							item.optString("id", ""),
							item.optString("component", ""),
							item.optString("foundation", ""),
							item.optString("priority", ""),
							item.optString("ip", ""),
							item.optString("type", ""),
							item.optString("tcpType", ""),
							item.optString("relAddr", ""),
							item.optString("relPort", "")
					);
				}
				tgVoip.setJoinResponsePayload(ufrag, pwd, fingerprints, candidates);
				dispatchStateChanged(STATE_WAIT_INIT_ACK);
			} catch (Exception e) {
				FileLog.e(e);
			}
		}
	}

	public void onCallUpdated(TLRPC.PhoneCall phoneCall) {
		if (user == null) {
			return;
		}
		if (privateCall == null) {
			pendingUpdates.add(phoneCall);
			return;
		}
		if (phoneCall == null) {
			return;
		}
		if (phoneCall.id != privateCall.id) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.w("onCallUpdated called with wrong call id (got " + phoneCall.id + ", expected " + this.privateCall.id + ")");
			}
			return;
		}
		if (phoneCall.access_hash == 0) {
			phoneCall.access_hash = this.privateCall.access_hash;
		}
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("Call updated: " + phoneCall);
		}
		privateCall = phoneCall;
		if (phoneCall instanceof TLRPC.TL_phoneCallDiscarded) {
			needSendDebugLog = phoneCall.need_debug;
			needRateCall = phoneCall.need_rating;
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("call discarded, stopping service");
			}
			if (phoneCall.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
				dispatchStateChanged(STATE_BUSY);
				playingSound = true;
				Utilities.globalQueue.postRunnable(() -> soundPool.play(spBusyId, 1, 1, 0, -1, 1));
				AndroidUtilities.runOnUIThread(afterSoundRunnable, 1500);
				endConnectionServiceCall(1500);
				stopSelf();
			} else {
				callEnded();
			}
		} else if (phoneCall instanceof TLRPC.TL_phoneCall && authKey == null) {
			if (phoneCall.g_a_or_b == null) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.w("stopping VoIP service, Ga == null");
				}
				callFailed();
				return;
			}
			if (!Arrays.equals(g_a_hash, Utilities.computeSHA256(phoneCall.g_a_or_b, 0, phoneCall.g_a_or_b.length))) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.w("stopping VoIP service, Ga hash doesn't match");
				}
				callFailed();
				return;
			}
			g_a = phoneCall.g_a_or_b;
			BigInteger g_a = new BigInteger(1, phoneCall.g_a_or_b);
			BigInteger p = new BigInteger(1, MessagesStorage.getInstance(currentAccount).getSecretPBytes());

			if (!Utilities.isGoodGaAndGb(g_a, p)) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.w("stopping VoIP service, bad Ga and Gb (accepting)");
				}
				callFailed();
				return;
			}
			g_a = g_a.modPow(new BigInteger(1, a_or_b), p);

			byte[] authKey = g_a.toByteArray();
			if (authKey.length > 256) {
				byte[] correctedAuth = new byte[256];
				System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
				authKey = correctedAuth;
			} else if (authKey.length < 256) {
				byte[] correctedAuth = new byte[256];
				System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
				for (int a = 0; a < 256 - authKey.length; a++) {
					correctedAuth[a] = 0;
				}
				authKey = correctedAuth;
			}
			byte[] authKeyHash = Utilities.computeSHA1(authKey);
			byte[] authKeyId = new byte[8];
			System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
			VoIPService.this.authKey = authKey;
			keyFingerprint = Utilities.bytesToLong(authKeyId);

			if (keyFingerprint != phoneCall.key_fingerprint) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.w("key fingerprints don't match");
				}
				callFailed();
				return;
			}

			initiateActualEncryptedCall();
		} else if (phoneCall instanceof TLRPC.TL_phoneCallAccepted && authKey == null) {
			processAcceptedCall();
		} else {
			if (currentState == STATE_WAITING && phoneCall.receive_date != 0) {
				dispatchStateChanged(STATE_RINGING);
				if (BuildVars.LOGS_ENABLED) {
					FileLog.d("!!!!!! CALL RECEIVED");
				}
				if (connectingSoundRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable);
					connectingSoundRunnable = null;
				}
				Utilities.globalQueue.postRunnable(() -> {
					if (spPlayID != 0) {
						soundPool.stop(spPlayID);
					}
					spPlayID = soundPool.play(spRingbackID, 1, 1, 0, -1, 1);
				});
				if (timeoutRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
					timeoutRunnable = null;
				}
				timeoutRunnable = () -> {
					timeoutRunnable = null;
					declineIncomingCall(DISCARD_REASON_MISSED, null);
				};
				AndroidUtilities.runOnUIThread(timeoutRunnable, MessagesController.getInstance(currentAccount).callRingTimeout);
			}
		}
	}

	private void startRatingActivity() {
		try {
			PendingIntent.getActivity(VoIPService.this, 0, new Intent(VoIPService.this, VoIPFeedbackActivity.class)
					.putExtra("call_id", privateCall.id)
					.putExtra("call_access_hash", privateCall.access_hash)
					.putExtra("call_video", privateCall.video)
					.putExtra("account", currentAccount)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), 0).send();
		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("Error starting incall activity", x);
			}
		}
	}

	public byte[] getEncryptionKey() {
		return authKey;
	}

	private void processAcceptedCall() {
		dispatchStateChanged(STATE_EXCHANGING_KEYS);
		BigInteger p = new BigInteger(1, MessagesStorage.getInstance(currentAccount).getSecretPBytes());
		BigInteger i_authKey = new BigInteger(1, privateCall.g_b);

		if (!Utilities.isGoodGaAndGb(i_authKey, p)) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.w("stopping VoIP service, bad Ga and Gb");
			}
			callFailed();
			return;
		}

		i_authKey = i_authKey.modPow(new BigInteger(1, a_or_b), p);

		byte[] authKey = i_authKey.toByteArray();
		if (authKey.length > 256) {
			byte[] correctedAuth = new byte[256];
			System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
			authKey = correctedAuth;
		} else if (authKey.length < 256) {
			byte[] correctedAuth = new byte[256];
			System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
			for (int a = 0; a < 256 - authKey.length; a++) {
				correctedAuth[a] = 0;
			}
			authKey = correctedAuth;
		}
		byte[] authKeyHash = Utilities.computeSHA1(authKey);
		byte[] authKeyId = new byte[8];
		System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
		long fingerprint = Utilities.bytesToLong(authKeyId);
		this.authKey = authKey;
		keyFingerprint = fingerprint;
		TLRPC.TL_phone_confirmCall req = new TLRPC.TL_phone_confirmCall();
		req.g_a = g_a;
		req.key_fingerprint = fingerprint;
		req.peer = new TLRPC.TL_inputPhoneCall();
		req.peer.id = privateCall.id;
		req.peer.access_hash = privateCall.access_hash;
		req.protocol = new TLRPC.TL_phoneCallProtocol();
		req.protocol.max_layer = Instance.getConnectionMaxLayer();
		req.protocol.min_layer = CALL_MIN_LAYER;
		req.protocol.udp_p2p = req.protocol.udp_reflector = true;
		req.protocol.library_versions.addAll(Instance.AVAILABLE_VERSIONS);
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			if (error != null) {
				callFailed();
			} else {
				privateCall = ((TLRPC.TL_phone_phoneCall) response).phone_call;
				initiateActualEncryptedCall();
			}
		}));
	}

	private int convertDataSavingMode(int mode) {
		if (mode != Instance.DATA_SAVING_ROAMING) {
			return mode;
		}
		return ApplicationLoader.isRoaming() ? Instance.DATA_SAVING_MOBILE : Instance.DATA_SAVING_NEVER;
	}

	public void migrateToChat(TLRPC.Chat newChat) {
		chat = newChat;
	}

	private void startGroupCall(int ssrc, String json) {
		if (sharedInstance != this) {
			return;
		}
		if (createGroupCall) {
			groupCall = new ChatObject.Call();
			groupCall.call = new TLRPC.TL_groupCall();
			groupCall.call.participants_count = 0;
			groupCall.call.version = 1;
			groupCall.call.can_change_join_muted = true;
			groupCall.chatId = chat.id;
			groupCall.currentAccount = AccountInstance.getInstance(currentAccount);

			dispatchStateChanged(STATE_CREATING);
			TLRPC.TL_phone_createGroupCall req = new TLRPC.TL_phone_createGroupCall();
			req.peer = MessagesController.getInputPeer(chat);
			req.random_id = Utilities.random.nextInt();
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
				if (response != null) {
					TLRPC.Updates updates = (TLRPC.Updates) response;
					for (int a = 0; a < updates.updates.size(); a++) {
						TLRPC.Update update = updates.updates.get(a);
						if (update instanceof TLRPC.TL_updateGroupCall) {
							TLRPC.TL_updateGroupCall updateGroupCall = (TLRPC.TL_updateGroupCall) update;
							AndroidUtilities.runOnUIThread(() -> {
								if (sharedInstance == null) {
									return;
								}
								groupCall.call.access_hash = updateGroupCall.call.access_hash;
								groupCall.call.id = updateGroupCall.call.id;
								MessagesController.getInstance(currentAccount).putGroupCall(groupCall.chatId, groupCall);
								startGroupCall(0, null);
							});
							break;
						}
					}
					MessagesController.getInstance(currentAccount).processUpdates(updates, false);
				} else {
					AndroidUtilities.runOnUIThread(() -> {
						NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 6, error.text);
						hangUp(0);
					});
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors);
			createGroupCall = false;
			return;
		}

		if (json == null) {
			if (groupCall == null) {
				groupCall = MessagesController.getInstance(currentAccount).getGroupCall(chat.id, false);
			}
			configureDeviceForCall();
			showNotification();
			AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall));
			createGroupInstance();
		} else {
			if (getSharedInstance() == null || groupCall == null) {
				return;
			}
			dispatchStateChanged(STATE_WAIT_INIT);
			mySource = ssrc;
			TLRPC.TL_phone_joinGroupCall req = new TLRPC.TL_phone_joinGroupCall();
			req.muted = true;
			req.call = groupCall.getInputGroupCall();
			req.params = new TLRPC.TL_dataJSON();
			req.params.data = json;
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
				if (response != null) {
					MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) response, false);
					AndroidUtilities.runOnUIThread(() -> groupCall.loadMembers(false));
				} else {
					AndroidUtilities.runOnUIThread(() -> {
						if ("GROUPCALL_SSRC_DUPLICATE_MUCH".equals(error.text)) {
							createGroupInstance();
						} else {
							NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 6, error.text);
							hangUp(0);
						}
					});
				}
			}, BuildVars.DEBUG_PRIVATE_VERSION ? ConnectionsManager.RequestFlagFailOnServerErrors : 0);
		}
	}

	private Runnable shortPollRunnable;
	private int checkRequestId;

	private void startGroupCheckShortpoll() {
		if (shortPollRunnable != null || sharedInstance == null || groupCall == null) {
			return;
		}
		AndroidUtilities.runOnUIThread(shortPollRunnable = () -> {
			if (shortPollRunnable == null || sharedInstance == null || groupCall == null) {
				return;
			}
			TLRPC.TL_phone_checkGroupCall req = new TLRPC.TL_phone_checkGroupCall();
			req.call = groupCall.getInputGroupCall();
			req.source = mySource;
			checkRequestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
				if (shortPollRunnable == null || sharedInstance == null || groupCall == null) {
					return;
				}
				shortPollRunnable = null;
				checkRequestId = 0;
				if (response instanceof TLRPC.TL_boolFalse || error != null && error.code == 400) {
					createGroupInstance();
				} else {
					startGroupCheckShortpoll();
				}
			}));
		}, 4000);
	}

	private void cancelGroupCheckShortPoll() {
		if (checkRequestId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(checkRequestId, false);
			checkRequestId = 0;
		}
		if (shortPollRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
			shortPollRunnable = null;
		}
	}

	private void createGroupInstance() {
		if (tgVoip != null) {
			tgVoip.stopGroup();
		}
		cancelGroupCheckShortPoll();
		wasConnected = false;
		tgVoip = NativeInstance.makeGroup(this::startGroupCall, (uids, levels, voice) -> {
			if (sharedInstance == null || groupCall == null) {
				return;
			}
			groupCall.processVoiceLevelsUpdate(uids, levels, voice);
			float maxAmplitude = 0;
			boolean hasOther = false;
			for (int a = 0; a < uids.length; a++) {
				if (uids[a] == 0) {
					if (lastTypingTimeSend < SystemClock.uptimeMillis() - 5000 && levels[a] > 0.1f && voice[a]) {
						lastTypingTimeSend = SystemClock.uptimeMillis();
						TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
						req.action = new TLRPC.TL_speakingInGroupCallAction();
						req.peer = MessagesController.getInputPeer(chat);
						ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

						});
					}
					NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.webRtcMicAmplitudeEvent, levels[a]);
					continue;
				}
				hasOther = true;
				maxAmplitude = Math.max(maxAmplitude, levels[a]);
			}
			if (hasOther) {
				NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.webRtcSpeakerAmplitudeEvent, maxAmplitude);
				if (audioLevelsCallback != null) {
					audioLevelsCallback.run(uids, levels, voice);
				}
			}
		});
		tgVoip.setOnStateUpdatedListener(state -> {
			dispatchStateChanged(state == 1 ? STATE_ESTABLISHED : STATE_RECONNECTING);
			if (state == 0) {
				startGroupCheckShortpoll();
				if (playedConnectedSound && spPlayID == 0) {
					Utilities.globalQueue.postRunnable(() -> {
						if (spPlayID != 0) {
							soundPool.stop(spPlayID);
						}
						spPlayID = soundPool.play(spVoiceChatConnecting, 1.0f, 1.0f, 0, -1, 1);
					});
				}
			} else {
				cancelGroupCheckShortPoll();
				if (playedConnectedSound) {
					Utilities.globalQueue.postRunnable(() -> {
						if (spPlayID != 0) {
							soundPool.stop(spPlayID);
							spPlayID = 0;
						}
					});
					if (connectingSoundRunnable != null) {
						AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable);
						connectingSoundRunnable = null;
					}
				} else {
					Utilities.globalQueue.postRunnable(() -> soundPool.play(spVoiceChatStartId, 1.0f, 1.0f, 0, 0, 1));
					playedConnectedSound = true;
				}
				if (!wasConnected) {
					if (!micMute) {
						tgVoip.setMuteMicrophone(false);
					}
					wasConnected = true;
				}
			}
		});
		dispatchStateChanged(STATE_WAIT_INIT);
	}

	private void initiateActualEncryptedCall() {
		if (timeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
			timeoutRunnable = null;
		}
		try {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("InitCall: keyID=" + keyFingerprint);
			}
			SharedPreferences nprefs = MessagesController.getNotificationsSettings(currentAccount);
			Set<String> set = nprefs.getStringSet("calls_access_hashes", null);
			HashSet<String> hashes;
			if (set != null) {
				hashes = new HashSet<>(set);
			} else {
				hashes = new HashSet<>();
			}
			hashes.add(privateCall.id + " " + privateCall.access_hash + " " + System.currentTimeMillis());
			while (hashes.size() > 20) {
				String oldest = null;
				long oldestTime = Long.MAX_VALUE;
				Iterator<String> itr = hashes.iterator();
				while (itr.hasNext()) {
					String item = itr.next();
					String[] s = item.split(" ");
					if (s.length < 2) {
						itr.remove();
					} else {
						try {
							long t = Long.parseLong(s[2]);
							if (t < oldestTime) {
								oldestTime = t;
								oldest = item;
							}
						} catch (Exception x) {
							itr.remove();
						}
					}
				}
				if (oldest != null) {
					hashes.remove(oldest);
				}
			}
			nprefs.edit().putStringSet("calls_access_hashes", hashes).commit();

			boolean sysAecAvailable = false, sysNsAvailable = false;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				try {
					sysAecAvailable = AcousticEchoCanceler.isAvailable();
				} catch (Exception ignored) {
				}
				try {
					sysNsAvailable = NoiseSuppressor.isAvailable();
				} catch (Exception ignored) {
				}
			}

			final SharedPreferences preferences = MessagesController.getGlobalMainSettings();

			// config
			final MessagesController messagesController = MessagesController.getInstance(currentAccount);
			final double initializationTimeout = messagesController.callConnectTimeout / 1000.0;
			final double receiveTimeout = messagesController.callPacketTimeout / 1000.0;
			final int voipDataSaving = convertDataSavingMode(preferences.getInt("VoipDataSaving", VoIPHelper.getDataSavingDefault()));
			final Instance.ServerConfig serverConfig = Instance.getGlobalServerConfig();
			final boolean enableAec = !(sysAecAvailable && serverConfig.useSystemAec);
			final boolean enableNs = !(sysNsAvailable && serverConfig.useSystemNs);
			final String logFilePath = BuildVars.DEBUG_VERSION ? VoIPHelper.getLogFilePath("voip" + privateCall.id) : VoIPHelper.getLogFilePath(privateCall.id, false);
			final String statisLogFilePath = "";
			final Instance.Config config = new Instance.Config(initializationTimeout, receiveTimeout, voipDataSaving, privateCall.p2p_allowed, enableAec, enableNs, true, false, serverConfig.enableStunMarking, logFilePath, statisLogFilePath, privateCall.protocol.max_layer);

			// persistent state
			final String persistentStateFilePath = new File(ApplicationLoader.applicationContext.getFilesDir(), "voip_persistent_state.json").getAbsolutePath();

			// endpoints
			final boolean forceTcp = preferences.getBoolean("dbg_force_tcp_in_calls", false);
			final int endpointType = forceTcp ? Instance.ENDPOINT_TYPE_TCP_RELAY : Instance.ENDPOINT_TYPE_UDP_RELAY;
			final Instance.Endpoint[] endpoints = new Instance.Endpoint[privateCall.connections.size()];
			for (int i = 0; i < endpoints.length; i++) {
				final TLRPC.PhoneConnection connection = privateCall.connections.get(i);
				endpoints[i] = new Instance.Endpoint(connection instanceof TLRPC.TL_phoneConnectionWebrtc, connection.id, connection.ip, connection.ipv6, connection.port, endpointType, connection.peer_tag, connection.turn, connection.stun, connection.username, connection.password);
			}
			if (forceTcp) {
				AndroidUtilities.runOnUIThread(() -> Toast.makeText(VoIPService.this, "This call uses TCP which will degrade its quality.", Toast.LENGTH_SHORT).show());
			}

			// proxy
			Instance.Proxy proxy = null;
			if (preferences.getBoolean("proxy_enabled", false) && preferences.getBoolean("proxy_enabled_calls", false)) {
				final String server = preferences.getString("proxy_ip", null);
				final String secret = preferences.getString("proxy_secret", null);
				if (!TextUtils.isEmpty(server) && TextUtils.isEmpty(secret)) {
					proxy = new Instance.Proxy(server, preferences.getInt("proxy_port", 0), preferences.getString("proxy_user", null), preferences.getString("proxy_pass", null));
				}
			}

			// encryption key
			final Instance.EncryptionKey encryptionKey = new Instance.EncryptionKey(authKey, isOutgoing);

			boolean newAvailable = "2.7.7".compareTo(privateCall.protocol.library_versions.get(0)) <= 0;
			if (videoCapturer != 0 && !newAvailable) {
				NativeInstance.destroyVideoCapturer(videoCapturer);
				videoCapturer = 0;
				videoState = Instance.VIDEO_STATE_INACTIVE;
			}
			// init
			tgVoip = Instance.makeInstance(privateCall.protocol.library_versions.get(0), config, persistentStateFilePath, endpoints, proxy, getNetworkType(), encryptionKey, remoteSink, videoCapturer);
			tgVoip.setOnStateUpdatedListener(this::onConnectionStateChanged);
			tgVoip.setOnSignalBarsUpdatedListener(this::onSignalBarCountChanged);
			tgVoip.setOnSignalDataListener(this::onSignalingData);
			tgVoip.setOnRemoteMediaStateUpdatedListener(this::onMediaStateUpdated);
			tgVoip.setMuteMicrophone(micMute);

			if (newAvailable != isVideoAvailable) {
				isVideoAvailable = newAvailable;
				for (int a = 0; a < stateListeners.size(); a++) {
					StateListener l = stateListeners.get(a);
					l.onVideoAvailableChange(isVideoAvailable);
				}
			}
			videoCapturer = 0;

			AndroidUtilities.runOnUIThread(new Runnable() {
				@Override
				public void run() {
					if (tgVoip != null) {
						updateTrafficStats(null);
						AndroidUtilities.runOnUIThread(this, 5000);
					}
				}
			}, 5000);
		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("error starting call", x);
			}
			callFailed();
		}
	}

	protected void showNotification() {
		if (user != null) {
			showNotification(ContactsController.formatName(user.first_name, user.last_name), getRoundAvatarBitmap(user));
		} else {
			showNotification(chat.title, getRoundAvatarBitmap(chat));
		}
	}

	private void startConnectingSound() {
		Utilities.globalQueue.postRunnable(() -> {
			if (spPlayID != 0) {
				soundPool.stop(spPlayID);
			}
			spPlayID = soundPool.play(spConnectingId, 1, 1, 0, -1, 1);
			if (spPlayID == 0) {
				AndroidUtilities.runOnUIThread(connectingSoundRunnable = new Runnable() {
					@Override
					public void run() {
						if (sharedInstance == null) {
							return;
						}
						Utilities.globalQueue.postRunnable(() -> {
							if (spPlayID == 0) {
								spPlayID = soundPool.play(spConnectingId, 1, 1, 0, -1, 1);
							}
							if (spPlayID == 0) {
								AndroidUtilities.runOnUIThread(this, 100);
							} else {
								connectingSoundRunnable = null;
							}
						});
					}
				}, 100);
			}
		});
	}

	public void onSignalingData(byte[] data) {
		if (privateCall == null) {
			return;
		}
		TLRPC.TL_phone_sendSignalingData req = new TLRPC.TL_phone_sendSignalingData();
		req.peer = new TLRPC.TL_inputPhoneCall();
		req.peer.access_hash = privateCall.access_hash;
		req.peer.id = privateCall.id;
		req.data = data;
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

		});
	}

	protected void callFailed(String error) {
		if (privateCall != null) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("Discarding failed call");
			}
			TLRPC.TL_phone_discardCall req = new TLRPC.TL_phone_discardCall();
			req.peer = new TLRPC.TL_inputPhoneCall();
			req.peer.access_hash = privateCall.access_hash;
			req.peer.id = privateCall.id;
			req.duration = (int) (getCallDuration() / 1000);
			req.connection_id = tgVoip != null ? tgVoip.getPreferredRelayId() : 0;
			req.reason = new TLRPC.TL_phoneCallDiscardReasonDisconnect();
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error1) -> {
				if (error1 != null) {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.e("error on phone.discardCall: " + error1);
					}
				} else {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.d("phone.discardCall " + response);
					}
				}
			});
		}
		super.callFailed(error);
	}

	@Override
	public long getCallID() {
		return privateCall != null ? privateCall.id : 0;
	}

	public boolean isVideoAvailable() {
		return isVideoAvailable;
	}

	void onMediaButtonEvent(KeyEvent ev) {
		if (ev.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK || ev.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE || ev.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
			if (ev.getAction() == KeyEvent.ACTION_UP) {
				if (currentState == STATE_WAITING_INCOMING) {
					acceptIncomingCall();
				} else {
					setMicMute(!isMicMute(), false, true);
				}
			}
		}
	}

	public byte[] getGA() {
		return g_a;
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.appDidLogout) {
			callEnded();
		}
	}

	public void forceRating() {
		forceRating = true;
	}

	private String[] getEmoji() {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			os.write(authKey);
			os.write(g_a);
		} catch (IOException ignore) {
		}
		return EncryptionKeyEmojifier.emojifyForCall(Utilities.computeSHA256(os.toByteArray(), 0, os.size()));
	}

	@Override
	public void onConnectionStateChanged(int newState) {
		AndroidUtilities.runOnUIThread(() -> {
			if (newState == STATE_ESTABLISHED) {
				if (callStartTime == 0) {
					callStartTime = SystemClock.elapsedRealtime();
				}
				//peerCapabilities = tgVoip.getPeerCapabilities();
			}
			super.onConnectionStateChanged(newState);
		});
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public CallConnection getConnectionAndStartCall() {
		if (systemCallConnection == null) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("creating call connection");
			}
			systemCallConnection = new CallConnection();
			systemCallConnection.setInitializing();
			if (isOutgoing) {
				delayedStartOutgoingCall = () -> {
					delayedStartOutgoingCall = null;
					startOutgoingCall();
				};
				AndroidUtilities.runOnUIThread(delayedStartOutgoingCall, 2000);
			}
			systemCallConnection.setAddress(Uri.fromParts("tel", "+99084" + user.id, null), TelecomManager.PRESENTATION_ALLOWED);
			systemCallConnection.setCallerDisplayName(ContactsController.formatName(user.first_name, user.last_name), TelecomManager.PRESENTATION_ALLOWED);
		}
		return systemCallConnection;
	}
}
