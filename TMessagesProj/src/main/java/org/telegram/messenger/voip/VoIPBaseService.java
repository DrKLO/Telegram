package org.telegram.messenger.voip;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRouter;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
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
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.VoIPPermissionActivity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Created by grishka on 21.07.17.
 */

@SuppressLint("NewApi")
public abstract class VoIPBaseService extends Service implements SensorEventListener, AudioManager.OnAudioFocusChangeListener, VoIPController.ConnectionStateListener, NotificationCenter.NotificationCenterDelegate {

	protected int currentAccount = -1;
	public static final int STATE_WAIT_INIT = Instance.STATE_WAIT_INIT;
	public static final int STATE_WAIT_INIT_ACK = Instance.STATE_WAIT_INIT_ACK;
	public static final int STATE_ESTABLISHED = Instance.STATE_ESTABLISHED;
	public static final int STATE_FAILED = Instance.STATE_FAILED;
	public static final int STATE_RECONNECTING = Instance.STATE_RECONNECTING;
	public static final int STATE_CREATING = 6;
	public static final int STATE_ENDED = 11;
	public static final String ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG";

	protected static final int ID_ONGOING_CALL_NOTIFICATION = 201;
	protected static final int ID_INCOMING_CALL_NOTIFICATION = 202;

	public static final int DISCARD_REASON_HANGUP = 1;
	public static final int DISCARD_REASON_DISCONNECT = 2;
	public static final int DISCARD_REASON_MISSED = 3;
	public static final int DISCARD_REASON_LINE_BUSY = 4;

	public static final int AUDIO_ROUTE_EARPIECE = 0;
	public static final int AUDIO_ROUTE_SPEAKER = 1;
	public static final int AUDIO_ROUTE_BLUETOOTH = 2;

	protected static final boolean USE_CONNECTION_SERVICE = isDeviceCompatibleWithConnectionServiceAPI();

	protected static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
	protected static VoIPBaseService sharedInstance;
	protected static Runnable setModeRunnable;
	protected static final Object sync = new Object();
	protected NetworkInfo lastNetInfo;
	protected int currentState = 0;
	protected Notification ongoingCallNotification;
	protected NativeInstance tgVoip;
	protected boolean wasConnected;

	protected TLRPC.Chat chat;

	protected boolean isVideoAvailable;
	protected boolean notificationsDisabled;
	protected boolean switchingCamera;
	protected boolean isFrontFaceCamera = true;
	protected String lastError;
	protected PowerManager.WakeLock proximityWakelock;
	protected PowerManager.WakeLock cpuWakelock;
	protected boolean isProximityNear;
	protected boolean isHeadsetPlugged;
	protected int previousAudioOutput = -1;
	protected ArrayList<StateListener> stateListeners = new ArrayList<>();
	protected MediaPlayer ringtonePlayer;
	protected Vibrator vibrator;
	protected SoundPool soundPool;
	protected int spRingbackID;
	protected int spFailedID;
	protected int spEndId;
	protected int spVoiceChatEndId;
	protected int spVoiceChatStartId;
	protected int spVoiceChatConnecting;
	protected int spBusyId;
	protected int spConnectingId;
	protected int spPlayID;
	protected boolean needPlayEndSound;
	protected boolean hasAudioFocus;
	protected boolean micMute;
	protected boolean unmutedByHold;
	protected BluetoothAdapter btAdapter;
	protected Instance.TrafficStats prevTrafficStats;
	protected boolean isBtHeadsetConnected;
	protected boolean screenOn;

	private Runnable updateNotificationRunnable;

	protected Runnable onDestroyRunnable;

	protected boolean playedConnectedSound;

	protected int videoState = Instance.VIDEO_STATE_INACTIVE;

	public TLRPC.PhoneCall privateCall;
	public ChatObject.Call groupCall;

	protected int mySource;
	protected boolean createGroupCall;

	protected long callStartTime;
	protected boolean playingSound;
	protected boolean isOutgoing;
	public boolean videoCall;
	protected long videoCapturer;
	protected Runnable timeoutRunnable;

