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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRouter;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.LruCache;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.JoinCallAlert;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.VoIPFeedbackActivity;
import org.telegram.ui.VoIPFragment;
import org.telegram.ui.VoIPPermissionActivity;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.voiceengine.WebRtcAudioTrack;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SuppressLint("NewApi")
public class VoIPService extends Service implements SensorEventListener, AudioManager.OnAudioFocusChangeListener, VoIPController.ConnectionStateListener, NotificationCenter.NotificationCenterDelegate {

	public static final int CALL_MIN_LAYER = 65;

	public static final int STATE_HANGING_UP = 10;
	public static final int STATE_EXCHANGING_KEYS = 12;
	public static final int STATE_WAITING = 13;
	public static final int STATE_REQUESTING = 14;
	public static final int STATE_WAITING_INCOMING = 15;
	public static final int STATE_RINGING = 16;
	public static final int STATE_BUSY = 17;

	public static final int STATE_WAIT_INIT = Instance.STATE_WAIT_INIT;
	public static final int STATE_WAIT_INIT_ACK = Instance.STATE_WAIT_INIT_ACK;
	public static final int STATE_ESTABLISHED = Instance.STATE_ESTABLISHED;
	public static final int STATE_FAILED = Instance.STATE_FAILED;
	public static final int STATE_RECONNECTING = Instance.STATE_RECONNECTING;
	public static final int STATE_CREATING = 6;
	public static final int STATE_ENDED = 11;
	public static final String ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG";

	private static final int ID_ONGOING_CALL_NOTIFICATION = 201;
	private static final int ID_INCOMING_CALL_NOTIFICATION = 202;

	public static final int QUALITY_SMALL = 0;
	public static final int QUALITY_MEDIUM = 1;
	public static final int QUALITY_FULL = 2;

	public static final int CAPTURE_DEVICE_CAMERA = 0;
	public static final int CAPTURE_DEVICE_SCREEN = 1;

	public static final int DISCARD_REASON_HANGUP = 1;
	public static final int DISCARD_REASON_DISCONNECT = 2;
	public static final int DISCARD_REASON_MISSED = 3;
	public static final int DISCARD_REASON_LINE_BUSY = 4;

	public static final int AUDIO_ROUTE_EARPIECE = 0;
	public static final int AUDIO_ROUTE_SPEAKER = 1;
	public static final int AUDIO_ROUTE_BLUETOOTH = 2;

	private static final boolean USE_CONNECTION_SERVICE = isDeviceCompatibleWithConnectionServiceAPI();

	private int currentAccount = -1;
	private static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
	private static VoIPService sharedInstance;
	private static Runnable setModeRunnable;
	private static final Object sync = new Object();
	private NetworkInfo lastNetInfo;
	private int currentState = 0;
	private boolean wasConnected;

	private boolean reconnectScreenCapture;

	private TLRPC.Chat chat;

	private boolean isVideoAvailable;
	private boolean notificationsDisabled;
	private boolean switchingCamera;
	private boolean isFrontFaceCamera = true;
	private boolean isPrivateScreencast;
	private String lastError;
	private PowerManager.WakeLock proximityWakelock;
	private PowerManager.WakeLock cpuWakelock;
	private boolean isProximityNear;
	private boolean isHeadsetPlugged;
	private int previousAudioOutput = -1;
	private ArrayList<StateListener> stateListeners = new ArrayList<>();
	private MediaPlayer ringtonePlayer;
	private Vibrator vibrator;
	private SoundPool soundPool;
	private int spRingbackID;
	private int spFailedID;
	private int spEndId;
	private int spVoiceChatEndId;
	private int spVoiceChatStartId;
	private int spVoiceChatConnecting;
	private int spBusyId;
	private int spConnectingId;
	private int spPlayId;
	private int spStartRecordId;
	private int spAllowTalkId;
	private boolean needPlayEndSound;
	private boolean hasAudioFocus;
	private boolean micMute;
	private boolean unmutedByHold;
	private BluetoothAdapter btAdapter;
	private Instance.TrafficStats prevTrafficStats;
	private boolean isBtHeadsetConnected;
	private volatile boolean isCallEnded;

	private Runnable updateNotificationRunnable;

	private Runnable onDestroyRunnable;

	private Runnable switchingStreamTimeoutRunnable;

	private boolean playedConnectedSound;
	private boolean switchingStream;
	private boolean switchingAccount;

	public TLRPC.PhoneCall privateCall;
	public ChatObject.Call groupCall;

	public boolean currentGroupModeStreaming;

	private boolean createGroupCall;
	private int scheduleDate;
	private TLRPC.InputPeer groupCallPeer;
	public boolean hasFewPeers;
	private String joinHash;

	private int remoteVideoState = Instance.VIDEO_STATE_INACTIVE;
	private TLRPC.TL_dataJSON myParams;

	private int[] mySource = new int[2];
	private NativeInstance[] tgVoip = new NativeInstance[2];
	private long[] captureDevice = new long[2];
	private boolean[] destroyCaptureDevice = {true, true};
	private int[] videoState = {Instance.VIDEO_STATE_INACTIVE, Instance.VIDEO_STATE_INACTIVE};

	private long callStartTime;
	private boolean playingSound;
	private boolean isOutgoing;
	public boolean videoCall;
	private Runnable timeoutRunnable;

	private Boolean mHasEarpiece;
	private boolean wasEstablished;
	private int signalBarCount;
	private int remoteAudioState = Instance.AUDIO_STATE_ACTIVE;
	private boolean audioConfigured;
	private int audioRouteToSet = AUDIO_ROUTE_BLUETOOTH;
	private boolean speakerphoneStateToSet;
	private CallConnection systemCallConnection;
	private int callDiscardReason;
	private boolean bluetoothScoActive;
	private boolean bluetoothScoConnecting;
	private boolean needSwitchToBluetoothAfterScoActivates;
	private boolean didDeleteConnectionServiceContact;
	private Runnable connectingSoundRunnable;

	public String currentBluetoothDeviceName;

	public final SharedUIParams sharedUIParams = new SharedUIParams();

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

	private int classGuid;
	private volatile CountDownLatch groupCallBottomSheetLatch;

	private HashMap<String, Integer> currentStreamRequestTimestamp = new HashMap<>();
	public boolean micSwitching;

	private Runnable afterSoundRunnable = new Runnable() {
		@Override
		public void run() {

			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			VoipAudioManager vam = VoipAudioManager.get();
			am.abandonAudioFocus(VoIPService.this);
			am.unregisterMediaButtonEventReceiver(new ComponentName(VoIPService.this, VoIPMediaButtonReceiver.class));
			if (audioDeviceCallback != null) {
				am.unregisterAudioDeviceCallback(audioDeviceCallback);
			}
			if (!USE_CONNECTION_SERVICE && sharedInstance == null) {
				if (isBtHeadsetConnected) {
					am.stopBluetoothSco();
					am.setBluetoothScoOn(false);
					bluetoothScoActive = false;
					bluetoothScoConnecting = false;
				}
				vam.setSpeakerphoneOn(false);
			}

			Utilities.globalQueue.postRunnable(() -> soundPool.release());
			Utilities.globalQueue.postRunnable(setModeRunnable = () -> {
				synchronized (sync) {
					if (setModeRunnable == null) {
						return;
					}
					setModeRunnable = null;
				}
				try {
					am.setMode(AudioManager.MODE_NORMAL);
				} catch (SecurityException x) {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.e("Error setting audio more to normal", x);
					}
				}
			});
		}
	};

	boolean fetchingBluetoothDeviceName;
	private BluetoothProfile.ServiceListener serviceListener = new BluetoothProfile.ServiceListener() {
		@Override
		public void onServiceDisconnected(int profile) {

		}

		@Override
		public void onServiceConnected(int profile, BluetoothProfile proxy) {
			try {
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
					for (BluetoothDevice device : proxy.getConnectedDevices()) {
						if (proxy.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
							continue;
						}
						currentBluetoothDeviceName = device.getName();
						break;
					}
				}
				BluetoothAdapter.getDefaultAdapter().closeProfileProxy(profile, proxy);
				fetchingBluetoothDeviceName = false;
			} catch (Throwable e) {
				FileLog.e(e);
			}
		}
	};

	private AudioDeviceCallback audioDeviceCallback;

	private BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_HEADSET_PLUG.equals(intent.getAction())) {
				isHeadsetPlugged = intent.getIntExtra("state", 0) == 1;
				if (isHeadsetPlugged && proximityWakelock != null && proximityWakelock.isHeld()) {
					proximityWakelock.release();
				}
				if (isHeadsetPlugged) {
					AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
					VoipAudioManager vam = VoipAudioManager.get();
					if (vam.isSpeakerphoneOn()) {
						previousAudioOutput = 0;
					} else if (am.isBluetoothScoOn()) {
						previousAudioOutput = 2;
					} else {
						previousAudioOutput = 1;
					}
					setAudioOutput(1);
				} else {
					if (previousAudioOutput >= 0) {
						setAudioOutput(previousAudioOutput);
						previousAudioOutput = -1;
					}
				}
				isProximityNear = false;
				updateOutputGainControlState();
			} else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
				updateNetworkType();
			} else if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("bt headset state = " + intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0));
				}
				updateBluetoothHeadsetState(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED) == BluetoothProfile.STATE_CONNECTED);
			} else if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(intent.getAction())) {
				int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("Bluetooth SCO state updated: " + state);
				}
				if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED && isBtHeadsetConnected) {
					if (!btAdapter.isEnabled() || btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) != BluetoothProfile.STATE_CONNECTED) {
						updateBluetoothHeadsetState(false);
						return;
					}
				}
				bluetoothScoConnecting = state == AudioManager.SCO_AUDIO_STATE_CONNECTING;
				bluetoothScoActive = state == AudioManager.SCO_AUDIO_STATE_CONNECTED;
				if (bluetoothScoActive) {
					fetchBluetoothDeviceName();
					if (needSwitchToBluetoothAfterScoActivates) {
						needSwitchToBluetoothAfterScoActivates = false;
						AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
						VoipAudioManager vam = VoipAudioManager.get();
						vam.setSpeakerphoneOn(false);
						am.setBluetoothScoOn(true);
					}
				}
				for (VoIPService.StateListener l : stateListeners) {
					l.onAudioSettingsChanged();
				}
			} else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
				String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
				if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
					hangUp();
				}
			} else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
				for (int i = 0; i< stateListeners.size(); i++) {
					stateListeners.get(i).onScreenOnChange(true);
				}
			} else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
				for (int i = 0; i< stateListeners.size(); i++) {
					stateListeners.get(i).onScreenOnChange(false);
				}
			}
		}
	};

	public CountDownLatch getGroupCallBottomSheetLatch() {
		return groupCallBottomSheetLatch;
	}

	public boolean isFrontFaceCamera() {
		return isFrontFaceCamera;
	}

	public boolean isScreencast() {
		return isPrivateScreencast;
	}

	public void setMicMute(boolean mute, boolean hold, boolean send) {
		if (micMute == mute || micSwitching) {
			return;
		}
		micMute = mute;
		if (groupCall != null) {
			if (!send) {
				TLRPC.TL_groupCallParticipant self = groupCall.participants.get(getSelfId());
				if (self != null && self.muted && !self.can_self_unmute) {
					send = true;
				}
			}
			if (send) {
				editCallMember(UserConfig.getInstance(currentAccount).getCurrentUser(), mute, null, null, null, null);
				Utilities.globalQueue.postRunnable(updateNotificationRunnable = () -> {
					if (updateNotificationRunnable == null) {
						return;
					}
					updateNotificationRunnable = null;
					showNotification(chat.title, getRoundAvatarBitmap(chat));
				});
			}
		}
		unmutedByHold = !micMute && hold;
		if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
			tgVoip[CAPTURE_DEVICE_CAMERA].setMuteMicrophone(mute);
		}
		for (StateListener l : stateListeners) {
			l.onAudioSettingsChanged();
		}
	}

	public boolean mutedByAdmin() {
		ChatObject.Call call = groupCall;
		if (call != null) {
			long selfId = getSelfId();
			TLRPC.TL_groupCallParticipant participant = call.participants.get(selfId);
			if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(chat)) {
				return true;
			}
		}
		return false;
	}

	private final HashMap<String, TLRPC.TL_groupCallParticipant> waitingFrameParticipant = new HashMap<>();
	private final LruCache<String, ProxyVideoSink> proxyVideoSinkLruCache = new LruCache<String, ProxyVideoSink>(6) {
		@Override
		protected void entryRemoved(boolean evicted, String key, ProxyVideoSink oldValue, ProxyVideoSink newValue) {
			super.entryRemoved(evicted, key, oldValue, newValue);
			tgVoip[CAPTURE_DEVICE_CAMERA].removeIncomingVideoOutput(oldValue.nativeInstance);
		}
	};

	public boolean hasVideoCapturer() {
		return captureDevice[CAPTURE_DEVICE_CAMERA] != 0;
	}

	public void checkVideoFrame(TLRPC.TL_groupCallParticipant participant, boolean screencast) {
		String endpointId = screencast ? participant.presentationEndpoint : participant.videoEndpoint;
		if (endpointId == null) {
			return;
		}
		if ((screencast && participant.hasPresentationFrame != ChatObject.VIDEO_FRAME_NO_FRAME) || (!screencast && participant.hasCameraFrame != ChatObject.VIDEO_FRAME_NO_FRAME)) {
			return;
		}

		if (proxyVideoSinkLruCache.get(endpointId) != null || (remoteSinks.get(endpointId) != null && waitingFrameParticipant.get(endpointId) == null)) {
			if (screencast) {
				participant.hasPresentationFrame = ChatObject.VIDEO_FRAME_HAS_FRAME;
			} else {
				participant.hasCameraFrame = ChatObject.VIDEO_FRAME_HAS_FRAME;
			}
			return;
		}
		if (waitingFrameParticipant.containsKey(endpointId)) {
			waitingFrameParticipant.put(endpointId, participant);
			if (screencast) {
				participant.hasPresentationFrame = ChatObject.VIDEO_FRAME_REQUESTING;
			} else {
				participant.hasCameraFrame = ChatObject.VIDEO_FRAME_REQUESTING;
			}
			return;
		}
		if (screencast) {
			participant.hasPresentationFrame = ChatObject.VIDEO_FRAME_REQUESTING;
		} else {
			participant.hasCameraFrame = ChatObject.VIDEO_FRAME_REQUESTING;
		}
		waitingFrameParticipant.put(endpointId, participant);
		addRemoteSink(participant, screencast, new VideoSink() {
			@Override
			public void onFrame(VideoFrame frame) {
				VideoSink thisSink = this;
				if (frame != null && frame.getBuffer().getHeight() != 0 && frame.getBuffer().getWidth() != 0) {
					AndroidUtilities.runOnUIThread(() -> {
						TLRPC.TL_groupCallParticipant currentParticipant = waitingFrameParticipant.remove(endpointId);
						ProxyVideoSink proxyVideoSink = remoteSinks.get(endpointId);
						if (proxyVideoSink != null && proxyVideoSink.target == thisSink) {
							proxyVideoSinkLruCache.put(endpointId, proxyVideoSink);
							remoteSinks.remove(endpointId);
							proxyVideoSink.setTarget(null);
						}
						if (currentParticipant != null) {
							if (screencast) {
								currentParticipant.hasPresentationFrame = ChatObject.VIDEO_FRAME_HAS_FRAME;
							} else {
								currentParticipant.hasCameraFrame = ChatObject.VIDEO_FRAME_HAS_FRAME;
							}
						}
						if (groupCall != null) {
							groupCall.updateVisibleParticipants();
						}
					});
				}
			}
		}, null);
	}

	public void clearRemoteSinks() {
		proxyVideoSinkLruCache.evictAll();
	}

	public void setAudioRoute(int route) {
		if (route == AUDIO_ROUTE_SPEAKER) {
			setAudioOutput(0);
		} else if (route == AUDIO_ROUTE_EARPIECE) {
			setAudioOutput(1);
		} else if (route == AUDIO_ROUTE_BLUETOOTH) {
			setAudioOutput(2);
		}
	}

	public static class ProxyVideoSink implements VideoSink {
		private VideoSink target;
		private VideoSink background;

		private long nativeInstance;

		@Override
		synchronized public void onFrame(VideoFrame frame) {
			if (target != null) {
				target.onFrame(frame);
			}
			if (background != null) {
				background.onFrame(frame);
			}
		}

		synchronized public void setTarget(VideoSink newTarget) {
			if (target != newTarget) {
				if (target != null) {
					target.setParentSink(null);
				}
				target = newTarget;
				if (target != null) {
					target.setParentSink(this);
				}
			}
		}

		synchronized public void setBackground(VideoSink newBackground) {
			if (background != null) {
				background.setParentSink(null);
			}
			background = newBackground;
			if (background != null) {
				background.setParentSink(this);
			}
		}

		synchronized public void removeTarget(VideoSink target) {
			if (this.target == target) {
				this.target = null;
			}
		}

		synchronized public void removeBackground(VideoSink background) {
			if (this.background == background) {
				this.background = null;
			}
		}

		synchronized public void swap() {
			if (target != null && background != null) {
				target = background;
				background = null;
			}
		}
	}

	private ProxyVideoSink[] localSink = new ProxyVideoSink[2];
	private ProxyVideoSink[] remoteSink = new ProxyVideoSink[2];
	private ProxyVideoSink[] currentBackgroundSink = new ProxyVideoSink[2];
	private String[] currentBackgroundEndpointId = new String[2];

	private HashMap<String, ProxyVideoSink> remoteSinks = new HashMap<>();

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
		classGuid = ConnectionsManager.generateClassGuid();
		long userID = intent.getLongExtra("user_id", 0);
		long chatID = intent.getLongExtra("chat_id", 0);
		createGroupCall = intent.getBooleanExtra("createGroupCall", false);
		hasFewPeers = intent.getBooleanExtra("hasFewPeers", false);
		joinHash = intent.getStringExtra("hash");
		long peerChannelId = intent.getLongExtra("peerChannelId", 0);
		long peerChatId = intent.getLongExtra("peerChatId", 0);
		long peerUserId = intent.getLongExtra("peerUserId", 0);
		if (peerChatId != 0) {
			groupCallPeer = new TLRPC.TL_inputPeerChat();
			groupCallPeer.chat_id = peerChatId;
			groupCallPeer.access_hash = intent.getLongExtra("peerAccessHash", 0);
		} else if (peerChannelId != 0) {
			groupCallPeer = new TLRPC.TL_inputPeerChannel();
			groupCallPeer.channel_id = peerChannelId;
			groupCallPeer.access_hash = intent.getLongExtra("peerAccessHash", 0);
		} else if (peerUserId != 0) {
			groupCallPeer = new TLRPC.TL_inputPeerUser();
			groupCallPeer.user_id = peerUserId;
			groupCallPeer.access_hash = intent.getLongExtra("peerAccessHash", 0);
		}
		scheduleDate = intent.getIntExtra("scheduleDate", 0);

		isOutgoing = intent.getBooleanExtra("is_outgoing", false);
		videoCall = intent.getBooleanExtra("video_call", false);
		isVideoAvailable = intent.getBooleanExtra("can_video_call", false);
		notificationsDisabled = intent.getBooleanExtra("notifications_disabled", false);
		if (userID != 0) {
			user = MessagesController.getInstance(currentAccount).getUser(userID);
		}
		if (chatID != 0) {
			chat = MessagesController.getInstance(currentAccount).getChat(chatID);
			if (ChatObject.isChannel(chat)) {
				MessagesController.getInstance(currentAccount).startShortPoll(chat, classGuid, false);
			}
		}
		loadResources();
		for (int a = 0; a < localSink.length; a++) {
			localSink[a] = new ProxyVideoSink();
			remoteSink[a] = new ProxyVideoSink();
		}
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
			if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
				captureDevice[CAPTURE_DEVICE_CAMERA] = NativeInstance.createVideoCapturer(localSink[CAPTURE_DEVICE_CAMERA], isFrontFaceCamera ? 1 : 0);
				if (chatID != 0) {
					videoState[CAPTURE_DEVICE_CAMERA] = Instance.VIDEO_STATE_PAUSED;
				} else {
					videoState[CAPTURE_DEVICE_CAMERA] = Instance.VIDEO_STATE_ACTIVE;
				}
			} else {
				videoState[CAPTURE_DEVICE_CAMERA] = Instance.VIDEO_STATE_PAUSED;
			}
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
				startGroupCall(0, null, false);
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

	public static boolean hasRtmpStream() {
		return getSharedInstance() != null && getSharedInstance().groupCall != null && getSharedInstance().groupCall.call.rtmp_stream;
	}

	public static VoIPService getSharedInstance() {
		return sharedInstance;
	}

	public TLRPC.User getUser() {
		return user;
	}

	public TLRPC.Chat getChat() {
		return chat;
	}

	public void setNoiseSupressionEnabled(boolean enabled) {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] == null) {
			return;
		}
		tgVoip[CAPTURE_DEVICE_CAMERA].setNoiseSuppressionEnabled(enabled);
	}

	public void setGroupCallHash(String hash) {
		if (!currentGroupModeStreaming || TextUtils.isEmpty(hash) || hash.equals(joinHash)) {
			return;
		}
		joinHash = hash;
		createGroupInstance(CAPTURE_DEVICE_CAMERA, false);
	}

	public long getCallerId() {
		if (user != null) {
			return user.id;
		} else {
			return -chat.id;
		}
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
				req.source = mySource[CAPTURE_DEVICE_CAMERA];
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

	private boolean isRinging() {
		return currentState == STATE_WAITING_INCOMING;
	}

	public boolean isJoined() {
		return currentState != STATE_WAIT_INIT && currentState != STATE_CREATING;
	}

	public void requestVideoCall(boolean screencast) {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] == null) {
			return;
		}
		if (!screencast && captureDevice[CAPTURE_DEVICE_CAMERA] != 0) {
			tgVoip[CAPTURE_DEVICE_CAMERA].setupOutgoingVideoCreated(captureDevice[CAPTURE_DEVICE_CAMERA]);
			destroyCaptureDevice[CAPTURE_DEVICE_CAMERA] = false;
		} else {
			tgVoip[CAPTURE_DEVICE_CAMERA].setupOutgoingVideo(localSink[CAPTURE_DEVICE_CAMERA], screencast ? 2 : (isFrontFaceCamera ? 1 : 0));
		}
		isPrivateScreencast = screencast;
	}

	public void switchCamera() {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] == null || !tgVoip[CAPTURE_DEVICE_CAMERA].hasVideoCapturer() || switchingCamera) {
			if (captureDevice[CAPTURE_DEVICE_CAMERA] != 0 && !switchingCamera) {
				NativeInstance.switchCameraCapturer(captureDevice[CAPTURE_DEVICE_CAMERA], !isFrontFaceCamera);
			}
			return;
		}
		switchingCamera = true;
		tgVoip[CAPTURE_DEVICE_CAMERA].switchCamera(!isFrontFaceCamera);
	}

	public boolean isSwitchingCamera() {
		return switchingCamera;
	}

	public void createCaptureDevice(boolean screencast) {
		int index = screencast ? CAPTURE_DEVICE_SCREEN : CAPTURE_DEVICE_CAMERA;
		int deviceType;
		if (screencast) {
			deviceType = 2;
		} else {
			deviceType = isFrontFaceCamera ? 1 : 0;
		}
		if (groupCall == null) {
			if (!isPrivateScreencast && screencast) {
				setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
			}
			isPrivateScreencast = screencast;
			if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
				tgVoip[CAPTURE_DEVICE_CAMERA].clearVideoCapturer();
			}
		}
		if (index == CAPTURE_DEVICE_SCREEN) {
			if (groupCall != null) {
				if (captureDevice[index] != 0) {
					return;
				}
				captureDevice[index] = NativeInstance.createVideoCapturer(localSink[index], deviceType);
				createGroupInstance(CAPTURE_DEVICE_SCREEN, false);
				setVideoState(true, Instance.VIDEO_STATE_ACTIVE);
				AccountInstance.getInstance(currentAccount).getNotificationCenter().postNotificationName(NotificationCenter.groupCallScreencastStateChanged);
			} else {
				requestVideoCall(true);
				setVideoState(true, Instance.VIDEO_STATE_ACTIVE);
				if (VoIPFragment.getInstance() != null) {
					VoIPFragment.getInstance().onScreenCastStart();
				}
			}
		} else {
			if (captureDevice[index] != 0 || tgVoip[index] == null) {
				if (tgVoip[index] != null && captureDevice[index] != 0) {
					tgVoip[index].activateVideoCapturer(captureDevice[index]);
				}
				if (captureDevice[index] != 0) {
					return;
				}
			}
			captureDevice[index] = NativeInstance.createVideoCapturer(localSink[index], deviceType);
		}
	}

	public void setupCaptureDevice(boolean screencast, boolean micEnabled) {
		if (!screencast) {
			int index = screencast ? CAPTURE_DEVICE_SCREEN : CAPTURE_DEVICE_CAMERA;
			if (captureDevice[index] == 0 || tgVoip[index] == null) {
				return;
			}
			tgVoip[index].setupOutgoingVideoCreated(captureDevice[index]);
			destroyCaptureDevice[index] = false;
			videoState[index] = Instance.VIDEO_STATE_ACTIVE;
		}
		if (micMute == micEnabled) {
			setMicMute(!micEnabled, false, false);
			micSwitching = true;
		}
		if (groupCall != null) {
			editCallMember(UserConfig.getInstance(currentAccount).getCurrentUser(), !micEnabled, videoState[CAPTURE_DEVICE_CAMERA] != Instance.VIDEO_STATE_ACTIVE, null, null, () -> micSwitching = false);
		}
	}

	public void clearCamera() {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
			tgVoip[CAPTURE_DEVICE_CAMERA].clearVideoCapturer();
		}
	}

	public void setVideoState(boolean screencast, int state) {
		int index = screencast ? CAPTURE_DEVICE_SCREEN : CAPTURE_DEVICE_CAMERA;
		int trueIndex = groupCall != null ? index : CAPTURE_DEVICE_CAMERA;
		if (tgVoip[trueIndex] == null) {
			if (captureDevice[index] != 0) {
				videoState[trueIndex] = state;
				NativeInstance.setVideoStateCapturer(captureDevice[index], videoState[trueIndex]);
			} else if (state == Instance.VIDEO_STATE_ACTIVE && currentState != STATE_BUSY && currentState != STATE_ENDED) {
				captureDevice[index] = NativeInstance.createVideoCapturer(localSink[trueIndex], isFrontFaceCamera ? 1 : 0);
				videoState[trueIndex] = Instance.VIDEO_STATE_ACTIVE;
			}
			return;
		}
		videoState[trueIndex] = state;
		tgVoip[trueIndex].setVideoState(videoState[trueIndex]);
		if (captureDevice[index] != 0) {
			NativeInstance.setVideoStateCapturer(captureDevice[index], videoState[trueIndex]);
		}
		if (!screencast) {
			if (groupCall != null) {
				editCallMember(UserConfig.getInstance(currentAccount).getCurrentUser(), null, videoState[CAPTURE_DEVICE_CAMERA] != Instance.VIDEO_STATE_ACTIVE, null, null, null);
			}
			checkIsNear();
		}
	}

	public void stopScreenCapture() {
		if (groupCall == null || videoState[CAPTURE_DEVICE_SCREEN] != Instance.VIDEO_STATE_ACTIVE) {
			return;
		}
		TLRPC.TL_phone_leaveGroupCallPresentation req = new TLRPC.TL_phone_leaveGroupCallPresentation();
		req.call = groupCall.getInputGroupCall();
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
			if (response != null) {
				TLRPC.Updates updates = (TLRPC.Updates) response;
				MessagesController.getInstance(currentAccount).processUpdates(updates, false);
			}
		});
		NativeInstance instance = tgVoip[CAPTURE_DEVICE_SCREEN];
		if (instance != null) {
			Utilities.globalQueue.postRunnable(instance::stopGroup);
		}
		mySource[CAPTURE_DEVICE_SCREEN] = 0;
		tgVoip[CAPTURE_DEVICE_SCREEN] = null;
		destroyCaptureDevice[CAPTURE_DEVICE_SCREEN] = true;
		captureDevice[CAPTURE_DEVICE_SCREEN] = 0;
		videoState[CAPTURE_DEVICE_SCREEN] = Instance.VIDEO_STATE_INACTIVE;
		AccountInstance.getInstance(currentAccount).getNotificationCenter().postNotificationName(NotificationCenter.groupCallScreencastStateChanged);
	}

	public int getVideoState(boolean screencast) {
		return videoState[screencast ? CAPTURE_DEVICE_SCREEN : CAPTURE_DEVICE_CAMERA];
	}

	public void setSinks(VideoSink local, VideoSink remote) {
		setSinks(local, false, remote);
	}

	public void setSinks(VideoSink local, boolean screencast, VideoSink remote) {
		ProxyVideoSink localSink = this.localSink[screencast ? CAPTURE_DEVICE_SCREEN : CAPTURE_DEVICE_CAMERA];
		ProxyVideoSink remoteSink = this.remoteSink[screencast ? CAPTURE_DEVICE_SCREEN : CAPTURE_DEVICE_CAMERA];
		if (localSink != null) {
			localSink.setTarget(local);
		}
		if (remoteSink != null) {
			remoteSink.setTarget(remote);
		}
	}

	public void setLocalSink(VideoSink local, boolean screencast) {
		if (screencast) {
			//localSink[CAPTURE_DEVICE_SCREEN].setTarget(local);
		} else {
			localSink[CAPTURE_DEVICE_CAMERA].setTarget(local);
		}
	}

	public void setRemoteSink(VideoSink remote, boolean screencast) {
		remoteSink[screencast ? CAPTURE_DEVICE_SCREEN : CAPTURE_DEVICE_CAMERA].setTarget(remote);
	}

	public ProxyVideoSink addRemoteSink(TLRPC.TL_groupCallParticipant participant, boolean screencast, VideoSink remote, VideoSink background) {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] == null) {
			return null;
		}
		String endpointId = screencast ? participant.presentationEndpoint : participant.videoEndpoint;
		if (endpointId == null) {
			return null;
		}
		ProxyVideoSink sink = remoteSinks.get(endpointId);
		if (sink != null && sink.target == remote) {
			return sink;
		}
		if (sink == null) {
			sink = proxyVideoSinkLruCache.remove(endpointId);
		}
		if (sink == null) {
			sink = new ProxyVideoSink();
		}
		if (remote != null) {
			sink.setTarget(remote);
		}
		if (background != null) {
			sink.setBackground(background);
		}
		remoteSinks.put(endpointId, sink);
		sink.nativeInstance = tgVoip[CAPTURE_DEVICE_CAMERA].addIncomingVideoOutput(QUALITY_MEDIUM, endpointId, createSsrcGroups(screencast ? participant.presentation : participant.video), sink);
		return sink;
	}

	private NativeInstance.SsrcGroup[] createSsrcGroups(TLRPC.TL_groupCallParticipantVideo video) {
		if (video.source_groups.isEmpty()) {
			return null;
		}
		NativeInstance.SsrcGroup[] result = new NativeInstance.SsrcGroup[video.source_groups.size()];
		for (int a = 0; a < result.length; a++) {
			result[a] = new NativeInstance.SsrcGroup();
			TLRPC.TL_groupCallParticipantVideoSourceGroup group = video.source_groups.get(a);
			result[a].semantics = group.semantics;
			result[a].ssrcs = new int[group.sources.size()];
			for (int b = 0; b < result[a].ssrcs.length; b++) {
				result[a].ssrcs[b] = group.sources.get(b);
			}
		}
		return result;
	}

	public void requestFullScreen(TLRPC.TL_groupCallParticipant participant, boolean full, boolean screencast) {
		String endpointId = screencast ? participant.presentationEndpoint : participant.videoEndpoint;
		if (endpointId == null) {
			return;
		}
		if (full) {
			tgVoip[CAPTURE_DEVICE_CAMERA].setVideoEndpointQuality(endpointId, QUALITY_FULL);
		} else {
			tgVoip[CAPTURE_DEVICE_CAMERA].setVideoEndpointQuality(endpointId, QUALITY_MEDIUM);
		}
	}

	public void removeRemoteSink(TLRPC.TL_groupCallParticipant participant, boolean presentation) {
		if (presentation) {
			ProxyVideoSink sink = remoteSinks.remove(participant.presentationEndpoint);
			if (sink != null) {
				tgVoip[CAPTURE_DEVICE_CAMERA].removeIncomingVideoOutput(sink.nativeInstance);
			}
		} else {
			ProxyVideoSink sink = remoteSinks.remove(participant.videoEndpoint);
			if (sink != null) {
				tgVoip[CAPTURE_DEVICE_CAMERA].removeIncomingVideoOutput(sink.nativeInstance);
			}
		}
	}

	public boolean isFullscreen(TLRPC.TL_groupCallParticipant participant, boolean screencast) {
		return currentBackgroundSink[screencast ? CAPTURE_DEVICE_SCREEN : CAPTURE_DEVICE_CAMERA] != null && TextUtils.equals(currentBackgroundEndpointId[screencast ? CAPTURE_DEVICE_SCREEN : CAPTURE_DEVICE_CAMERA], screencast ? participant.presentationEndpoint : participant.videoEndpoint);
	}

	public void setBackgroundSinks(VideoSink local, VideoSink remote) {
		localSink[CAPTURE_DEVICE_CAMERA].setBackground(local);
		remoteSink[CAPTURE_DEVICE_CAMERA].setBackground(remote);
	}

	public void swapSinks() {
		localSink[CAPTURE_DEVICE_CAMERA].swap();
		remoteSink[CAPTURE_DEVICE_CAMERA].swap();
	}

	public boolean isHangingUp() {
		return currentState == STATE_HANGING_UP;
	}

	public void onSignalingData(TLRPC.TL_updatePhoneCallSignalingData data) {
		if (user == null || tgVoip[CAPTURE_DEVICE_CAMERA] == null || tgVoip[CAPTURE_DEVICE_CAMERA].isGroup() || getCallID() != data.phone_call_id) {
			return;
		}
		tgVoip[CAPTURE_DEVICE_CAMERA].onSignalingDataReceive(data.data);
	}

	public long getSelfId() {
		if (groupCallPeer == null) {
			return UserConfig.getInstance(currentAccount).clientUserId;
		}
		if (groupCallPeer instanceof TLRPC.TL_inputPeerUser) {
			return groupCallPeer.user_id;
		} else if (groupCallPeer instanceof TLRPC.TL_inputPeerChannel) {
			return -groupCallPeer.channel_id;
		} else {
			return -groupCallPeer.chat_id;
		}
	}

	public void onGroupCallParticipantsUpdate(TLRPC.TL_updateGroupCallParticipants update) {
		if (chat == null || groupCall == null || groupCall.call.id != update.call.id) {
			return;
		}
		long selfId = getSelfId();
		for (int a = 0, N = update.participants.size(); a < N; a++) {
			TLRPC.TL_groupCallParticipant participant = update.participants.get(a);
			if (participant.left) {
				if (participant.source != 0) {
					if (participant.source == mySource[CAPTURE_DEVICE_CAMERA]) {
						int selfCount = 0;
						for (int b = 0; b < N; b++) {
							TLRPC.TL_groupCallParticipant p = update.participants.get(b);
							if (p.self || p.source == mySource[CAPTURE_DEVICE_CAMERA]) {
								selfCount++;
							}
						}
						if (selfCount > 1) {
							hangUp(2);
							return;
						}
					}
				}
			} else if (MessageObject.getPeerId(participant.peer) == selfId) {
				if (participant.source != mySource[CAPTURE_DEVICE_CAMERA] && mySource[CAPTURE_DEVICE_CAMERA] != 0 && participant.source != 0) {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.d("source mismatch my = " + mySource[CAPTURE_DEVICE_CAMERA] + " psrc = " + participant.source);
					}
					hangUp(2);
					return;
				} else if (ChatObject.isChannel(chat) && currentGroupModeStreaming && participant.can_self_unmute) {
					switchingStream = true;
					createGroupInstance(CAPTURE_DEVICE_CAMERA, false);
				}
				if (participant.muted) {
					setMicMute(true, false, false);
				}
			}
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
		boolean newModeStreaming = false;
		if (myParams != null) {
			try {
				JSONObject object = new JSONObject(myParams.data);
				newModeStreaming = object.optBoolean("stream");
			} catch (Exception e) {
				FileLog.e(e);
			}
		}
		if ((currentState == STATE_WAIT_INIT || newModeStreaming != currentGroupModeStreaming) && myParams != null) {
			if (playedConnectedSound && newModeStreaming != currentGroupModeStreaming) {
				switchingStream = true;
			}
			currentGroupModeStreaming = newModeStreaming;
			try {
				if (newModeStreaming) {
					tgVoip[CAPTURE_DEVICE_CAMERA].prepareForStream(groupCall.call != null && groupCall.call.rtmp_stream);
				} else {
					tgVoip[CAPTURE_DEVICE_CAMERA].setJoinResponsePayload(myParams.data);
				}
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
					if (spPlayId != 0) {
						soundPool.stop(spPlayId);
					}
					spPlayId = soundPool.play(spRingbackID, 1, 1, 0, -1, 1);
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
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE).send();
		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("Error starting incall activity", x);
			}
		}
	}

	public void sendCallRating(int rating) {
		VoIPHelper.sendCallRating(privateCall.id, privateCall.access_hash, currentAccount, rating);
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

	public void setGroupCallPeer(TLRPC.InputPeer peer) {
		if (groupCall == null) {
			return;
		}
		groupCallPeer = peer;
		groupCall.setSelfPeer(groupCallPeer);
		TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(groupCall.chatId);
		if (chatFull != null) {
			chatFull.groupcall_default_join_as = groupCall.selfPeer;
			if (chatFull.groupcall_default_join_as != null) {
				if (chatFull instanceof TLRPC.TL_chatFull) {
					chatFull.flags |= 32768;
				} else {
					chatFull.flags |= 67108864;
				}
			} else {
				if (chatFull instanceof TLRPC.TL_chatFull) {
					chatFull.flags &=~ 32768;
				} else {
					chatFull.flags &=~ 67108864;
				}
			}
		}
		createGroupInstance(CAPTURE_DEVICE_CAMERA, true);
		if (videoState[CAPTURE_DEVICE_SCREEN] == Instance.VIDEO_STATE_ACTIVE) {
			createGroupInstance(CAPTURE_DEVICE_SCREEN, true);
		}
	}

	private void startGroupCall(int ssrc, String json, boolean create) {
		if (sharedInstance != this) {
			return;
		}
		if (createGroupCall) {
			groupCall = new ChatObject.Call();
			groupCall.call = new TLRPC.TL_groupCall();
			groupCall.call.participants_count = 0;
			groupCall.call.version = 1;
			groupCall.call.can_start_video = true;
			groupCall.call.can_change_join_muted = true;
			groupCall.chatId = chat.id;
			groupCall.currentAccount = AccountInstance.getInstance(currentAccount);
			groupCall.setSelfPeer(groupCallPeer);
			groupCall.createNoVideoParticipant();

			dispatchStateChanged(STATE_CREATING);
			TLRPC.TL_phone_createGroupCall req = new TLRPC.TL_phone_createGroupCall();
			req.peer = MessagesController.getInputPeer(chat);
			req.random_id = Utilities.random.nextInt();
			if (scheduleDate != 0) {
				req.schedule_date = scheduleDate;
				req.flags |= 2;
			}
			groupCallBottomSheetLatch = new CountDownLatch(1);
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
				if (response != null) {
					try {
						groupCallBottomSheetLatch.await(800, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						FileLog.e(e);
					}
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
								startGroupCall(0, null, false);
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
				if (groupCall != null) {
					groupCall.setSelfPeer(groupCallPeer);
				}
			}
			configureDeviceForCall();
			showNotification();
			AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall));
			createGroupInstance(CAPTURE_DEVICE_CAMERA, false);
		} else {
			if (getSharedInstance() == null || groupCall == null) {
				return;
			}
			dispatchStateChanged(STATE_WAIT_INIT);
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("initital source = " + ssrc);
			}
			TLRPC.TL_phone_joinGroupCall req = new TLRPC.TL_phone_joinGroupCall();
			req.muted = true;
			req.video_stopped = videoState[CAPTURE_DEVICE_CAMERA] != Instance.VIDEO_STATE_ACTIVE;
			req.call = groupCall.getInputGroupCall();
			req.params = new TLRPC.TL_dataJSON();
			req.params.data = json;
			if (!TextUtils.isEmpty(joinHash)) {
				req.invite_hash = joinHash;
				req.flags |= 2;
			}
			if (groupCallPeer != null) {
				req.join_as = groupCallPeer;
			} else {
				req.join_as = new TLRPC.TL_inputPeerUser();
				req.join_as.user_id = AccountInstance.getInstance(currentAccount).getUserConfig().getClientUserId();
			}
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
				if (response != null) {
					AndroidUtilities.runOnUIThread(() -> mySource[CAPTURE_DEVICE_CAMERA] = ssrc);
					TLRPC.Updates updates = (TLRPC.Updates) response;
					long selfId = getSelfId();
					for (int a = 0, N = updates.updates.size(); a < N; a++) {
						TLRPC.Update update = updates.updates.get(a);
						if (update instanceof TLRPC.TL_updateGroupCallParticipants) {
							TLRPC.TL_updateGroupCallParticipants updateGroupCallParticipants = (TLRPC.TL_updateGroupCallParticipants) update;
							for (int b = 0, N2 = updateGroupCallParticipants.participants.size(); b < N2; b++) {
								TLRPC.TL_groupCallParticipant participant = updateGroupCallParticipants.participants.get(b);
								if (MessageObject.getPeerId(participant.peer) == selfId) {
									AndroidUtilities.runOnUIThread(() -> mySource[CAPTURE_DEVICE_CAMERA] = participant.source);
									if (BuildVars.LOGS_ENABLED) {
										FileLog.d("join source = " + participant.source);
									}
									break;
								}
							}
						} else if (update instanceof TLRPC.TL_updateGroupCallConnection) {
							TLRPC.TL_updateGroupCallConnection updateGroupCallConnection = (TLRPC.TL_updateGroupCallConnection) update;
							if (!updateGroupCallConnection.presentation) {
								myParams = updateGroupCallConnection.params;
							}
						}
					}
					MessagesController.getInstance(currentAccount).processUpdates(updates, false);
					AndroidUtilities.runOnUIThread(() -> groupCall.loadMembers(create));
					startGroupCheckShortpoll();
				} else {
					AndroidUtilities.runOnUIThread(() -> {
						if ("JOIN_AS_PEER_INVALID".equals(error.text)) {
							TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
							if (chatFull != null) {
								if (chatFull instanceof TLRPC.TL_chatFull) {
									chatFull.flags &=~ 32768;
								} else {
									chatFull.flags &=~ 67108864;
								}
								chatFull.groupcall_default_join_as = null;
								JoinCallAlert.resetCache();
							}
							hangUp(2);
						} else if ("GROUPCALL_SSRC_DUPLICATE_MUCH".equals(error.text)) {
							createGroupInstance(CAPTURE_DEVICE_CAMERA, false);
						} else {
							if ("GROUPCALL_INVALID".equals(error.text)) {
								MessagesController.getInstance(currentAccount).loadFullChat(chat.id, 0, true);
							}
							NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 6, error.text);
							hangUp(0);
						}
					});
				}
			});
		}
	}

	private void startScreenCapture(int ssrc, String json) {
		if (getSharedInstance() == null || groupCall == null) {
			return;
		}
		mySource[CAPTURE_DEVICE_SCREEN] = 0;
		TLRPC.TL_phone_joinGroupCallPresentation req = new TLRPC.TL_phone_joinGroupCallPresentation();
		req.call = groupCall.getInputGroupCall();
		req.params = new TLRPC.TL_dataJSON();
		req.params.data = json;
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
			if (response != null) {
				AndroidUtilities.runOnUIThread(() -> mySource[CAPTURE_DEVICE_SCREEN] = ssrc);
				TLRPC.Updates updates = (TLRPC.Updates) response;
				AndroidUtilities.runOnUIThread(() -> {
					if (tgVoip[CAPTURE_DEVICE_SCREEN] != null) {
						long selfId = getSelfId();
						for (int a = 0, N = updates.updates.size(); a < N; a++) {
							TLRPC.Update update = updates.updates.get(a);
							if (update instanceof TLRPC.TL_updateGroupCallConnection) {
								TLRPC.TL_updateGroupCallConnection updateGroupCallConnection = (TLRPC.TL_updateGroupCallConnection) update;
								if (updateGroupCallConnection.presentation) {
									tgVoip[CAPTURE_DEVICE_SCREEN].setJoinResponsePayload(updateGroupCallConnection.params.data);
								}
							} else if (update instanceof TLRPC.TL_updateGroupCallParticipants) {
								TLRPC.TL_updateGroupCallParticipants updateGroupCallParticipants = (TLRPC.TL_updateGroupCallParticipants) update;
								for (int b = 0, N2 = updateGroupCallParticipants.participants.size(); b < N2; b++) {
									TLRPC.TL_groupCallParticipant participant = updateGroupCallParticipants.participants.get(b);
									if (MessageObject.getPeerId(participant.peer) == selfId) {
										if (participant.presentation != null) {
											if ((participant.presentation.flags & 2) != 0) {
												mySource[CAPTURE_DEVICE_SCREEN] = participant.presentation.audio_source;
											} else {
												for (int c = 0, N3 = participant.presentation.source_groups.size(); c < N3; c++) {
													TLRPC.TL_groupCallParticipantVideoSourceGroup sourceGroup = participant.presentation.source_groups.get(c);
													if (sourceGroup.sources.size() > 0) {
														mySource[CAPTURE_DEVICE_SCREEN] = sourceGroup.sources.get(0);
													}
												}
											}
										}
										break;
									}
								}
							}
						}
					}
				});
				MessagesController.getInstance(currentAccount).processUpdates(updates, false);
				startGroupCheckShortpoll();
			} else {
				AndroidUtilities.runOnUIThread(() -> {
					if ("GROUPCALL_VIDEO_TOO_MUCH".equals(error.text)) {
						groupCall.reloadGroupCall();
					} else if ("JOIN_AS_PEER_INVALID".equals(error.text)) {
						TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
						if (chatFull != null) {
							if (chatFull instanceof TLRPC.TL_chatFull) {
								chatFull.flags &=~ 32768;
							} else {
								chatFull.flags &=~ 67108864;
							}
							chatFull.groupcall_default_join_as = null;
							JoinCallAlert.resetCache();
						}
						hangUp(2);
					} else if ("GROUPCALL_SSRC_DUPLICATE_MUCH".equals(error.text)) {
						createGroupInstance(CAPTURE_DEVICE_SCREEN, false);
					} else {
						if ("GROUPCALL_INVALID".equals(error.text)) {
							MessagesController.getInstance(currentAccount).loadFullChat(chat.id, 0, true);
						}
					}
				});
			}
		});
	}

	private Runnable shortPollRunnable;
	private int checkRequestId;

	private void startGroupCheckShortpoll() {
		if (shortPollRunnable != null || sharedInstance == null || groupCall == null || (mySource[CAPTURE_DEVICE_CAMERA] == 0 && mySource[CAPTURE_DEVICE_SCREEN] == 0 && !(groupCall.call != null && groupCall.call.rtmp_stream))) {
			return;
		}
		AndroidUtilities.runOnUIThread(shortPollRunnable = () -> {
			if (shortPollRunnable == null || sharedInstance == null || groupCall == null || (mySource[CAPTURE_DEVICE_CAMERA] == 0 && mySource[CAPTURE_DEVICE_SCREEN] == 0 && !(groupCall.call != null && groupCall.call.rtmp_stream))) {
				return;
			}
			TLRPC.TL_phone_checkGroupCall req = new TLRPC.TL_phone_checkGroupCall();
			req.call = groupCall.getInputGroupCall();
			for (int a = 0; a < mySource.length; a++) {
				if (mySource[a] != 0) {
					req.sources.add(mySource[a]);
				}
			}
			checkRequestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
				if (shortPollRunnable == null || sharedInstance == null || groupCall == null) {
					return;
				}
				shortPollRunnable = null;
				checkRequestId = 0;
				boolean recreateCamera = false;
				boolean recreateScreenCapture = false;
				if (response instanceof TLRPC.Vector) {
					TLRPC.Vector vector = (TLRPC.Vector) response;
					if (mySource[CAPTURE_DEVICE_CAMERA] != 0 && req.sources.contains(mySource[CAPTURE_DEVICE_CAMERA])) {
						if (!vector.objects.contains(mySource[CAPTURE_DEVICE_CAMERA])) {
							recreateCamera = true;
						}
					}
					if (mySource[CAPTURE_DEVICE_SCREEN] != 0 && req.sources.contains(mySource[CAPTURE_DEVICE_SCREEN])) {
						if (!vector.objects.contains(mySource[CAPTURE_DEVICE_SCREEN])) {
							recreateScreenCapture = true;
						}
					}
				} else if (error != null && error.code == 400) {
					recreateCamera = true;
					if (mySource[CAPTURE_DEVICE_SCREEN] != 0 && req.sources.contains(mySource[CAPTURE_DEVICE_SCREEN])) {
						recreateScreenCapture = true;
					}
				}
				if (recreateCamera) {
					createGroupInstance(CAPTURE_DEVICE_CAMERA, false);
				}
				if (recreateScreenCapture) {
					createGroupInstance(CAPTURE_DEVICE_SCREEN, false);
				}
				if (mySource[CAPTURE_DEVICE_SCREEN] != 0 || mySource[CAPTURE_DEVICE_CAMERA] != 0 || (groupCall.call != null && groupCall.call.rtmp_stream)) {
					startGroupCheckShortpoll();
				}
			}));
		}, 4000);
	}

	private void cancelGroupCheckShortPoll() {
		if (mySource[CAPTURE_DEVICE_SCREEN] != 0 || mySource[CAPTURE_DEVICE_CAMERA] != 0) {
			return;
		}
		if (checkRequestId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(checkRequestId, false);
			checkRequestId = 0;
		}
		if (shortPollRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
			shortPollRunnable = null;
		}
	}

	private static class RequestedParticipant {
		public int audioSsrc;
		public TLRPC.TL_groupCallParticipant participant;

		public RequestedParticipant(TLRPC.TL_groupCallParticipant p, int ssrc) {
			participant = p;
			audioSsrc = ssrc;
		}
	}

	private void broadcastUnknownParticipants(long taskPtr, int[] unknown) {
		if (groupCall == null || tgVoip[CAPTURE_DEVICE_CAMERA] == null) {
			return;
		}
		long selfId = getSelfId();
		ArrayList<RequestedParticipant> participants = null;
		for (int a = 0, N = unknown.length; a < N; a++) {
			TLRPC.TL_groupCallParticipant p = groupCall.participantsBySources.get(unknown[a]);
			if (p == null) {
				p = groupCall.participantsByVideoSources.get(unknown[a]);
				if (p == null) {
					p = groupCall.participantsByPresentationSources.get(unknown[a]);
				}
			}
			if (p == null || MessageObject.getPeerId(p.peer) == selfId || p.source == 0) {
				continue;
			}
			if (participants == null) {
				participants = new ArrayList<>();
			}
			participants.add(new RequestedParticipant(p, unknown[a]));
		}
		if (participants != null) {
			int[] ssrcs = new int[participants.size()];
			for (int a = 0, N = participants.size(); a < N; a++) {
				RequestedParticipant p = participants.get(a);
				ssrcs[a] = p.audioSsrc;
			}
			tgVoip[CAPTURE_DEVICE_CAMERA].onMediaDescriptionAvailable(taskPtr, ssrcs);

			for (int a = 0, N = participants.size(); a < N; a++) {
				RequestedParticipant p = participants.get(a);
				if (p.participant.muted_by_you) {
					tgVoip[CAPTURE_DEVICE_CAMERA].setVolume(p.audioSsrc, 0);
				} else {
					tgVoip[CAPTURE_DEVICE_CAMERA].setVolume(p.audioSsrc, ChatObject.getParticipantVolume(p.participant) / 10000.0);
				}
			}
		}
	}

	private void createGroupInstance(int type, boolean switchAccount) {
		if (switchAccount) {
			mySource[type] = 0;
			if (type == CAPTURE_DEVICE_CAMERA) {
				switchingAccount = switchAccount;
			}
		}
		cancelGroupCheckShortPoll();
		if (type == CAPTURE_DEVICE_CAMERA) {
			wasConnected = false;
		} else if (!wasConnected) {
			reconnectScreenCapture = true;
			return;
		}
		boolean created = false;
		if (tgVoip[type] == null) {
			created = true;
			final String logFilePath = BuildVars.DEBUG_VERSION ? VoIPHelper.getLogFilePath("voip_" + type + "_" + groupCall.call.id) : VoIPHelper.getLogFilePath(groupCall.call.id, false);
			tgVoip[type] = NativeInstance.makeGroup(logFilePath, captureDevice[type], type == CAPTURE_DEVICE_SCREEN, type == CAPTURE_DEVICE_CAMERA && SharedConfig.noiseSupression, (ssrc, json) -> {
				if (type == CAPTURE_DEVICE_CAMERA) {
					startGroupCall(ssrc, json, true);
				} else {
					startScreenCapture(ssrc, json);
				}
			}, (uids, levels, voice) -> {
				if (sharedInstance == null || groupCall == null || type != CAPTURE_DEVICE_CAMERA) {
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
			}, (taskPtr, unknown) -> {
				if (sharedInstance == null || groupCall == null || type != CAPTURE_DEVICE_CAMERA) {
					return;
				}
				groupCall.processUnknownVideoParticipants(unknown, (ssrcs) -> {
					if (sharedInstance == null || groupCall == null) {
						return;
					}
					broadcastUnknownParticipants(taskPtr, unknown);
				});
			}, (timestamp, duration, videoChannel, quality) -> {
				if (type != CAPTURE_DEVICE_CAMERA) {
					return;
				}
				TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
				req.limit = 128 * 1024;
				TLRPC.TL_inputGroupCallStream inputGroupCallStream = new TLRPC.TL_inputGroupCallStream();
				inputGroupCallStream.call = groupCall.getInputGroupCall();
				inputGroupCallStream.time_ms = timestamp;
				if (duration == 500) {
					inputGroupCallStream.scale = 1;
				}
				if (videoChannel != 0) {
					inputGroupCallStream.flags |= 1;
					inputGroupCallStream.video_channel = videoChannel;
					inputGroupCallStream.video_quality = quality;
				}
				req.location = inputGroupCallStream;
				String key = videoChannel == 0 ? ("" + timestamp) : (videoChannel + "_" + timestamp + "_" + quality);
				int reqId = AccountInstance.getInstance(currentAccount).getConnectionsManager().sendRequest(req, (response, error, responseTime) -> {
					AndroidUtilities.runOnUIThread(() -> currentStreamRequestTimestamp.remove(key));
					if (tgVoip[type] == null) {
						return;
					}
					if (response != null) {
						TLRPC.TL_upload_file res = (TLRPC.TL_upload_file) response;
						tgVoip[type].onStreamPartAvailable(timestamp, res.bytes.buffer, res.bytes.limit(), responseTime, videoChannel, quality);
					} else {
						if ("GROUPCALL_JOIN_MISSING".equals(error.text)) {
							AndroidUtilities.runOnUIThread(() -> createGroupInstance(type, false));
						} else {
							int status;
							if ("TIME_TOO_BIG".equals(error.text) || error.text.startsWith("FLOOD_WAIT")) {
								status = 0;
							} else {
								status = -1;
							}
							tgVoip[type].onStreamPartAvailable(timestamp, null, status, responseTime, videoChannel, quality);
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors, ConnectionsManager.ConnectionTypeDownload, groupCall.call.stream_dc_id);
				AndroidUtilities.runOnUIThread(() -> currentStreamRequestTimestamp.put(key, reqId));
			}, (timestamp, duration, videoChannel, quality) -> {
				if (type != CAPTURE_DEVICE_CAMERA) {
					return;
				}
				AndroidUtilities.runOnUIThread(() -> {
					String key = videoChannel == 0 ? ("" + timestamp) : (videoChannel + "_" + timestamp + "_" + quality);
					Integer reqId = currentStreamRequestTimestamp.get(key);
					if (reqId != null) {
						AccountInstance.getInstance(currentAccount).getConnectionsManager().cancelRequest(reqId, true);
						currentStreamRequestTimestamp.remove(key);
					}
				});
			}, taskPtr -> {
				if (groupCall != null && groupCall.call != null && groupCall.call.rtmp_stream) {
					TLRPC.TL_phone_getGroupCallStreamChannels req = new TLRPC.TL_phone_getGroupCallStreamChannels();
					req.call = groupCall.getInputGroupCall();
					if (groupCall == null || groupCall.call == null || tgVoip[type] == null) {
						if (tgVoip[type] != null) {
							tgVoip[type].onRequestTimeComplete(taskPtr, 0);
						}
						return;
					}
					ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error, responseTime) -> {
						long currentTime = 0;
						if (error == null) {
							TLRPC.TL_phone_groupCallStreamChannels res = (TLRPC.TL_phone_groupCallStreamChannels) response;
							if (!res.channels.isEmpty()) {
								currentTime = res.channels.get(0).last_timestamp_ms;
							}
							if (!groupCall.loadedRtmpStreamParticipant) {
								groupCall.createRtmpStreamParticipant(res.channels);
								groupCall.loadedRtmpStreamParticipant = true;
							}
						}
						if (tgVoip[type] != null) {
							tgVoip[type].onRequestTimeComplete(taskPtr, currentTime);
						}
					}, ConnectionsManager.RequestFlagFailOnServerErrors, ConnectionsManager.ConnectionTypeDownload, groupCall.call.stream_dc_id);
				} else {
					if (tgVoip[type] != null) {
						tgVoip[type].onRequestTimeComplete(taskPtr, ConnectionsManager.getInstance(currentAccount).getCurrentTimeMillis());
					}
				}
			});
			tgVoip[type].setOnStateUpdatedListener((state, inTransition) -> updateConnectionState(type, state, inTransition));
		}
		tgVoip[type].resetGroupInstance(!created, false);
		if (captureDevice[type] != 0) {
			destroyCaptureDevice[type] = false;
		}
		if (type == CAPTURE_DEVICE_CAMERA) {
			dispatchStateChanged(STATE_WAIT_INIT);
		}
	}

	private void updateConnectionState(int type, int state, boolean inTransition) {
		if (type != CAPTURE_DEVICE_CAMERA) {
			return;
		}
		dispatchStateChanged(state == 1 || switchingStream ? STATE_ESTABLISHED : STATE_RECONNECTING);
		if (switchingStream && (state == 0 || state == 1 && inTransition)) {
			AndroidUtilities.runOnUIThread(switchingStreamTimeoutRunnable = () -> {
				if (switchingStreamTimeoutRunnable == null) {
					return;
				}
				switchingStream = false;
				updateConnectionState(type, 0, true);
				switchingStreamTimeoutRunnable = null;
			}, 3000);
		}
		if (state == 0) {
			startGroupCheckShortpoll();
			if (playedConnectedSound && spPlayId == 0 && !switchingStream && !switchingAccount) {
				Utilities.globalQueue.postRunnable(() -> {
					if (spPlayId != 0) {
						soundPool.stop(spPlayId);
					}
					spPlayId = soundPool.play(spVoiceChatConnecting, 1.0f, 1.0f, 0, -1, 1);
				});
			}
		} else {
			cancelGroupCheckShortPoll();
			if (!inTransition) {
				switchingStream = false;
				switchingAccount = false;
			}
			if (switchingStreamTimeoutRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(switchingStreamTimeoutRunnable);
				switchingStreamTimeoutRunnable = null;
			}
			if (playedConnectedSound) {
				Utilities.globalQueue.postRunnable(() -> {
					if (spPlayId != 0) {
						soundPool.stop(spPlayId);
						spPlayId = 0;
					}
				});
				if (connectingSoundRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable);
					connectingSoundRunnable = null;
				}
			} else {
				playConnectedSound();
			}
			if (!wasConnected) {
				wasConnected = true;
				if (reconnectScreenCapture) {
					createGroupInstance(CAPTURE_DEVICE_SCREEN, false);
					reconnectScreenCapture = false;
				}
				NativeInstance instance = tgVoip[CAPTURE_DEVICE_CAMERA];
				if (instance != null) {
					if (!micMute) {
						instance.setMuteMicrophone(false);
					}
				}
				setParticipantsVolume();
			}
		}
	}

	public void setParticipantsVolume() {
		NativeInstance instance = tgVoip[CAPTURE_DEVICE_CAMERA];
		if (instance != null) {
			for (int a = 0, N = groupCall.participants.size(); a < N; a++) {
				TLRPC.TL_groupCallParticipant participant = groupCall.participants.valueAt(a);
				if (participant.self || participant.source == 0 || !participant.can_self_unmute && participant.muted) {
					continue;
				}
				if (participant.muted_by_you) {
					setParticipantVolume(participant, 0);
				} else {
					setParticipantVolume(participant, ChatObject.getParticipantVolume(participant));
				}
			}
		}
	}

	public void setParticipantVolume(TLRPC.TL_groupCallParticipant participant, int volume) {
		tgVoip[CAPTURE_DEVICE_CAMERA].setVolume(participant.source, volume / 10000.0);
		if (participant.presentation != null && participant.presentation.audio_source != 0) {
			tgVoip[CAPTURE_DEVICE_CAMERA].setVolume(participant.presentation.audio_source, volume / 10000.0);
		}
	}

	public boolean isSwitchingStream() {
		return switchingStream;
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
			final String statsLogFilePath = VoIPHelper.getLogFilePath(privateCall.id, true);
			final Instance.Config config = new Instance.Config(initializationTimeout, receiveTimeout, voipDataSaving, privateCall.p2p_allowed, enableAec, enableNs, true, false, serverConfig.enableStunMarking, logFilePath, statsLogFilePath, privateCall.protocol.max_layer);

			// persistent state
			final String persistentStateFilePath = new File(ApplicationLoader.applicationContext.getCacheDir(), "voip_persistent_state.json").getAbsolutePath();

			// endpoints
			final boolean forceTcp = preferences.getBoolean("dbg_force_tcp_in_calls", false);
			final int endpointType = forceTcp ? Instance.ENDPOINT_TYPE_TCP_RELAY : Instance.ENDPOINT_TYPE_UDP_RELAY;
			final Instance.Endpoint[] endpoints = new Instance.Endpoint[privateCall.connections.size()];
			ArrayList<Long> reflectorIds = new ArrayList<>();
			for (int i = 0; i < endpoints.length; i++) {
				final TLRPC.PhoneConnection connection = privateCall.connections.get(i);
				endpoints[i] = new Instance.Endpoint(connection instanceof TLRPC.TL_phoneConnectionWebrtc, connection.id, connection.ip, connection.ipv6, connection.port, endpointType, connection.peer_tag, connection.turn, connection.stun, connection.username, connection.password, connection.tcp);
				if (connection instanceof TLRPC.TL_phoneConnection) {
					reflectorIds.add(((TLRPC.TL_phoneConnection) connection).id);
				}
			}
			if (!reflectorIds.isEmpty()) {
				Collections.sort(reflectorIds);
				HashMap<Long, Integer> reflectorIdMapping = new HashMap<>();
				for (int i = 0; i < reflectorIds.size(); i++) {
					reflectorIdMapping.put(reflectorIds.get(i), i + 1);
				}
				for (int i = 0; i < endpoints.length; i++) {
					endpoints[i].reflectorId = reflectorIdMapping.getOrDefault(endpoints[i].id, 0);
				}
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
			if (captureDevice[CAPTURE_DEVICE_CAMERA] != 0 && !newAvailable) {
				NativeInstance.destroyVideoCapturer(captureDevice[CAPTURE_DEVICE_CAMERA]);
				captureDevice[CAPTURE_DEVICE_CAMERA] = 0;
				videoState[CAPTURE_DEVICE_CAMERA] = Instance.VIDEO_STATE_INACTIVE;
			}
			if (!isOutgoing) {
				if (videoCall && (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)) {
					captureDevice[CAPTURE_DEVICE_CAMERA] = NativeInstance.createVideoCapturer(localSink[CAPTURE_DEVICE_CAMERA], isFrontFaceCamera ? 1 : 0);
					videoState[CAPTURE_DEVICE_CAMERA] = Instance.VIDEO_STATE_ACTIVE;
				} else {
					videoState[CAPTURE_DEVICE_CAMERA] = Instance.VIDEO_STATE_INACTIVE;
				}
			}
			// init
			tgVoip[CAPTURE_DEVICE_CAMERA] = Instance.makeInstance(privateCall.protocol.library_versions.get(0), config, persistentStateFilePath, endpoints, proxy, getNetworkType(), encryptionKey, remoteSink[CAPTURE_DEVICE_CAMERA], captureDevice[CAPTURE_DEVICE_CAMERA], (uids, levels, voice) -> {
				if (sharedInstance == null || privateCall == null) {
					return;
				}
				NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.webRtcMicAmplitudeEvent, levels[0]);
				NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.webRtcSpeakerAmplitudeEvent, levels[1]);
			});
			tgVoip[CAPTURE_DEVICE_CAMERA].setOnStateUpdatedListener(this::onConnectionStateChanged);
			tgVoip[CAPTURE_DEVICE_CAMERA].setOnSignalBarsUpdatedListener(this::onSignalBarCountChanged);
			tgVoip[CAPTURE_DEVICE_CAMERA].setOnSignalDataListener(this::onSignalingData);
			tgVoip[CAPTURE_DEVICE_CAMERA].setOnRemoteMediaStateUpdatedListener((audioState, videoState) -> AndroidUtilities.runOnUIThread(() -> {
				remoteAudioState = audioState;
				remoteVideoState = videoState;
				checkIsNear();

				for (int a = 0; a < stateListeners.size(); a++) {
					StateListener l = stateListeners.get(a);
					l.onMediaStateUpdated(audioState, videoState);
				}
			}));
			tgVoip[CAPTURE_DEVICE_CAMERA].setMuteMicrophone(micMute);

			if (newAvailable != isVideoAvailable) {
				isVideoAvailable = newAvailable;
				for (int a = 0; a < stateListeners.size(); a++) {
					StateListener l = stateListeners.get(a);
					l.onVideoAvailableChange(isVideoAvailable);
				}
			}
			destroyCaptureDevice[CAPTURE_DEVICE_CAMERA] = false;

			AndroidUtilities.runOnUIThread(new Runnable() {
				@Override
				public void run() {
					if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
						updateTrafficStats(tgVoip[CAPTURE_DEVICE_CAMERA], null);
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

	public void playConnectedSound() {
		Utilities.globalQueue.postRunnable(() -> soundPool.play(spVoiceChatStartId, 1.0f, 1.0f, 0, 0, 1));
		playedConnectedSound = true;
	}

	private void startConnectingSound() {
		Utilities.globalQueue.postRunnable(() -> {
			if (spPlayId != 0) {
				soundPool.stop(spPlayId);
			}
			spPlayId = soundPool.play(spConnectingId, 1, 1, 0, -1, 1);
			if (spPlayId == 0) {
				AndroidUtilities.runOnUIThread(connectingSoundRunnable = new Runnable() {
					@Override
					public void run() {
						if (sharedInstance == null) {
							return;
						}
						Utilities.globalQueue.postRunnable(() -> {
							if (spPlayId == 0) {
								spPlayId = soundPool.play(spConnectingId, 1, 1, 0, -1, 1);
							}
							if (spPlayId == 0) {
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

	public boolean isVideoAvailable() {
		return isVideoAvailable;
	}

	void onMediaButtonEvent(KeyEvent ev) {
		if (ev == null) {
			return;
		}
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

	public boolean hasEarpiece() {
		if (USE_CONNECTION_SERVICE) {
			if (systemCallConnection != null && systemCallConnection.getCallAudioState() != null) {
				int routeMask = systemCallConnection.getCallAudioState().getSupportedRouteMask();
				return (routeMask & (CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_WIRED_HEADSET)) != 0;
			}
		}
		if (((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getPhoneType() != TelephonyManager.PHONE_TYPE_NONE) {
			return true;
		}
		if (mHasEarpiece != null) {
			return mHasEarpiece;
		}

		// not calculated yet, do it now
		try {
			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			Method method = AudioManager.class.getMethod("getDevicesForStream", Integer.TYPE);
			Field field = AudioManager.class.getField("DEVICE_OUT_EARPIECE");
			int earpieceFlag = field.getInt(null);
			int bitmaskResult = (int) method.invoke(am, AudioManager.STREAM_VOICE_CALL);

			// check if masked by the earpiece flag
			if ((bitmaskResult & earpieceFlag) == earpieceFlag) {
				mHasEarpiece = Boolean.TRUE;
			} else {
				mHasEarpiece = Boolean.FALSE;
			}
		} catch (Throwable error) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("Error while checking earpiece! ", error);
			}
			mHasEarpiece = Boolean.TRUE;
		}

		return mHasEarpiece;
	}

	private int getStatsNetworkType() {
		int netType = StatsController.TYPE_WIFI;
		if (lastNetInfo != null) {
			if (lastNetInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
				netType = lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE;
			}
		}
		return netType;
	}

	protected void setSwitchingCamera(boolean switching, boolean isFrontFace) {
		switchingCamera = switching;
		if (!switching) {
			isFrontFaceCamera = isFrontFace;
			for (int a = 0; a < stateListeners.size(); a++) {
				StateListener l = stateListeners.get(a);
				l.onCameraSwitch(isFrontFaceCamera);
			}
		}
	}

	protected void onCameraFirstFrameAvailable() {
		for (int a = 0; a < stateListeners.size(); a++) {
			StateListener l = stateListeners.get(a);
			l.onCameraFirstFrameAvailable();
		}
	}

	public void registerStateListener(StateListener l) {
		if (stateListeners.contains(l)) {
			return;
		}
		stateListeners.add(l);
		if (currentState != 0) {
			l.onStateChanged(currentState);
		}
		if (signalBarCount != 0) {
			l.onSignalBarsCountChanged(signalBarCount);
		}
	}

	public void unregisterStateListener(StateListener l) {
		stateListeners.remove(l);
	}

	public void editCallMember(TLObject object, Boolean mute, Boolean muteVideo, Integer volume, Boolean raiseHand, Runnable onComplete) {
		if (object == null || groupCall == null) {
			return;
		}
		TLRPC.TL_phone_editGroupCallParticipant req = new TLRPC.TL_phone_editGroupCallParticipant();
		req.call = groupCall.getInputGroupCall();
		if (object instanceof TLRPC.User) {
			TLRPC.User user = (TLRPC.User) object;
			if (UserObject.isUserSelf(user) && groupCallPeer != null) {
				req.participant = groupCallPeer;
			} else {
				req.participant = MessagesController.getInputPeer(user);
				if (BuildVars.LOGS_ENABLED) {
					FileLog.d("edit group call part id = " + req.participant.user_id + " access_hash = " + req.participant.user_id);
				}
			}
		} else if (object instanceof TLRPC.Chat) {
			TLRPC.Chat chat = (TLRPC.Chat) object;
			req.participant = MessagesController.getInputPeer(chat);
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("edit group call part id = " + (req.participant.chat_id != 0 ? req.participant.chat_id : req.participant.channel_id)  + " access_hash = " + req.participant.access_hash);
			}
		}
		if (mute != null) {
			req.muted = mute;
			req.flags |= 1;
		}
		if (volume != null) {
			req.volume = volume;
			req.flags |= 2;
		}
		if (raiseHand != null) {
			req.raise_hand = raiseHand;
			req.flags |= 4;
		}
		if (muteVideo != null) {
			req.video_stopped = muteVideo;
			req.flags |= 8;
		}
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("edit group call flags = " + req.flags);
		}
		int account = currentAccount;
		AccountInstance.getInstance(account).getConnectionsManager().sendRequest(req, (response, error) -> {
			if (response != null) {
				AccountInstance.getInstance(account).getMessagesController().processUpdates((TLRPC.Updates) response, false);
			} else if (error != null) {
				if ("GROUPCALL_VIDEO_TOO_MUCH".equals(error.text)) {
					groupCall.reloadGroupCall();
				}
			}
			if (onComplete != null) {
				AndroidUtilities.runOnUIThread(onComplete);
			}
		});
	}

	public boolean isMicMute() {
		return micMute;
	}

	public void toggleSpeakerphoneOrShowRouteSheet(Context context, boolean fromOverlayWindow) {
		toggleSpeakerphoneOrShowRouteSheet(context, fromOverlayWindow, null);
	}

	public void switchToSpeaker() {
		AndroidUtilities.runOnUIThread(() -> {
			VoipAudioManager vam = VoipAudioManager.get();
			if ((isBluetoothHeadsetConnected() && hasEarpiece()) || isHeadsetPlugged || isSpeakerphoneOn()) {
				return;
			}
			vam.setSpeakerphoneOn(true);
			vam.isBluetoothAndSpeakerOnAsync((isBluetoothOn, isSpeakerOn) -> {
				updateOutputGainControlState();
				for (StateListener l : stateListeners) {
					l.onAudioSettingsChanged();
				}
			});
		}, 500);
	}

	public void toggleSpeakerphoneOrShowRouteSheet(Context context, boolean fromOverlayWindow, Integer selectedPos) {
		if (isBluetoothHeadsetConnected() && hasEarpiece()) {
			BottomSheet.Builder builder = new BottomSheet.Builder(context)
					.setTitle(LocaleController.getString("VoipOutputDevices", R.string.VoipOutputDevices), true)
					.selectedPos(selectedPos)
					.setCellType(selectedPos != null ? BottomSheet.Builder.CELL_TYPE_CALL : 0)
					.setItems(new CharSequence[]{
									LocaleController.getString("VoipAudioRoutingSpeaker", R.string.VoipAudioRoutingSpeaker),
									isHeadsetPlugged ? LocaleController.getString("VoipAudioRoutingHeadset", R.string.VoipAudioRoutingHeadset) : LocaleController.getString("VoipAudioRoutingEarpiece", R.string.VoipAudioRoutingEarpiece),
									currentBluetoothDeviceName != null ? currentBluetoothDeviceName : LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth)},
							new int[]{R.drawable.msg_call_speaker,
									isHeadsetPlugged ? R.drawable.calls_menu_headset : R.drawable.msg_call_earpiece,
									R.drawable.msg_call_bluetooth}, (dialog, which) -> {
								if (getSharedInstance() == null) {
									return;
								}
								setAudioOutput(which);
							});

			BottomSheet bottomSheet = builder.create();
			bottomSheet.setOnShowListener(dialog -> {
				for (int i = 0; i < bottomSheet.getItemViews().size(); i++) {
					bottomSheet.setItemColor(i, Theme.getColor(Theme.key_dialogTextBlack), Theme.getColor(Theme.key_dialogTextBlack));
				}
				if (selectedPos != null) {
					int selectedColor = Theme.getColor(Theme.key_dialogTextLink);
					bottomSheet.setItemColor(selectedPos, selectedColor, selectedColor);
				}
			});
			if (fromOverlayWindow) {
				if (Build.VERSION.SDK_INT >= 26) {
					bottomSheet.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
				} else {
					bottomSheet.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
				}
			}
			builder.show();
			return;
		}
		if (USE_CONNECTION_SERVICE && systemCallConnection != null && systemCallConnection.getCallAudioState() != null) {
			if (hasEarpiece()) {
				systemCallConnection.setAudioRoute(systemCallConnection.getCallAudioState().getRoute() == CallAudioState.ROUTE_SPEAKER ? CallAudioState.ROUTE_WIRED_OR_EARPIECE : CallAudioState.ROUTE_SPEAKER);
			} else {
				systemCallConnection.setAudioRoute(systemCallConnection.getCallAudioState().getRoute() == CallAudioState.ROUTE_BLUETOOTH ? CallAudioState.ROUTE_WIRED_OR_EARPIECE : CallAudioState.ROUTE_BLUETOOTH);
			}
		} else if (audioConfigured && !USE_CONNECTION_SERVICE) {
			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			VoipAudioManager vam = VoipAudioManager.get();
			if (hasEarpiece()) {
				vam.setSpeakerphoneOn(!vam.isSpeakerphoneOn());
			} else {
				am.setBluetoothScoOn(!am.isBluetoothScoOn());
			}
			vam.isBluetoothAndSpeakerOnAsync((isBluetoothOn, isSpeakerOn) -> {
				updateOutputGainControlState();
				for (StateListener l : stateListeners) {
					l.onAudioSettingsChanged();
				}
			});
			return;
		} else {
			speakerphoneStateToSet = !speakerphoneStateToSet;
		}
		for (StateListener l : stateListeners) {
			l.onAudioSettingsChanged();
		}
	}

	public void setAudioOutput(int which) {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("setAudioOutput " + which);
		}
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		VoipAudioManager vam = VoipAudioManager.get();
		if (USE_CONNECTION_SERVICE && systemCallConnection != null) {
			switch (which) {
				case 2:
					systemCallConnection.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
					break;
				case 1:
					systemCallConnection.setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
					break;
				case 0:
					systemCallConnection.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
					break;
			}
		} else if (audioConfigured && !USE_CONNECTION_SERVICE) {
			switch (which) {
				case 2:
					if (!bluetoothScoActive) {
						needSwitchToBluetoothAfterScoActivates = true;
						try {
							am.startBluetoothSco();
						} catch (Throwable e) {
							FileLog.e(e);
						}
					} else {
						am.setBluetoothScoOn(true);
						vam.setSpeakerphoneOn(false);
					}
					audioRouteToSet = AUDIO_ROUTE_BLUETOOTH;
					break;
				case 1:
					needSwitchToBluetoothAfterScoActivates = false;
					if (bluetoothScoActive || bluetoothScoConnecting) {
						am.stopBluetoothSco();
						bluetoothScoActive = false;
						bluetoothScoConnecting = false;
					}
					vam.setSpeakerphoneOn(false);
					am.setBluetoothScoOn(false);
					audioRouteToSet = AUDIO_ROUTE_EARPIECE;
					break;
				case 0:
					needSwitchToBluetoothAfterScoActivates = false;
					if (bluetoothScoActive || bluetoothScoConnecting) {
						am.stopBluetoothSco();
						bluetoothScoActive = false;
						bluetoothScoConnecting = false;
					}
					am.setBluetoothScoOn(false);
					vam.setSpeakerphoneOn(true);
					audioRouteToSet = AUDIO_ROUTE_SPEAKER;
					break;
			}
			updateOutputGainControlState();
		} else {
			switch (which) {
				case 2:
					audioRouteToSet = AUDIO_ROUTE_BLUETOOTH;
					speakerphoneStateToSet = false;
					break;
				case 1:
					audioRouteToSet = AUDIO_ROUTE_EARPIECE;
					speakerphoneStateToSet = false;
					break;
				case 0:
					audioRouteToSet = AUDIO_ROUTE_SPEAKER;
					speakerphoneStateToSet = true;
					break;
			}
		}
		for (StateListener l : stateListeners) {
			l.onAudioSettingsChanged();
		}
	}

	public boolean isSpeakerphoneOn() {
		if (USE_CONNECTION_SERVICE && systemCallConnection != null && systemCallConnection.getCallAudioState() != null) {
			int route = systemCallConnection.getCallAudioState().getRoute();
			return hasEarpiece() ? route == CallAudioState.ROUTE_SPEAKER : route == CallAudioState.ROUTE_BLUETOOTH;
		} else if (audioConfigured && !USE_CONNECTION_SERVICE) {
			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			VoipAudioManager vam = VoipAudioManager.get();
			return hasEarpiece() ? vam.isSpeakerphoneOn() : am.isBluetoothScoOn();
		}
		return speakerphoneStateToSet;
	}

	public int getCurrentAudioRoute() {
		if (USE_CONNECTION_SERVICE) {
			if (systemCallConnection != null && systemCallConnection.getCallAudioState() != null) {
				switch (systemCallConnection.getCallAudioState().getRoute()) {
					case CallAudioState.ROUTE_BLUETOOTH:
						return AUDIO_ROUTE_BLUETOOTH;
					case CallAudioState.ROUTE_EARPIECE:
					case CallAudioState.ROUTE_WIRED_HEADSET:
						return AUDIO_ROUTE_EARPIECE;
					case CallAudioState.ROUTE_SPEAKER:
						return AUDIO_ROUTE_SPEAKER;
				}
			}
			return audioRouteToSet;
		}
		if (audioConfigured) {
			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			VoipAudioManager vam = VoipAudioManager.get();
			if (am.isBluetoothScoOn()) {
				return AUDIO_ROUTE_BLUETOOTH;
			} else if (vam.isSpeakerphoneOn()) {
				return AUDIO_ROUTE_SPEAKER;
			} else {
				return AUDIO_ROUTE_EARPIECE;
			}
		}
		return audioRouteToSet;
	}

	public String getDebugString() {
		return tgVoip[CAPTURE_DEVICE_CAMERA] != null ? tgVoip[CAPTURE_DEVICE_CAMERA].getDebugInfo() : "";
	}

	public long getCallDuration() {
		if (callStartTime == 0) {
			return 0;
		}
		return SystemClock.elapsedRealtime() - callStartTime;
	}

	public void stopRinging() {
		synchronized (sync) {
			if (ringtonePlayer != null) {
				ringtonePlayer.stop();
				ringtonePlayer.release();
				ringtonePlayer = null;
			}
		}
		if (vibrator != null) {
			vibrator.cancel();
			vibrator = null;
		}
	}

	private void showNotification(String name, Bitmap photo) {
		Intent intent = new Intent(this, LaunchActivity.class).setAction(groupCall != null ? "voip_chat" : "voip");
		if (groupCall != null) {
			intent.putExtra("currentAccount", currentAccount);
		}
		Notification.Builder builder = new Notification.Builder(this)
				.setContentText(name)
				.setContentIntent(PendingIntent.getActivity(this, 50, intent, PendingIntent.FLAG_MUTABLE));
		if (groupCall != null) {
			builder.setContentTitle(ChatObject.isChannelOrGiga(chat) ? LocaleController.getString("VoipLiveStream", R.string.VoipLiveStream) : LocaleController.getString("VoipVoiceChat", R.string.VoipVoiceChat));
			builder.setSmallIcon(isMicMute() ? R.drawable.voicechat_muted : R.drawable.voicechat_active);
		} else {
			builder.setContentTitle(LocaleController.getString("VoipOutgoingCall", R.string.VoipOutgoingCall));
			builder.setSmallIcon(R.drawable.ic_call);
            builder.setOngoing(true);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			Intent endIntent = new Intent(this, VoIPActionsReceiver.class);
			endIntent.setAction(getPackageName() + ".END_CALL");
			if (groupCall != null) {
				builder.addAction(R.drawable.ic_call_end_white_24dp, ChatObject.isChannelOrGiga(chat) ? LocaleController.getString("VoipChannelLeaveAlertTitle", R.string.VoipChannelLeaveAlertTitle) : LocaleController.getString("VoipGroupLeaveAlertTitle", R.string.VoipGroupLeaveAlertTitle), PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
			} else {
				builder.addAction(R.drawable.ic_call_end_white_24dp, LocaleController.getString("VoipEndCall", R.string.VoipEndCall), PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
			}
			builder.setPriority(Notification.PRIORITY_MAX);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			builder.setShowWhen(false);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			builder.setColor(0xff282e31);
			builder.setColorized(true);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setColor(0xff2ca5e0);
		}
		if (Build.VERSION.SDK_INT >= 26) {
			NotificationsController.checkOtherNotificationsChannel();
			builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
		}
		if (photo != null) {
			builder.setLargeIcon(photo);
		}
		try {
			startForeground(ID_ONGOING_CALL_NOTIFICATION, builder.getNotification());
		} catch (Exception e) {
			if (photo != null && e instanceof IllegalArgumentException) {
				showNotification(name, null);
			}
		}
	}

	private void startRingtoneAndVibration(long chatID) {
		SharedPreferences prefs = MessagesController.getNotificationsSettings(currentAccount);
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		boolean needRing = am.getRingerMode() != AudioManager.RINGER_MODE_SILENT;
		if (needRing) {
			if (ringtonePlayer != null) {
				return;
			}
			synchronized (sync) {
				if (ringtonePlayer != null) {
					return;
				}
				ringtonePlayer = new MediaPlayer();
				ringtonePlayer.setOnPreparedListener(mediaPlayer -> {
					try {
						ringtonePlayer.start();
					} catch (Throwable e) {
						FileLog.e(e);
					}
				});
				ringtonePlayer.setLooping(true);
				if (isHeadsetPlugged) {
					ringtonePlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
				} else {
					ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
					if (!USE_CONNECTION_SERVICE) {
						int focusResult = am.requestAudioFocus(this, AudioManager.STREAM_RING, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
						hasAudioFocus = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
					}
				}
				try {
					String notificationUri;
					if (prefs.getBoolean("custom_" + chatID, false)) {
						notificationUri = prefs.getString("ringtone_path_" + chatID, null);
					} else {
						notificationUri = prefs.getString("CallsRingtonePath", null);
					}
					Uri ringtoneUri;
					boolean isDafaultUri = false;
					if (notificationUri == null) {
						ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
						isDafaultUri = true;
					} else {
						Uri defaultUri = Settings.System.DEFAULT_RINGTONE_URI;
						if (defaultUri != null && notificationUri.equalsIgnoreCase(defaultUri.getPath())) {
							ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
							isDafaultUri = true;
						} else {
							ringtoneUri = Uri.parse(notificationUri);
						}
					}
					FileLog.d("start ringtone with " + isDafaultUri + " " + ringtoneUri);
					ringtonePlayer.setDataSource(this, ringtoneUri);
					ringtonePlayer.prepareAsync();
				} catch (Exception e) {
					FileLog.e(e);
					if (ringtonePlayer != null) {
						ringtonePlayer.release();
						ringtonePlayer = null;
					}
				}
				int vibrate;
				if (prefs.getBoolean("custom_" + chatID, false)) {
					vibrate = prefs.getInt("calls_vibrate_" + chatID, 0);
				} else {
					vibrate = prefs.getInt("vibrate_calls", 0);
				}
				if ((vibrate != 2 && vibrate != 4 && (am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE || am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL)) || (vibrate == 4 && am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)) {
					vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
					long duration = 700;
					if (vibrate == 1) {
						duration /= 2;
					} else if (vibrate == 3) {
						duration *= 2;
					}
					vibrator.vibrate(new long[]{0, duration, 500}, 0);
				}
			}
		}
	}

	@Override
	public void onDestroy() {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("=============== VoIPService STOPPING ===============");
		}
		stopForeground(true);
		stopRinging();
		if (currentAccount >= 0) {
			if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
				MessagesController.getInstance(currentAccount).ignoreSetOnline = false;
			}
			NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout);
		}
		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		if (proximity != null) {
			sm.unregisterListener(this);
		}
		if (proximityWakelock != null && proximityWakelock.isHeld()) {
			proximityWakelock.release();
		}
		if (updateNotificationRunnable != null) {
			Utilities.globalQueue.cancelRunnable(updateNotificationRunnable);
			updateNotificationRunnable = null;
		}
		if (switchingStreamTimeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(switchingStreamTimeoutRunnable);
			switchingStreamTimeoutRunnable = null;
		}
		unregisterReceiver(receiver);
		if (timeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
			timeoutRunnable = null;
		}
		super.onDestroy();
		sharedInstance = null;
		Arrays.fill(mySource, 0);
		cancelGroupCheckShortPoll();
		AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didEndCall));
		if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
			StatsController.getInstance(currentAccount).incrementTotalCallsTime(getStatsNetworkType(), (int) (getCallDuration() / 1000) % 5);
			onTgVoipPreStop();
			if (tgVoip[CAPTURE_DEVICE_CAMERA].isGroup()) {
				NativeInstance instance = tgVoip[CAPTURE_DEVICE_CAMERA];
				Utilities.globalQueue.postRunnable(instance::stopGroup);
				for (HashMap.Entry<String, Integer> entry : currentStreamRequestTimestamp.entrySet()) {
					AccountInstance.getInstance(currentAccount).getConnectionsManager().cancelRequest(entry.getValue(), true);
				}
				currentStreamRequestTimestamp.clear();
			} else {
				Instance.FinalState state = tgVoip[CAPTURE_DEVICE_CAMERA].stop();
				updateTrafficStats(tgVoip[CAPTURE_DEVICE_CAMERA], state.trafficStats);
				onTgVoipStop(state);
			}
			prevTrafficStats = null;
			callStartTime = 0;
			tgVoip[CAPTURE_DEVICE_CAMERA] = null;
			Instance.destroyInstance();
		}
		if (tgVoip[CAPTURE_DEVICE_SCREEN] != null) {
			NativeInstance instance = tgVoip[CAPTURE_DEVICE_SCREEN];
			Utilities.globalQueue.postRunnable(instance::stopGroup);
			tgVoip[CAPTURE_DEVICE_SCREEN] = null;
		}
		for (int a = 0; a < captureDevice.length; a++) {
			if (captureDevice[a] != 0) {
				if (destroyCaptureDevice[a]) {
					NativeInstance.destroyVideoCapturer(captureDevice[a]);
				}
				captureDevice[a] = 0;
			}
		}
		cpuWakelock.release();
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (!playingSound) {
			VoipAudioManager vam = VoipAudioManager.get();
			if (!USE_CONNECTION_SERVICE) {
				if (isBtHeadsetConnected || bluetoothScoActive || bluetoothScoConnecting) {
					am.stopBluetoothSco();
					am.setBluetoothScoOn(false);
					vam.setSpeakerphoneOn(false);
					bluetoothScoActive = false;
					bluetoothScoConnecting = false;
				}
				if (onDestroyRunnable == null) {
					Utilities.globalQueue.postRunnable(setModeRunnable = () -> {
						synchronized (sync) {
							if (setModeRunnable == null) {
								return;
							}
							setModeRunnable = null;
						}
						try {
							am.setMode(AudioManager.MODE_NORMAL);
						} catch (SecurityException x) {
							if (BuildVars.LOGS_ENABLED) {
								FileLog.e("Error setting audio more to normal", x);
							}
						}
					});
				}
				am.abandonAudioFocus(this);
			}
			try {
				am.unregisterMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));
			} catch (Exception e) {
				FileLog.e(e);
			}
			if (audioDeviceCallback != null) {
				am.unregisterAudioDeviceCallback(audioDeviceCallback);
			}

			Utilities.globalQueue.postRunnable(() -> {
				if (soundPool != null) {
					soundPool.release();
				}
			});
		}
		if (hasAudioFocus) {
			am.abandonAudioFocus(this);
		}

		if (USE_CONNECTION_SERVICE) {
			if (!didDeleteConnectionServiceContact) {
				ContactsController.getInstance(currentAccount).deleteConnectionServiceContact();
			}
			if (systemCallConnection != null && !playingSound) {
				systemCallConnection.destroy();
			}
		}

		VoIPHelper.lastCallTime = SystemClock.elapsedRealtime();

		setSinks(null, null);
		if (onDestroyRunnable != null) {
			onDestroyRunnable.run();
		}
		if (currentAccount >= 0) {
			ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
			if (ChatObject.isChannel(chat)) {
				MessagesController.getInstance(currentAccount).startShortPoll(chat, classGuid, true);
			}
		}
	}

	public long getCallID() {
		return privateCall != null ? privateCall.id : 0;
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

	public void declineIncomingCall(int reason, final Runnable onDone) {
		if (groupCall != null) {
			stopScreenCapture();
		}
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
		req.connection_id = tgVoip[CAPTURE_DEVICE_CAMERA] != null ? tgVoip[CAPTURE_DEVICE_CAMERA].getPreferredRelayId() : 0;
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

	public void declineIncomingCall() {
		declineIncomingCall(DISCARD_REASON_HANGUP, null);
	}

	private Class<? extends Activity> getUIActivityClass() {
		return LaunchActivity.class;
	}

	@TargetApi(Build.VERSION_CODES.O)
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

	private void startRinging() {
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
			showIncomingNotification(ContactsController.formatName(user.first_name, user.last_name), user, privateCall.video, 0);
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("Showing incoming call notification");
			}
		} else {
			startRingtoneAndVibration(user.id);
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("Starting incall activity for incoming call");
			}
			try {
				PendingIntent.getActivity(VoIPService.this, 12345, new Intent(VoIPService.this, LaunchActivity.class).setAction("voip"), PendingIntent.FLAG_MUTABLE).send();
			} catch (Exception x) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("Error starting incall activity", x);
				}
			}
		}
	}

	public void startRingtoneAndVibration() {
		if (!startedRinging) {
			startRingtoneAndVibration(user.id);
			startedRinging = true;
		}
	}

	private void updateServerConfig() {
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

	private void showNotification() {
		if (user != null) {
			showNotification(ContactsController.formatName(user.first_name, user.last_name), getRoundAvatarBitmap(user));
		} else {
			showNotification(chat.title, getRoundAvatarBitmap(chat));
		}
	}

	private void onTgVoipPreStop() {
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

	public static String convertStreamToString(InputStream is) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = reader.readLine()) != null) {
			sb.append(line).append("\n");
		}
		reader.close();
		return sb.toString();
	}

	public static String getStringFromFile(String filePath) throws Exception {
		File fl = new File(filePath);
		FileInputStream fin = new FileInputStream(fl);
		String ret = convertStreamToString(fin);
		fin.close();
		return ret;
	}

	public boolean hasRate() {
		return needRateCall || forceRating;
	}

	private void onTgVoipStop(Instance.FinalState finalState) {
		if (user == null) {
			return;
		}
		if (TextUtils.isEmpty(finalState.debugLog)) {
			try {
				finalState.debugLog = getStringFromFile(VoIPHelper.getLogFilePath(privateCall.id, true));
			} catch (Exception e) {
				e.printStackTrace();
			}
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

	private void initializeAccountRelatedThings() {
		updateServerConfig();
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appDidLogout);
		ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
	}

	@SuppressLint("InvalidWakeLockTag")
	@Override
	public void onCreate() {
		super.onCreate();
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("=============== VoIPService STARTING ===============");
		}
		try {
			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			if (am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) != null) {
				int outFramesPerBuffer = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
				Instance.setBufferSize(outFramesPerBuffer);
			} else {
				Instance.setBufferSize(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2);
			}

			cpuWakelock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "telegram-voip");
			cpuWakelock.acquire();

			btAdapter = am.isBluetoothScoAvailableOffCall() ? BluetoothAdapter.getDefaultAdapter() : null;

			IntentFilter filter = new IntentFilter();
			filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
			if (!USE_CONNECTION_SERVICE) {
				filter.addAction(ACTION_HEADSET_PLUG);
				if (btAdapter != null) {
					filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
					filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
				}
				filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
				filter.addAction(Intent.ACTION_SCREEN_ON);
				filter.addAction(Intent.ACTION_SCREEN_OFF);
			}
			registerReceiver(receiver, filter);
			fetchBluetoothDeviceName();

			if (audioDeviceCallback == null) {
				try {
					audioDeviceCallback = new AudioDeviceCallback() {
						@Override
						public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
							checkUpdateBluetoothHeadset();
						}

						@Override
						public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
							checkUpdateBluetoothHeadset();
						}
					};
				} catch (Throwable e) {
					//java.lang.NoClassDefFoundError on some devices
					FileLog.e(e);
					audioDeviceCallback = null;
				}
			}
			if (audioDeviceCallback != null) {
				am.registerAudioDeviceCallback(audioDeviceCallback, new Handler(Looper.getMainLooper()));
			}
			am.registerMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));

			checkUpdateBluetoothHeadset();
		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("error initializing voip controller", x);
			}
			callFailed();
		}
		if (callIShouldHavePutIntoIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationsController.checkOtherNotificationsChannel();
			Notification.Builder bldr = new Notification.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
					.setContentTitle(LocaleController.getString("VoipOutgoingCall", R.string.VoipOutgoingCall))
					.setShowWhen(false);
			if (groupCall != null) {
				bldr.setSmallIcon(isMicMute() ? R.drawable.voicechat_muted : R.drawable.voicechat_active);
			} else {
				bldr.setSmallIcon(R.drawable.ic_call);
			}
			startForeground(ID_ONGOING_CALL_NOTIFICATION, bldr.build());
		}
	}

	private void checkUpdateBluetoothHeadset() {
		if (!USE_CONNECTION_SERVICE && btAdapter != null && btAdapter.isEnabled()) {
			try {
				MediaRouter mr = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
				AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (Build.VERSION.SDK_INT < 24) {
					int headsetState = btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
					updateBluetoothHeadsetState(headsetState == BluetoothProfile.STATE_CONNECTED);
					for (StateListener l : stateListeners) {
						l.onAudioSettingsChanged();
					}
				} else {
					MediaRouter.RouteInfo ri = mr.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
					if (ri.getDeviceType() == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH) {
						int headsetState = btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
						updateBluetoothHeadsetState(headsetState == BluetoothProfile.STATE_CONNECTED);
						for (StateListener l : stateListeners) {
							l.onAudioSettingsChanged();
						}
					} else {
						updateBluetoothHeadsetState(am.isBluetoothA2dpOn());
					}
				}
			} catch (Throwable e) {
				FileLog.e(e);
			}
		}
	}

	private void loadResources() {
		if (Build.VERSION.SDK_INT >= 21) {
			WebRtcAudioTrack.setAudioTrackUsageAttribute(AudioAttributes.USAGE_VOICE_COMMUNICATION);
		}
		Utilities.globalQueue.postRunnable(() -> {
			soundPool = new SoundPool(1, AudioManager.STREAM_VOICE_CALL, 0);
			spConnectingId = soundPool.load(this, R.raw.voip_connecting, 1);
			spRingbackID = soundPool.load(this, R.raw.voip_ringback, 1);
			spFailedID = soundPool.load(this, R.raw.voip_failed, 1);
			spEndId = soundPool.load(this, R.raw.voip_end, 1);
			spBusyId = soundPool.load(this, R.raw.voip_busy, 1);
			spVoiceChatEndId = soundPool.load(this, R.raw.voicechat_leave, 1);
			spVoiceChatStartId = soundPool.load(this, R.raw.voicechat_join, 1);
			spVoiceChatConnecting = soundPool.load(this, R.raw.voicechat_connecting, 1);
			spAllowTalkId = soundPool.load(this, R.raw.voip_onallowtalk, 1);
			spStartRecordId = soundPool.load(this, R.raw.voip_recordstart, 1);
		});
	}

	private void dispatchStateChanged(int state) {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("== Call " + getCallID() + " state changed to " + state + " ==");
		}
		currentState = state;
		if (USE_CONNECTION_SERVICE && state == STATE_ESTABLISHED /*&& !wasEstablished*/ && systemCallConnection != null) {
			systemCallConnection.setActive();
		}
		for (int a = 0; a < stateListeners.size(); a++) {
			StateListener l = stateListeners.get(a);
			l.onStateChanged(state);
		}
	}

	private void updateTrafficStats(NativeInstance instance, Instance.TrafficStats trafficStats) {
		if (trafficStats == null) {
			trafficStats = instance.getTrafficStats();
		}
		final long wifiSentDiff = trafficStats.bytesSentWifi - (prevTrafficStats != null ? prevTrafficStats.bytesSentWifi : 0);
		final long wifiRecvdDiff = trafficStats.bytesReceivedWifi - (prevTrafficStats != null ? prevTrafficStats.bytesReceivedWifi : 0);
		final long mobileSentDiff = trafficStats.bytesSentMobile - (prevTrafficStats != null ? prevTrafficStats.bytesSentMobile : 0);
		final long mobileRecvdDiff = trafficStats.bytesReceivedMobile - (prevTrafficStats != null ? prevTrafficStats.bytesReceivedMobile : 0);
		prevTrafficStats = trafficStats;
		if (wifiSentDiff > 0) {
			StatsController.getInstance(currentAccount).incrementSentBytesCount(StatsController.TYPE_WIFI, StatsController.TYPE_CALLS, wifiSentDiff);
		}
		if (wifiRecvdDiff > 0) {
			StatsController.getInstance(currentAccount).incrementReceivedBytesCount(StatsController.TYPE_WIFI, StatsController.TYPE_CALLS, wifiRecvdDiff);
		}
		if (mobileSentDiff > 0) {
			StatsController.getInstance(currentAccount).incrementSentBytesCount(lastNetInfo != null && lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE, StatsController.TYPE_CALLS, mobileSentDiff);
		}
		if (mobileRecvdDiff > 0) {
			StatsController.getInstance(currentAccount).incrementReceivedBytesCount(lastNetInfo != null && lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE, StatsController.TYPE_CALLS, mobileRecvdDiff);
		}
	}

	@SuppressLint("InvalidWakeLockTag")
	private void configureDeviceForCall() {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("configureDeviceForCall, route to set = " + audioRouteToSet);
		}

		if (Build.VERSION.SDK_INT >= 21) {
			WebRtcAudioTrack.setAudioTrackUsageAttribute(hasRtmpStream() ? AudioAttributes.USAGE_MEDIA : AudioAttributes.USAGE_VOICE_COMMUNICATION);
			WebRtcAudioTrack.setAudioStreamType(hasRtmpStream() ? AudioManager.USE_DEFAULT_STREAM_TYPE : AudioManager.STREAM_VOICE_CALL);
		}

		needPlayEndSound = true;
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (!USE_CONNECTION_SERVICE) {
			Utilities.globalQueue.postRunnable(() -> {
				try {
					if (hasRtmpStream()) {
						am.setMode(AudioManager.MODE_NORMAL);
						am.setBluetoothScoOn(false);
						AndroidUtilities.runOnUIThread(() -> {
							if (!MediaController.getInstance().isMessagePaused()) {
								MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
							}
						});
						return;
					}

					am.setMode(AudioManager.MODE_IN_COMMUNICATION);
				} catch (Exception e) {
					FileLog.e(e);
				}
				AndroidUtilities.runOnUIThread(() -> {
					int focusResult = am.requestAudioFocus(VoIPService.this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
					hasAudioFocus = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
					final VoipAudioManager vam = VoipAudioManager.get();
					if (isBluetoothHeadsetConnected() && hasEarpiece()) {
						switch (audioRouteToSet) {
							case AUDIO_ROUTE_BLUETOOTH:
								if (!bluetoothScoActive) {
									needSwitchToBluetoothAfterScoActivates = true;
									try {
										am.startBluetoothSco();
									} catch (Throwable e) {
										FileLog.e(e);
									}
								} else {
									am.setBluetoothScoOn(true);
									vam.setSpeakerphoneOn(false);
								}
								break;
							case AUDIO_ROUTE_EARPIECE:
								am.setBluetoothScoOn(false);
								vam.setSpeakerphoneOn(false);
								break;
							case AUDIO_ROUTE_SPEAKER:
								am.setBluetoothScoOn(false);
								vam.setSpeakerphoneOn(true);
								break;
						}
					} else if (isBluetoothHeadsetConnected()) {
						am.setBluetoothScoOn(speakerphoneStateToSet);
					} else {
						vam.setSpeakerphoneOn(speakerphoneStateToSet);
						if (speakerphoneStateToSet) {
							audioRouteToSet = AUDIO_ROUTE_SPEAKER;
						} else {
							audioRouteToSet = AUDIO_ROUTE_EARPIECE;
						}
						if (lastSensorEvent != null) {
							//For the case when the phone was put to the ear before configureDeviceForCall.
							onSensorChanged(lastSensorEvent);
						}
					}
					updateOutputGainControlState();
					audioConfigured = true;
				});
			});
		}

		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		try {
			if (proximity != null) {
				proximityWakelock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "telegram-voip-prx");
				sm.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
			}
		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("Error initializing proximity sensor", x);
			}
		}
	}

	private void fetchBluetoothDeviceName() {
		if (fetchingBluetoothDeviceName) {
			return;
		}
		try {
			currentBluetoothDeviceName = null;
			fetchingBluetoothDeviceName = true;
			BluetoothAdapter.getDefaultAdapter().getProfileProxy(this, serviceListener, BluetoothProfile.HEADSET);
		} catch (Throwable e) {
			FileLog.e(e);
		}
	}

	private SensorEvent lastSensorEvent;

	@SuppressLint("NewApi")
	@Override
	public void onSensorChanged(SensorEvent event) {
		lastSensorEvent = event;
		if (unmutedByHold || remoteVideoState == Instance.VIDEO_STATE_ACTIVE || videoState[CAPTURE_DEVICE_CAMERA] == Instance.VIDEO_STATE_ACTIVE) {
			return;
		}
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			VoipAudioManager vam = VoipAudioManager.get();
			if (audioRouteToSet != AUDIO_ROUTE_EARPIECE || isHeadsetPlugged || vam.isSpeakerphoneOn() || (isBluetoothHeadsetConnected() && am.isBluetoothScoOn())) {
				return;
			}
			boolean newIsNear = event.values[0] < Math.min(event.sensor.getMaximumRange(), 3);
			checkIsNear(newIsNear);
			NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.nearEarEvent, newIsNear);
		}
	}

	private void checkIsNear() {
		if (remoteVideoState == Instance.VIDEO_STATE_ACTIVE || videoState[CAPTURE_DEVICE_CAMERA] == Instance.VIDEO_STATE_ACTIVE) {
			checkIsNear(false);
		}
	}

	private void checkIsNear(boolean newIsNear) {
		if (newIsNear != isProximityNear) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("proximity " + newIsNear);
			}
			isProximityNear = newIsNear;
			try {
				if (isProximityNear) {
					proximityWakelock.acquire();
				} else {
					proximityWakelock.release(1); // this is non-public API before L
				}
			} catch (Exception x) {
				FileLog.e(x);
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	public boolean isBluetoothHeadsetConnected() {
		if (USE_CONNECTION_SERVICE && systemCallConnection != null && systemCallConnection.getCallAudioState() != null) {
			return (systemCallConnection.getCallAudioState().getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0;
		}
		return isBtHeadsetConnected;
	}

	public void onAudioFocusChange(int focusChange) {
		if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			hasAudioFocus = true;
		} else {
			hasAudioFocus = false;
		}
	}

	private void updateBluetoothHeadsetState(boolean connected) {
		if (connected == isBtHeadsetConnected) {
			return;
		}
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("updateBluetoothHeadsetState: " + connected);
		}
		isBtHeadsetConnected = connected;
		final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (connected && !isRinging() && currentState != 0) {
			if (bluetoothScoActive) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.d("SCO already active, setting audio routing");
				}
				if (!hasRtmpStream()) {
					am.setSpeakerphoneOn(false);
					am.setBluetoothScoOn(true);
				}
			} else {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.d("startBluetoothSco");
				}
				if (!hasRtmpStream()) {
					needSwitchToBluetoothAfterScoActivates = true;
					AndroidUtilities.runOnUIThread(() -> {
						try {
							am.startBluetoothSco();
						} catch (Throwable ignore) {

						}
					}, 500);
				}
			}
		} else {
			bluetoothScoActive = false;
			bluetoothScoConnecting = false;

			am.setBluetoothScoOn(false);
		}
		for (StateListener l : stateListeners) {
			l.onAudioSettingsChanged();
		}
	}

	public String getLastError() {
		return lastError;
	}

	public int getCallState() {
		return currentState;
	}

	public TLRPC.InputPeer getGroupCallPeer() {
		return groupCallPeer;
	}

	private void updateNetworkType() {
		if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
			if (tgVoip[CAPTURE_DEVICE_CAMERA].isGroup()) {

			} else {
				tgVoip[CAPTURE_DEVICE_CAMERA].setNetworkType(getNetworkType());
			}
		} else {
			lastNetInfo = getActiveNetworkInfo();
		}
	}

	private int getNetworkType() {
		final NetworkInfo info = lastNetInfo = getActiveNetworkInfo();
		int type = Instance.NET_TYPE_UNKNOWN;
		if (info != null) {
			switch (info.getType()) {
				case ConnectivityManager.TYPE_MOBILE:
					switch (info.getSubtype()) {
						case TelephonyManager.NETWORK_TYPE_GPRS:
							type = Instance.NET_TYPE_GPRS;
							break;
						case TelephonyManager.NETWORK_TYPE_EDGE:
						case TelephonyManager.NETWORK_TYPE_1xRTT:
							type = Instance.NET_TYPE_EDGE;
							break;
						case TelephonyManager.NETWORK_TYPE_UMTS:
						case TelephonyManager.NETWORK_TYPE_EVDO_0:
							type = Instance.NET_TYPE_3G;
							break;
						case TelephonyManager.NETWORK_TYPE_HSDPA:
						case TelephonyManager.NETWORK_TYPE_HSPA:
						case TelephonyManager.NETWORK_TYPE_HSPAP:
						case TelephonyManager.NETWORK_TYPE_HSUPA:
						case TelephonyManager.NETWORK_TYPE_EVDO_A:
						case TelephonyManager.NETWORK_TYPE_EVDO_B:
							type = Instance.NET_TYPE_HSPA;
							break;
						case TelephonyManager.NETWORK_TYPE_LTE:
							type = Instance.NET_TYPE_LTE;
							break;
						default:
							type = Instance.NET_TYPE_OTHER_MOBILE;
							break;
					}
					break;
				case ConnectivityManager.TYPE_WIFI:
					type = Instance.NET_TYPE_WIFI;
					break;
				case ConnectivityManager.TYPE_ETHERNET:
					type = Instance.NET_TYPE_ETHERNET;
					break;
			}
		}
		return type;
	}

	private NetworkInfo getActiveNetworkInfo() {
		return ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
	}

	private void callFailed() {
		callFailed(tgVoip[CAPTURE_DEVICE_CAMERA] != null ? tgVoip[CAPTURE_DEVICE_CAMERA].getLastError() : Instance.ERROR_UNKNOWN);
	}

	private Bitmap getRoundAvatarBitmap(TLObject userOrChat) {
		Bitmap bitmap = null;
		try {
			if (userOrChat instanceof TLRPC.User) {
				TLRPC.User user = (TLRPC.User) userOrChat;
				if (user.photo != null && user.photo.photo_small != null) {
					BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(user.photo.photo_small, null, "50_50");
					if (img != null) {
						bitmap = img.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
					} else {
						try {
							BitmapFactory.Options opts = new BitmapFactory.Options();
							opts.inMutable = true;
							bitmap = BitmapFactory.decodeFile(FileLoader.getInstance(currentAccount).getPathToAttach(user.photo.photo_small, true).toString(), opts);
						} catch (Throwable e) {
							FileLog.e(e);
						}
					}
				}
			} else {
				TLRPC.Chat chat = (TLRPC.Chat) userOrChat;
				if (chat.photo != null && chat.photo.photo_small != null) {
					BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(chat.photo.photo_small, null, "50_50");
					if (img != null) {
						bitmap = img.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
					} else {
						try {
							BitmapFactory.Options opts = new BitmapFactory.Options();
							opts.inMutable = true;
							bitmap = BitmapFactory.decodeFile(FileLoader.getInstance(currentAccount).getPathToAttach(chat.photo.photo_small, true).toString(), opts);
						} catch (Throwable e) {
							FileLog.e(e);
						}
					}
				}
			}
		} catch (Throwable e) {
			FileLog.e(e);
		}
		if (bitmap == null) {
			Theme.createDialogsResources(this);
			AvatarDrawable placeholder;
			if (userOrChat instanceof TLRPC.User) {
				placeholder = new AvatarDrawable((TLRPC.User) userOrChat);
			} else {
				placeholder = new AvatarDrawable((TLRPC.Chat) userOrChat);
			}
			bitmap = Bitmap.createBitmap(AndroidUtilities.dp(42), AndroidUtilities.dp(42), Bitmap.Config.ARGB_8888);
			placeholder.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
			placeholder.draw(new Canvas(bitmap));
		}

		Canvas canvas = new Canvas(bitmap);
		Path circlePath = new Path();
		circlePath.addCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2, bitmap.getWidth() / 2, Path.Direction.CW);
		circlePath.toggleInverseFillType();
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		canvas.drawPath(circlePath, paint);
		return bitmap;
	}

	private void showIncomingNotification(String name, TLObject userOrChat, boolean video, int additionalMemberCount) {
		Intent intent = new Intent(this, LaunchActivity.class);
		intent.setAction("voip");

		Notification.Builder builder = new Notification.Builder(this)
				.setContentTitle(video ? LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding) : LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding))
				.setSmallIcon(R.drawable.ic_call)
				.setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			SharedPreferences nprefs = MessagesController.getGlobalNotificationsSettings();
			int chanIndex = nprefs.getInt("calls_notification_channel", 0);
			NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			NotificationChannel oldChannel = nm.getNotificationChannel("incoming_calls2" + chanIndex);
			if (oldChannel != null) {
				nm.deleteNotificationChannel(oldChannel.getId());
			}
            oldChannel = nm.getNotificationChannel("incoming_calls3" + chanIndex);
            if (oldChannel != null) {
                nm.deleteNotificationChannel(oldChannel.getId());
            }
			NotificationChannel existingChannel = nm.getNotificationChannel("incoming_calls4" + chanIndex);
			boolean needCreate = true;
			if (existingChannel != null) {
				if (existingChannel.getImportance() < NotificationManager.IMPORTANCE_HIGH || existingChannel.getSound() != null) {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.d("User messed up the notification channel; deleting it and creating a proper one");
					}
					nm.deleteNotificationChannel("incoming_calls4" + chanIndex);
					chanIndex++;
					nprefs.edit().putInt("calls_notification_channel", chanIndex).commit();
				} else {
					needCreate = false;
				}
			}
			if (needCreate) {
				AudioAttributes attrs = new AudioAttributes.Builder()
						.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
						.setLegacyStreamType(AudioManager.STREAM_RING)
						.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
						.build();
				NotificationChannel chan = new NotificationChannel("incoming_calls4" + chanIndex, LocaleController.getString("IncomingCallsSystemSetting", R.string.IncomingCallsSystemSetting), NotificationManager.IMPORTANCE_HIGH);
                try {
                    chan.setSound(null, attrs);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                chan.setDescription(LocaleController.getString("IncomingCallsSystemSettingDescription", R.string.IncomingCallsSystemSettingDescription));
				chan.enableVibration(false);
				chan.enableLights(false);
				chan.setBypassDnd(true);
				try {
					nm.createNotificationChannel(chan);
				} catch (Exception e) {
					FileLog.e(e);
					this.stopSelf();
					return;
				}
			}
			builder.setChannelId("incoming_calls4" + chanIndex);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setSound(null);
		}
		Intent endIntent = new Intent(this, VoIPActionsReceiver.class);
		endIntent.setAction(getPackageName() + ".DECLINE_CALL");
		endIntent.putExtra("call_id", getCallID());
		CharSequence endTitle = LocaleController.getString("VoipDeclineCall", R.string.VoipDeclineCall);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			endTitle = new SpannableString(endTitle);
			((SpannableString) endTitle).setSpan(new ForegroundColorSpan(0xFFF44336), 0, endTitle.length(), 0);
		}
		PendingIntent endPendingIntent = PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
		Intent answerIntent = new Intent(this, VoIPActionsReceiver.class);
		answerIntent.setAction(getPackageName() + ".ANSWER_CALL");
		answerIntent.putExtra("call_id", getCallID());
		CharSequence answerTitle = LocaleController.getString("VoipAnswerCall", R.string.VoipAnswerCall);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			answerTitle = new SpannableString(answerTitle);
			((SpannableString) answerTitle).setSpan(new ForegroundColorSpan(0xFF00AA00), 0, answerTitle.length(), 0);
		}
		PendingIntent answerPendingIntent = PendingIntent.getBroadcast(this, 0, answerIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
		builder.setPriority(Notification.PRIORITY_MAX);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			builder.setShowWhen(false);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setColor(0xff2ca5e0);
			builder.setVibrate(new long[0]);
			builder.setCategory(Notification.CATEGORY_CALL);
			builder.setFullScreenIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE), true);
			if (userOrChat instanceof TLRPC.User) {
				TLRPC.User user = (TLRPC.User) userOrChat;
				if (!TextUtils.isEmpty(user.phone)) {
					builder.addPerson("tel:" + user.phone);
				}
			}
		}
		Notification incomingNotification;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			Bitmap avatar = getRoundAvatarBitmap(userOrChat);
			String personName = ContactsController.formatName(userOrChat);
			if (TextUtils.isEmpty(personName)) {
				//java.lang.IllegalArgumentException: person must have a non-empty a name
				personName = "___";
			}
			Person person = new Person.Builder()
					.setName(personName)
					.setIcon(Icon.createWithAdaptiveBitmap(avatar)).build();
			Notification.CallStyle notificationStyle = Notification.CallStyle.forIncomingCall(person, endPendingIntent, answerPendingIntent);

			builder.setStyle(notificationStyle);
			incomingNotification = builder.build();
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			builder.addAction(R.drawable.ic_call_end_white_24dp, endTitle, endPendingIntent);
			builder.addAction(R.drawable.ic_call, answerTitle, answerPendingIntent);
			builder.setContentText(name);

			RemoteViews customView = new RemoteViews(getPackageName(), LocaleController.isRTL ? R.layout.call_notification_rtl : R.layout.call_notification);
			customView.setTextViewText(R.id.name, name);
			customView.setViewVisibility(R.id.subtitle, View.GONE);
			if (UserConfig.getActivatedAccountsCount() > 1) {
				TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();
				customView.setTextViewText(R.id.title, video ? LocaleController.formatString("VoipInVideoCallBrandingWithName", R.string.VoipInVideoCallBrandingWithName, ContactsController.formatName(self.first_name, self.last_name)) : LocaleController.formatString("VoipInCallBrandingWithName", R.string.VoipInCallBrandingWithName, ContactsController.formatName(self.first_name, self.last_name)));
			} else {
				customView.setTextViewText(R.id.title, video ? LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding) : LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding));
			}
			Bitmap avatar = getRoundAvatarBitmap(userOrChat);
			customView.setTextViewText(R.id.answer_text, LocaleController.getString("VoipAnswerCall", R.string.VoipAnswerCall));
			customView.setTextViewText(R.id.decline_text, LocaleController.getString("VoipDeclineCall", R.string.VoipDeclineCall));
			customView.setImageViewBitmap(R.id.photo, avatar);
			customView.setOnClickPendingIntent(R.id.answer_btn, answerPendingIntent);
			customView.setOnClickPendingIntent(R.id.decline_btn, endPendingIntent);
			builder.setLargeIcon(avatar);

			incomingNotification = builder.getNotification();
			incomingNotification.headsUpContentView = incomingNotification.bigContentView = customView;
		} else {
			builder.setContentText(name);
			builder.addAction(R.drawable.ic_call_end_white_24dp, endTitle, endPendingIntent);
			builder.addAction(R.drawable.ic_call, answerTitle, answerPendingIntent);
			incomingNotification = builder.getNotification();
		}
		startForeground(ID_INCOMING_CALL_NOTIFICATION, incomingNotification);
		startRingtoneAndVibration();
	}

	private void callFailed(String error) {
		if (privateCall != null) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("Discarding failed call");
			}
			TLRPC.TL_phone_discardCall req = new TLRPC.TL_phone_discardCall();
			req.peer = new TLRPC.TL_inputPhoneCall();
			req.peer.access_hash = privateCall.access_hash;
			req.peer.id = privateCall.id;
			req.duration = (int) (getCallDuration() / 1000);
			req.connection_id = tgVoip[CAPTURE_DEVICE_CAMERA] != null ? tgVoip[CAPTURE_DEVICE_CAMERA].getPreferredRelayId() : 0;
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
		try {
			throw new Exception("Call " + getCallID() + " failed with error: " + error);
		} catch (Exception x) {
			FileLog.e(x);
		}
		lastError = error;
		AndroidUtilities.runOnUIThread(() -> dispatchStateChanged(STATE_FAILED));
		if (TextUtils.equals(error, Instance.ERROR_LOCALIZED) && soundPool != null) {
			playingSound = true;
			Utilities.globalQueue.postRunnable(() -> soundPool.play(spFailedID, 1, 1, 0, 0, 1));
			AndroidUtilities.runOnUIThread(afterSoundRunnable, 1000);
		}
		if (USE_CONNECTION_SERVICE && systemCallConnection != null) {
			systemCallConnection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
			systemCallConnection.destroy();
			systemCallConnection = null;
		}
		stopSelf();
	}

	void callFailedFromConnectionService() {
		if (isOutgoing) {
			callFailed(Instance.ERROR_CONNECTION_SERVICE);
		} else {
			hangUp();
		}
	}

	@Override
	public void onConnectionStateChanged(int newState, boolean inTransition) {
		AndroidUtilities.runOnUIThread(() -> {
			if (newState == STATE_ESTABLISHED) {
				if (callStartTime == 0) {
					callStartTime = SystemClock.elapsedRealtime();
				}
				//peerCapabilities = tgVoip.getPeerCapabilities();
			}
			if (newState == STATE_FAILED) {
				callFailed();
				return;
			}
			if (newState == STATE_ESTABLISHED) {
				if (connectingSoundRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable);
					connectingSoundRunnable = null;
				}
				Utilities.globalQueue.postRunnable(() -> {
					if (spPlayId != 0) {
						soundPool.stop(spPlayId);
						spPlayId = 0;
					}
				});
				if (groupCall == null && !wasEstablished) {
					wasEstablished = true;
					if (!isProximityNear && !privateCall.video) {
						try {
							LaunchActivity.getLastFragment().getFragmentView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
						} catch (Exception ignore) {
						}
					}
					AndroidUtilities.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
								StatsController.getInstance(currentAccount).incrementTotalCallsTime(getStatsNetworkType(), 5);
								AndroidUtilities.runOnUIThread(this, 5000);
							}
						}
					}, 5000);
					if (isOutgoing) {
						StatsController.getInstance(currentAccount).incrementSentItemsCount(getStatsNetworkType(), StatsController.TYPE_CALLS, 1);
					} else {
						StatsController.getInstance(currentAccount).incrementReceivedItemsCount(getStatsNetworkType(), StatsController.TYPE_CALLS, 1);
					}
				}
			}
			if (newState == STATE_RECONNECTING && !isCallEnded) {
				Utilities.globalQueue.postRunnable(() -> {
					if (spPlayId != 0) {
						soundPool.stop(spPlayId);
					}
					spPlayId = soundPool.play(groupCall != null ? spVoiceChatConnecting : spConnectingId, 1, 1, 0, -1, 1);
				});
			}
			dispatchStateChanged(newState);
		});
	}

	public void playStartRecordSound() {
		Utilities.globalQueue.postRunnable(() -> soundPool.play(spStartRecordId, 0.5f, 0.5f, 0, 0, 1));
	}

	public void playAllowTalkSound() {
		Utilities.globalQueue.postRunnable(() -> soundPool.play(spAllowTalkId, 0.5f, 0.5f, 0, 0, 1));
	}

	@Override
	public void onSignalBarCountChanged(int newCount) {
		AndroidUtilities.runOnUIThread(() -> {
			signalBarCount = newCount;
			for (int a = 0; a < stateListeners.size(); a++) {
				StateListener l = stateListeners.get(a);
				l.onSignalBarsCountChanged(newCount);
			}
		});
	}

	public boolean isBluetoothOn() {
		final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		return am.isBluetoothScoOn();
	}

	public boolean isBluetoothWillOn() {
		return needSwitchToBluetoothAfterScoActivates;
	}

	public boolean isHeadsetPlugged() {
		return isHeadsetPlugged;
	}

	private void callEnded() {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("Call " + getCallID() + " ended");
		}
		isCallEnded = true;
		if (groupCall != null && (!playedConnectedSound || onDestroyRunnable != null)) {
			needPlayEndSound = false;
		}
		AndroidUtilities.runOnUIThread(() -> dispatchStateChanged(STATE_ENDED));
		int delay = 700;
		Utilities.globalQueue.postRunnable(() -> {
			if (spPlayId != 0) {
				soundPool.stop(spPlayId);
				spPlayId = 0;
			}
		});

		if (connectingSoundRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable);
			connectingSoundRunnable = null;
		}
		if (needPlayEndSound) {
			playingSound = true;
			if (groupCall == null) {
				Utilities.globalQueue.postRunnable(() -> soundPool.play(spEndId, 1, 1, 0, 0, 1));
			} else {
				Utilities.globalQueue.postRunnable(() -> soundPool.play(spVoiceChatEndId, 1.0f, 1.0f, 0, 0, 1), 100);
				delay = 500;
			}
			AndroidUtilities.runOnUIThread(afterSoundRunnable, delay);
		}
		if (timeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
			timeoutRunnable = null;
		}
		endConnectionServiceCall(needPlayEndSound ? delay : 0);
		stopSelf();
	}

	private void endConnectionServiceCall(long delay) {
		if (USE_CONNECTION_SERVICE) {
			Runnable r = () -> {
				if (systemCallConnection != null) {
					switch (callDiscardReason) {
						case DISCARD_REASON_HANGUP:
							systemCallConnection.setDisconnected(new DisconnectCause(isOutgoing ? DisconnectCause.LOCAL : DisconnectCause.REJECTED));
							break;
						case DISCARD_REASON_DISCONNECT:
							systemCallConnection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
							break;
						case DISCARD_REASON_LINE_BUSY:
							systemCallConnection.setDisconnected(new DisconnectCause(DisconnectCause.BUSY));
							break;
						case DISCARD_REASON_MISSED:
							systemCallConnection.setDisconnected(new DisconnectCause(isOutgoing ? DisconnectCause.CANCELED : DisconnectCause.MISSED));
							break;
						default:
							systemCallConnection.setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
							break;
					}
					systemCallConnection.destroy();
					systemCallConnection = null;
				}
			};
			if (delay > 0) {
				AndroidUtilities.runOnUIThread(r, delay);
			} else {
				r.run();
			}
		}
	}

	public boolean isOutgoing() {
		return isOutgoing;
	}

	public void handleNotificationAction(Intent intent) {
		if ((getPackageName() + ".END_CALL").equals(intent.getAction())) {
			stopForeground(true);
			hangUp();
		} else if ((getPackageName() + ".DECLINE_CALL").equals(intent.getAction())) {
			stopForeground(true);
			declineIncomingCall(DISCARD_REASON_LINE_BUSY, null);
		} else if ((getPackageName() + ".ANSWER_CALL").equals(intent.getAction())) {
			acceptIncomingCallFromNotification();
		}
	}

	private void acceptIncomingCallFromNotification() {
		showNotification();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || privateCall.video && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
			try {
				//intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
				PendingIntent.getActivity(VoIPService.this, 0, new Intent(VoIPService.this, VoIPPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ONE_SHOT).send();
			} catch (Exception x) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("Error starting permission activity", x);
				}
			}
			return;
		}
		acceptIncomingCall();
		try {
			PendingIntent.getActivity(VoIPService.this, 0, new Intent(VoIPService.this, getUIActivityClass()).setAction("voip"), PendingIntent.FLAG_MUTABLE).send();
		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("Error starting incall activity", x);
			}
		}
	}

	public void updateOutputGainControlState() {
		if (hasRtmpStream()) {
			return;
		}
		if (tgVoip[CAPTURE_DEVICE_CAMERA] != null) {
			if (!USE_CONNECTION_SERVICE) {
				final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
				boolean isSpeakerPhoneOn = VoipAudioManager.get().isSpeakerphoneOn();
				tgVoip[CAPTURE_DEVICE_CAMERA].setAudioOutputGainControlEnabled(hasEarpiece() && !isSpeakerPhoneOn && !am.isBluetoothScoOn() && !isHeadsetPlugged);
				tgVoip[CAPTURE_DEVICE_CAMERA].setEchoCancellationStrength(isHeadsetPlugged || (hasEarpiece() && !isSpeakerPhoneOn && !am.isBluetoothScoOn() && !isHeadsetPlugged) ? 0 : 1);
			} else {
				final boolean isEarpiece = systemCallConnection.getCallAudioState().getRoute() == CallAudioState.ROUTE_EARPIECE;
				tgVoip[CAPTURE_DEVICE_CAMERA].setAudioOutputGainControlEnabled(isEarpiece);
				tgVoip[CAPTURE_DEVICE_CAMERA].setEchoCancellationStrength(isEarpiece ? 0 : 1);
			}
		}
	}

	public int getAccount() {
		return currentAccount;
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.appDidLogout) {
			callEnded();
		}
	}

	public static boolean isAnyKindOfCallActive() {
		if (VoIPService.getSharedInstance() != null) {
			return VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING;
		}
		return false;
	}

	private boolean isFinished() {
		return currentState == STATE_ENDED || currentState == STATE_FAILED;
	}

	public int getRemoteAudioState() {
		return remoteAudioState;
	}

	public int getRemoteVideoState() {
		return remoteVideoState;
	}

	@TargetApi(Build.VERSION_CODES.O)
	private PhoneAccountHandle addAccountToTelecomManager() {
		TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
		TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();
		PhoneAccountHandle handle = new PhoneAccountHandle(new ComponentName(this, TelegramConnectionService.class), "" + self.id);
		PhoneAccount account = new PhoneAccount.Builder(handle, ContactsController.formatName(self.first_name, self.last_name))
				.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
				.setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_dr))
				.setHighlightColor(0xff2ca5e0)
				.addSupportedUriScheme("sip")
				.build();
		tm.registerPhoneAccount(account);
		return handle;
	}

	private static boolean isDeviceCompatibleWithConnectionServiceAPI() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			return false;
		}
		// some non-Google devices don't implement the ConnectionService API correctly so, sadly,
		// we'll have to whitelist only a handful of known-compatible devices for now
		return false;/*"angler".equals(Build.PRODUCT)            // Nexus 6P
				|| "bullhead".equals(Build.PRODUCT)        // Nexus 5X
				|| "sailfish".equals(Build.PRODUCT)        // Pixel
				|| "marlin".equals(Build.PRODUCT)        // Pixel XL
				|| "walleye".equals(Build.PRODUCT)        // Pixel 2
				|| "taimen".equals(Build.PRODUCT)        // Pixel 2 XL
				|| "blueline".equals(Build.PRODUCT)        // Pixel 3
				|| "crosshatch".equals(Build.PRODUCT)    // Pixel 3 XL
				|| MessagesController.getGlobalMainSettings().getBoolean("dbg_force_connection_service", false);*/
	}

	public interface StateListener {
		default void onStateChanged(int state) {

		}

		default void onSignalBarsCountChanged(int count) {

		}

		default void onAudioSettingsChanged() {

		}

		default void onMediaStateUpdated(int audioState, int videoState) {

		}

		default void onCameraSwitch(boolean isFrontFace) {

		}

		default void onCameraFirstFrameAvailable() {

		}

		default void onVideoAvailableChange(boolean isAvailable) {

		}

		default void onScreenOnChange(boolean screenOn) {

		}
	}

	public class CallConnection extends Connection {
		public CallConnection() {
			setConnectionProperties(PROPERTY_SELF_MANAGED);
			setAudioModeIsVoip(true);
		}

		@Override
		public void onCallAudioStateChanged(CallAudioState state) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("ConnectionService call audio state changed: " + state);
			}
			for (StateListener l : stateListeners) {
				l.onAudioSettingsChanged();
			}
		}

		@Override
		public void onDisconnect() {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("ConnectionService onDisconnect");
			}
			setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
			destroy();
			systemCallConnection = null;
			hangUp();
		}

		@Override
		public void onAnswer() {
			acceptIncomingCallFromNotification();
		}

		@Override
		public void onReject() {
			needPlayEndSound = false;
			declineIncomingCall(DISCARD_REASON_HANGUP, null);
		}

		@Override
		public void onShowIncomingCallUi() {
			startRinging();
		}

		@Override
		public void onStateChanged(int state) {
			super.onStateChanged(state);
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("ConnectionService onStateChanged " + stateToString(state));
			}
			if (state == Connection.STATE_ACTIVE) {
				ContactsController.getInstance(currentAccount).deleteConnectionServiceContact();
				didDeleteConnectionServiceContact = true;
			}
		}

		@Override
		public void onCallEvent(String event, Bundle extras) {
			super.onCallEvent(event, extras);
			if (BuildVars.LOGS_ENABLED)
				FileLog.d("ConnectionService onCallEvent " + event);
		}

		//undocumented API
		public void onSilence() {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("onSlience");
			}
			stopRinging();
		}
	}

	public static class SharedUIParams {
		public boolean tapToVideoTooltipWasShowed;
		public boolean cameraAlertWasShowed;
		public boolean wasVideoCall;
	}
}