	private Boolean mHasEarpiece;
	private boolean wasEstablished;
	protected int signalBarCount;
	protected int currentAudioState = Instance.AUDIO_STATE_ACTIVE;
	protected int currentVideoState = Instance.VIDEO_STATE_INACTIVE;
	protected boolean audioConfigured;
	protected int audioRouteToSet = AUDIO_ROUTE_BLUETOOTH;
	protected boolean speakerphoneStateToSet;
	protected CallConnection systemCallConnection;
	protected int callDiscardReason;
	protected boolean bluetoothScoActive;
	protected boolean needSwitchToBluetoothAfterScoActivates;
	protected boolean didDeleteConnectionServiceContact;
	protected Runnable connectingSoundRunnable;

	private String currentBluetoothDeviceName;

	public final SharedUIParams sharedUIParams = new SharedUIParams();

	protected Runnable afterSoundRunnable = new Runnable() {
		@Override
		public void run() {

			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			am.abandonAudioFocus(VoIPBaseService.this);
			am.unregisterMediaButtonEventReceiver(new ComponentName(VoIPBaseService.this, VoIPMediaButtonReceiver.class));
			if (!USE_CONNECTION_SERVICE && sharedInstance == null) {
				if (isBtHeadsetConnected) {
					am.stopBluetoothSco();
					am.setBluetoothScoOn(false);
					bluetoothScoActive = false;
				}
				am.setSpeakerphoneOn(false);
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
			for (BluetoothDevice device : proxy.getConnectedDevices()) {
				if (proxy.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
					continue;
				}
				currentBluetoothDeviceName = device.getName();
				break;
			}
			BluetoothAdapter.getDefaultAdapter().closeProfileProxy(profile, proxy);
			fetchingBluetoothDeviceName = false;
		}
	};

	protected BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_HEADSET_PLUG.equals(intent.getAction())) {
				isHeadsetPlugged = intent.getIntExtra("state", 0) == 1;
				if (isHeadsetPlugged && proximityWakelock != null && proximityWakelock.isHeld()) {
					proximityWakelock.release();
				}
				if (isHeadsetPlugged) {
					AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
					if (am.isSpeakerphoneOn()) {
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
				bluetoothScoActive = state == AudioManager.SCO_AUDIO_STATE_CONNECTED;
				if (bluetoothScoActive) {
					fetchBluetoothDeviceName();
					if (needSwitchToBluetoothAfterScoActivates) {
						needSwitchToBluetoothAfterScoActivates = false;
						AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
						am.setSpeakerphoneOn(false);
						am.setBluetoothScoOn(true);
					}
				}
				for (StateListener l : stateListeners) {
					l.onAudioSettingsChanged();
				}
			} else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
				String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
				if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
					hangUp();
				}
			} else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
				screenOn = true;
				for (int i = 0; i< stateListeners.size(); i++) {
					stateListeners.get(i).onScreenOnChange(screenOn);
				}
			} else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
				screenOn = false;
				for (int i = 0; i< stateListeners.size(); i++) {
					stateListeners.get(i).onScreenOnChange(screenOn);
				}
			}
		}
	};

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

	protected int getStatsNetworkType() {
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

	public void setMicMute(boolean mute, boolean hold, boolean send) {
		if (micMute == mute) {
			return;
		}
		micMute = mute;
		if (groupCall != null) {
			if (!send) {
				TLRPC.TL_groupCallParticipant self = groupCall.participants.get(UserConfig.getInstance(currentAccount).getClientUserId());
				if (self != null && self.muted && !self.can_self_unmute) {
					send = true;
				}
			}
			if (send) {
				editCallMember(UserConfig.getInstance(currentAccount).getCurrentUser(), mute, -1);
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
		if (tgVoip != null) {
			tgVoip.setMuteMicrophone(mute);
		}
		for (StateListener l : stateListeners) {
			l.onAudioSettingsChanged();
		}
	}

	public void editCallMember(TLObject object, boolean mute, int volume) {
		if (groupCall == null) {
			return;
		}
		TLRPC.TL_phone_editGroupCallMember req = new TLRPC.TL_phone_editGroupCallMember();
		req.call = groupCall.getInputGroupCall();
		if (object instanceof TLRPC.User) {
			TLRPC.User user = (TLRPC.User) object;
			if (UserObject.isUserSelf(user)) {
				req.user_id = new TLRPC.TL_inputUserSelf();
			} else {
				req.user_id = new TLRPC.TL_inputUser();
				req.user_id.user_id = user.id;
				req.user_id.access_hash = user.access_hash;
			}
		}
		req.muted = mute;
		if (volume >= 0) {
			req.volume = volume;
			req.flags |= 2;
		}
		int account = currentAccount;
		AccountInstance.getInstance(account).getConnectionsManager().sendRequest(req, (response, error) -> {
			if (response != null) {
				AccountInstance.getInstance(account).getMessagesController().processUpdates((TLRPC.Updates) response, false);
			}
		});
	}

	public boolean isMicMute() {
		return micMute;
	}

	public void toggleSpeakerphoneOrShowRouteSheet(Context context, boolean fromOverlayWindow) {
		if (isBluetoothHeadsetConnected() && hasEarpiece()) {
			BottomSheet.Builder builder = new BottomSheet.Builder(context)
					.setTitle(LocaleController.getString("VoipOutputDevices", R.string.VoipOutputDevices), true)
					.setItems(new CharSequence[]{
									LocaleController.getString("VoipAudioRoutingSpeaker", R.string.VoipAudioRoutingSpeaker),
									isHeadsetPlugged ? LocaleController.getString("VoipAudioRoutingHeadset", R.string.VoipAudioRoutingHeadset) : LocaleController.getString("VoipAudioRoutingEarpiece", R.string.VoipAudioRoutingEarpiece),
									currentBluetoothDeviceName != null ? currentBluetoothDeviceName : LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth)},
							new int[]{R.drawable.calls_menu_speaker,
									isHeadsetPlugged ? R.drawable.calls_menu_headset : R.drawable.calls_menu_phone,
									R.drawable.calls_menu_bluetooth}, (dialog, which) -> {
								if (getSharedInstance() == null) {
									return;
								}
								setAudioOutput(which);
							});

			BottomSheet bottomSheet = builder.create();
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
			if (hasEarpiece()) {
				am.setSpeakerphoneOn(!am.isSpeakerphoneOn());
			} else {
				am.setBluetoothScoOn(!am.isBluetoothScoOn());
			}
			updateOutputGainControlState();
		} else {
			speakerphoneStateToSet = !speakerphoneStateToSet;
		}
		for (StateListener l : stateListeners) {
			l.onAudioSettingsChanged();
		}
	}

	protected void setAudioOutput(int which) {
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
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
						} catch (Throwable ignore) {

						}
					} else {
						am.setBluetoothScoOn(true);
						am.setSpeakerphoneOn(false);
					}
					break;
				case 1:
					if (bluetoothScoActive) {
						am.stopBluetoothSco();
						bluetoothScoActive = false;
					}
					am.setSpeakerphoneOn(false);
					am.setBluetoothScoOn(false);
					break;
				case 0:
					if (bluetoothScoActive) {
						am.stopBluetoothSco();
						bluetoothScoActive = false;
					}
					am.setBluetoothScoOn(false);
					am.setSpeakerphoneOn(true);
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
			return hasEarpiece() ? am.isSpeakerphoneOn() : am.isBluetoothScoOn();
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
			if (am.isBluetoothScoOn()) {
				return AUDIO_ROUTE_BLUETOOTH;
			} else if (am.isSpeakerphoneOn()) {
				return AUDIO_ROUTE_SPEAKER;
			} else {
				return AUDIO_ROUTE_EARPIECE;
			}
		}
		return audioRouteToSet;
	}

	public String getDebugString() {
		return tgVoip != null ? tgVoip.getDebugInfo() : "";
	}

	public long getCallDuration() {
		if (callStartTime == 0) {
			return 0;
		}
		return SystemClock.elapsedRealtime() - callStartTime;
	}

	public static VoIPBaseService getSharedInstance() {
		return sharedInstance;
	}

	public void stopRinging() {
		if (ringtonePlayer != null) {
			ringtonePlayer.stop();
			ringtonePlayer.release();
			ringtonePlayer = null;
		}
		if (vibrator != null) {
			vibrator.cancel();
			vibrator = null;
		}
	}

	protected void showNotification(String name, Bitmap photo) {
		Intent intent = new Intent(this, LaunchActivity.class).setAction(groupCall != null ? "voip_chat" : "voip");
		if (groupCall != null) {
			intent.putExtra("currentAccount", currentAccount);
		}
		Notification.Builder builder = new Notification.Builder(this)
				.setContentTitle(groupCall != null ? LocaleController.getString("VoipVoiceChat", R.string.VoipVoiceChat) : LocaleController.getString("VoipOutgoingCall", R.string.VoipOutgoingCall))
				.setContentText(name)
				.setContentIntent(PendingIntent.getActivity(this, 50, intent, 0));
		if (groupCall != null) {
			builder.setSmallIcon(isMicMute() ? R.drawable.voicechat_muted : R.drawable.voicechat_active);
		} else {
			builder.setSmallIcon(R.drawable.notification);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			Intent endIntent = new Intent(this, VoIPActionsReceiver.class);
			endIntent.setAction(getPackageName() + ".END_CALL");
			builder.addAction(R.drawable.ic_call_end_white_24dp, groupCall != null ? LocaleController.getString("VoipGroupLeaveAlertTitle", R.string.VoipGroupLeaveAlertTitle) : LocaleController.getString("VoipEndCall", R.string.VoipEndCall), PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT));
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
		ongoingCallNotification = builder.getNotification();
		startForeground(ID_ONGOING_CALL_NOTIFICATION, ongoingCallNotification);
	}

	protected void startRingtoneAndVibration(int chatID) {
		SharedPreferences prefs = MessagesController.getNotificationsSettings(currentAccount);
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		boolean needRing = am.getRingerMode() != AudioManager.RINGER_MODE_SILENT;
		if (needRing) {
			ringtonePlayer = new MediaPlayer();
			ringtonePlayer.setOnPreparedListener(mediaPlayer -> ringtonePlayer.start());
			ringtonePlayer.setLooping(true);
			if (isHeadsetPlugged) {
				ringtonePlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
			} else {
				ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
				if (!USE_CONNECTION_SERVICE) {
					am.requestAudioFocus(this, AudioManager.STREAM_RING, AudioManager.AUDIOFOCUS_GAIN);
				}
			}
			try {
				String notificationUri;
				if (prefs.getBoolean("custom_" + chatID, false)) {
					notificationUri = prefs.getString("ringtone_path_" + chatID, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString());
				} else {
					notificationUri = prefs.getString("CallsRingtonePath", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString());
				}
				ringtonePlayer.setDataSource(this, Uri.parse(notificationUri));
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

	@Override
	public void onDestroy() {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("=============== VoIPService STOPPING ===============");
		}
		stopForeground(true);
		stopRinging();
		if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
			MessagesController.getInstance(currentAccount).ignoreSetOnline = false;
		}
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout);
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
		unregisterReceiver(receiver);
		if (timeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
			timeoutRunnable = null;
		}
		super.onDestroy();
		sharedInstance = null;
		AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didEndCall));
		if (tgVoip != null) {
			StatsController.getInstance(currentAccount).incrementTotalCallsTime(getStatsNetworkType(), (int) (getCallDuration() / 1000) % 5);
			onTgVoipPreStop();
			if (tgVoip.isGroup()) {
				NativeInstance instance = tgVoip;
				Utilities.globalQueue.postRunnable(instance::stopGroup);
			} else {
				Instance.FinalState state = tgVoip.stop();
				updateTrafficStats(state.trafficStats);
				onTgVoipStop(state);
			}
			prevTrafficStats = null;
			callStartTime = 0;
			tgVoip = null;
			Instance.destroyInstance();
		}
		if (videoCapturer != 0) {
			NativeInstance.destroyVideoCapturer(videoCapturer);
			videoCapturer = 0;
		}
		cpuWakelock.release();
		if (!playingSound) {
			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			if (!USE_CONNECTION_SERVICE) {
				if (isBtHeadsetConnected) {
					am.stopBluetoothSco();
					am.setBluetoothScoOn(false);
					am.setSpeakerphoneOn(false);
					bluetoothScoActive = false;
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
			am.unregisterMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));
			if (hasAudioFocus) {
				am.abandonAudioFocus(this);
			}
			Utilities.globalQueue.postRunnable(() -> soundPool.release());
		}

		if (USE_CONNECTION_SERVICE) {
			if (!didDeleteConnectionServiceContact) {
				ContactsController.getInstance(currentAccount).deleteConnectionServiceContact();
			}
			if (systemCallConnection != null && !playingSound) {
				systemCallConnection.destroy();
			}
		}

		ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
		VoIPHelper.lastCallTime = SystemClock.elapsedRealtime();
	}

	public abstract long getCallID();
	public abstract void hangUp();
	public abstract void hangUp(Runnable onDone);
	public abstract void acceptIncomingCall();
	public abstract void declineIncomingCall(int reason, Runnable onDone);
	public abstract void declineIncomingCall();
	protected abstract Class<? extends Activity> getUIActivityClass();
	public abstract CallConnection getConnectionAndStartCall();
	protected abstract void startRinging();
	public abstract void startRingtoneAndVibration();
	protected abstract void updateServerConfig();
	protected abstract void showNotification();

	protected void onTgVoipPreStop() {

	}

	protected void onTgVoipStop(Instance.FinalState finalState) {

	}

	protected void initializeAccountRelatedThings() {
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
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) != null) {
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
			});

			am.registerMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));

			if (!USE_CONNECTION_SERVICE && btAdapter != null && btAdapter.isEnabled()) {
				try {
					MediaRouter mr = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
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
							updateBluetoothHeadsetState(false);
						}
					}
				} catch (Throwable e) {
					FileLog.e(e);
				}
			}
		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("error initializing voip controller", x);
			}
			callFailed();
		}
	}

	protected void dispatchStateChanged(int state) {
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

	protected void updateTrafficStats(Instance.TrafficStats trafficStats) {
		if (trafficStats == null) {
			trafficStats = tgVoip.getTrafficStats();
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
	protected void configureDeviceForCall() {
		needPlayEndSound = true;
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (!USE_CONNECTION_SERVICE) {
			Utilities.globalQueue.postRunnable(() -> {
				try {
					am.setMode(AudioManager.MODE_IN_COMMUNICATION);
				} catch (Exception e) {
					FileLog.e(e);
				}
			});
			am.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
			if (isBluetoothHeadsetConnected() && hasEarpiece()) {
				switch (audioRouteToSet) {
					case AUDIO_ROUTE_BLUETOOTH:
						if (!bluetoothScoActive) {
							needSwitchToBluetoothAfterScoActivates = true;
							try {
								am.startBluetoothSco();
							} catch (Throwable ignore) {

							}
						} else {
							am.setBluetoothScoOn(true);
							am.setSpeakerphoneOn(false);
						}
						break;
					case AUDIO_ROUTE_EARPIECE:
						am.setBluetoothScoOn(false);
						am.setSpeakerphoneOn(false);
						break;
					case AUDIO_ROUTE_SPEAKER:
						am.setBluetoothScoOn(false);
						am.setSpeakerphoneOn(true);
						break;
				}
			} else if (isBluetoothHeadsetConnected()) {
				am.setBluetoothScoOn(speakerphoneStateToSet);
			} else {
				am.setSpeakerphoneOn(speakerphoneStateToSet);
			}
		}
		updateOutputGainControlState();
		audioConfigured = true;

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

	@SuppressLint("NewApi")
	@Override
	public void onSensorChanged(SensorEvent event) {
		if (unmutedByHold || currentVideoState == Instance.VIDEO_STATE_ACTIVE || videoState == Instance.VIDEO_STATE_ACTIVE) {
			return;
		}
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
			AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
			if (isHeadsetPlugged || am.isSpeakerphoneOn() || (isBluetoothHeadsetConnected() && am.isBluetoothScoOn())) {
				return;
			}
			boolean newIsNear = event.values[0] < Math.min(event.sensor.getMaximumRange(), 3);
			checkIsNear(newIsNear);
		}
	}

	protected void checkIsNear() {
		if (currentVideoState == Instance.VIDEO_STATE_ACTIVE || videoState == Instance.VIDEO_STATE_ACTIVE) {
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

	protected void updateBluetoothHeadsetState(boolean connected) {
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
				am.setSpeakerphoneOn(false);
				am.setBluetoothScoOn(true);
			} else {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.d("startBluetoothSco");
				}
				needSwitchToBluetoothAfterScoActivates = true;
				// some devices ignore startBluetoothSco when called immediately after the headset is connected, so delay it
				AndroidUtilities.runOnUIThread(() -> {
					try {
						am.startBluetoothSco();
					} catch (Throwable ignore) {

					}
				}, 500);
			}
		} else {
			bluetoothScoActive = false;
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

	protected void updateNetworkType() {
		if (tgVoip != null) {
			if (tgVoip.isGroup()) {

			} else {
				tgVoip.setNetworkType(getNetworkType());
			}
		} else {
			lastNetInfo = getActiveNetworkInfo();
		}
	}

	protected int getNetworkType() {
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

	protected NetworkInfo getActiveNetworkInfo() {
		return ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
	}

	protected void callFailed() {
		callFailed(tgVoip != null ? tgVoip.getLastError() : Instance.ERROR_UNKNOWN);
	}

	protected Bitmap getRoundAvatarBitmap(TLObject userOrChat) {
		Bitmap bitmap = null;
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
						bitmap = BitmapFactory.decodeFile(FileLoader.getPathToAttach(user.photo.photo_small, true).toString(), opts);
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
						bitmap = BitmapFactory.decodeFile(FileLoader.getPathToAttach(chat.photo.photo_small, true).toString(), opts);
					} catch (Throwable e) {
						FileLog.e(e);
					}
				}
			}
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

	protected void showIncomingNotification(String name, CharSequence subText, TLObject userOrChat, boolean video, int additionalMemberCount) {
		Intent intent = new Intent(this, LaunchActivity.class);
		intent.setAction("voip");
		Notification.Builder builder = new Notification.Builder(this)
				.setContentTitle(video ? LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding) : LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding))
				.setContentText(name)
				.setSmallIcon(R.drawable.notification)
				.setSubText(subText)
				.setContentIntent(PendingIntent.getActivity(this, 0, intent, 0));
		Uri soundProviderUri = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".call_sound_provider/start_ringing");
		if (Build.VERSION.SDK_INT >= 26) {
			SharedPreferences nprefs = MessagesController.getGlobalNotificationsSettings();
			int chanIndex = nprefs.getInt("calls_notification_channel", 0);
			NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			NotificationChannel oldChannel = nm.getNotificationChannel("incoming_calls" + chanIndex);
			if (oldChannel != null) {
				nm.deleteNotificationChannel(oldChannel.getId());
			}
			NotificationChannel existingChannel = nm.getNotificationChannel("incoming_calls2" + chanIndex);
			boolean needCreate = true;
			if (existingChannel != null) {
				if (existingChannel.getImportance() < NotificationManager.IMPORTANCE_HIGH || !soundProviderUri.equals(existingChannel.getSound()) || existingChannel.getVibrationPattern() != null || existingChannel.shouldVibrate()) {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.d("User messed up the notification channel; deleting it and creating a proper one");
					}
					nm.deleteNotificationChannel("incoming_calls2" + chanIndex);
					chanIndex++;
					nprefs.edit().putInt("calls_notification_channel", chanIndex).commit();
				} else {
					needCreate = false;
				}
			}
			if (needCreate) {
				AudioAttributes attrs = new AudioAttributes.Builder()
						.setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
						.build();
				NotificationChannel chan = new NotificationChannel("incoming_calls2" + chanIndex, LocaleController.getString("IncomingCalls", R.string.IncomingCalls), NotificationManager.IMPORTANCE_HIGH);
				chan.setSound(soundProviderUri, attrs);
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
			builder.setChannelId("incoming_calls2" + chanIndex);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setSound(soundProviderUri, AudioManager.STREAM_RING);
		}
		Intent endIntent = new Intent(this, VoIPActionsReceiver.class);
		endIntent.setAction(getPackageName() + ".DECLINE_CALL");
		endIntent.putExtra("call_id", getCallID());
		CharSequence endTitle = LocaleController.getString("VoipDeclineCall", R.string.VoipDeclineCall);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			endTitle = new SpannableString(endTitle);
			((SpannableString) endTitle).setSpan(new ForegroundColorSpan(0xFFF44336), 0, endTitle.length(), 0);
		}
		PendingIntent endPendingIntent = PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		builder.addAction(R.drawable.ic_call_end_white_24dp, endTitle, endPendingIntent);
		Intent answerIntent = new Intent(this, VoIPActionsReceiver.class);
		answerIntent.setAction(getPackageName() + ".ANSWER_CALL");
		answerIntent.putExtra("call_id", getCallID());
		CharSequence answerTitle = LocaleController.getString("VoipAnswerCall", R.string.VoipAnswerCall);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			answerTitle = new SpannableString(answerTitle);
			((SpannableString) answerTitle).setSpan(new ForegroundColorSpan(0xFF00AA00), 0, answerTitle.length(), 0);
		}
		PendingIntent answerPendingIntent = PendingIntent.getBroadcast(this, 0, answerIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		builder.addAction(R.drawable.ic_call, answerTitle, answerPendingIntent);
		builder.setPriority(Notification.PRIORITY_MAX);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			builder.setShowWhen(false);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setColor(0xff2ca5e0);
			builder.setVibrate(new long[0]);
			builder.setCategory(Notification.CATEGORY_CALL);
			builder.setFullScreenIntent(PendingIntent.getActivity(this, 0, intent, 0), true);
			if (userOrChat instanceof TLRPC.User) {
				TLRPC.User user = (TLRPC.User) userOrChat;
				if (!TextUtils.isEmpty(user.phone)) {
					builder.addPerson("tel:" + user.phone);
				}
			}
		}
		Notification incomingNotification = builder.getNotification();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			RemoteViews customView = new RemoteViews(getPackageName(), LocaleController.isRTL ? R.layout.call_notification_rtl : R.layout.call_notification);
			customView.setTextViewText(R.id.name, name);
			boolean subtitleVisible = true;
			if (TextUtils.isEmpty(subText)) {
				customView.setViewVisibility(R.id.subtitle, View.GONE);
				if (UserConfig.getActivatedAccountsCount() > 1) {
					TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();
					customView.setTextViewText(R.id.title, video ? LocaleController.formatString("VoipInVideoCallBrandingWithName", R.string.VoipInVideoCallBrandingWithName, ContactsController.formatName(self.first_name, self.last_name)) : LocaleController.formatString("VoipInCallBrandingWithName", R.string.VoipInCallBrandingWithName, ContactsController.formatName(self.first_name, self.last_name)));
				} else {
					customView.setTextViewText(R.id.title, video ? LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding) : LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding));
				}
			} else {
				if (UserConfig.getActivatedAccountsCount() > 1) {
					TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();
					customView.setTextViewText(R.id.subtitle, LocaleController.formatString("VoipAnsweringAsAccount", R.string.VoipAnsweringAsAccount, ContactsController.formatName(self.first_name, self.last_name)));
				} else {
					customView.setViewVisibility(R.id.subtitle, View.GONE);
				}
				customView.setTextViewText(R.id.title, subText);
			}
			Bitmap avatar = getRoundAvatarBitmap(userOrChat);
			customView.setTextViewText(R.id.answer_text, LocaleController.getString("VoipAnswerCall", R.string.VoipAnswerCall));
			customView.setTextViewText(R.id.decline_text, LocaleController.getString("VoipDeclineCall", R.string.VoipDeclineCall));
			customView.setImageViewBitmap(R.id.photo, avatar);
			customView.setOnClickPendingIntent(R.id.answer_btn, answerPendingIntent);
			customView.setOnClickPendingIntent(R.id.decline_btn, endPendingIntent);
			builder.setLargeIcon(avatar);

			incomingNotification.headsUpContentView = incomingNotification.bigContentView = customView;
		}
		startForeground(ID_INCOMING_CALL_NOTIFICATION, incomingNotification);
	}

	protected void callFailed(String error) {
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
	public void onConnectionStateChanged(int newState) {
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
				if (spPlayID != 0) {
					soundPool.stop(spPlayID);
					spPlayID = 0;
				}
			});
			if (groupCall == null && !wasEstablished) {
				wasEstablished = true;
				if (!isProximityNear && !privateCall.video) {
					Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
					if (vibrator.hasVibrator()) {
						vibrator.vibrate(100);
					}
				}
				AndroidUtilities.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (tgVoip != null) {
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
		if (newState == STATE_RECONNECTING) {
			Utilities.globalQueue.postRunnable(() -> {
				if (spPlayID != 0) {
					soundPool.stop(spPlayID);
				}
				spPlayID = soundPool.play(groupCall != null ? spVoiceChatConnecting : spConnectingId, 1, 1, 0, -1, 1);
			});
		}
		dispatchStateChanged(newState);
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

	public void onMediaStateUpdated(int audioState, int videoState) {
		AndroidUtilities.runOnUIThread(() -> {
			currentAudioState = audioState;
			currentVideoState = videoState;
			checkIsNear();

			for (int a = 0; a < stateListeners.size(); a++) {
				StateListener l = stateListeners.get(a);
				l.onMediaStateUpdated(audioState, videoState);
			}
		});
	}

	protected void callEnded() {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("Call " + getCallID() + " ended");
		}
		if (groupCall != null && (!playedConnectedSound || onDestroyRunnable != null)) {
			needPlayEndSound = false;
		}
		AndroidUtilities.runOnUIThread(() -> dispatchStateChanged(STATE_ENDED));
		int delay = 700;
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

	protected void endConnectionServiceCall(long delay) {
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || privateCall.video && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
			try {
				PendingIntent.getActivity(VoIPBaseService.this, 0, new Intent(VoIPBaseService.this, VoIPPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0).send();
			} catch (Exception x) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("Error starting permission activity", x);
				}
			}
			return;
		}
		acceptIncomingCall();
		try {
			PendingIntent.getActivity(VoIPBaseService.this, 0, new Intent(VoIPBaseService.this, getUIActivityClass()).setAction("voip"), 0).send();
		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("Error starting incall activity", x);
			}
		}
	}

	public void updateOutputGainControlState() {
		if (tgVoip != null) {
			if (!USE_CONNECTION_SERVICE) {
				final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
				tgVoip.setAudioOutputGainControlEnabled(hasEarpiece() && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged);
				tgVoip.setEchoCancellationStrength(isHeadsetPlugged || (hasEarpiece() && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged) ? 0 : 1);
			} else {
				final boolean isEarpiece = systemCallConnection.getCallAudioState().getRoute() == CallAudioState.ROUTE_EARPIECE;
				tgVoip.setAudioOutputGainControlEnabled(isEarpiece);
				tgVoip.setEchoCancellationStrength(isEarpiece ? 0 : 1);
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

	protected boolean isFinished() {
		return currentState == STATE_ENDED || currentState == STATE_FAILED;
	}

	protected boolean isRinging() {
		return false;
	}

	public int getCurrentAudioState() {
		return currentAudioState;
	}

	public int getCurrentVideoState() {
		return currentVideoState;
	}

	@TargetApi(Build.VERSION_CODES.O)
	protected PhoneAccountHandle addAccountToTelecomManager() {
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
