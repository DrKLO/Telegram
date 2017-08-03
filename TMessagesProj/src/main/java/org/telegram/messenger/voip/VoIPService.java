/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package org.telegram.messenger.voip;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
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
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.VoIPActivity;
import org.telegram.ui.VoIPFeedbackActivity;
import org.telegram.ui.VoIPPermissionActivity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

public class VoIPService extends Service implements VoIPController.ConnectionStateListener, SensorEventListener, AudioManager.OnAudioFocusChangeListener, NotificationCenter.NotificationCenterDelegate{

	private static final int ID_ONGOING_CALL_NOTIFICATION = 201;
	private static final int ID_INCOMING_CALL_NOTIFICATION = 202;

	private static final int CALL_MIN_LAYER = 65;
	private static final int CALL_MAX_LAYER = 65;

	public static final int STATE_WAIT_INIT = 1;
	public static final int STATE_WAIT_INIT_ACK = 2;
	public static final int STATE_ESTABLISHED = 3;
	public static final int STATE_FAILED = 4;
	public static final int STATE_RECONNECTING = 5;

	public static final int STATE_HANGING_UP = 10;
	public static final int STATE_ENDED = 11;
	public static final int STATE_EXCHANGING_KEYS = 12;
	public static final int STATE_WAITING = 13;
	public static final int STATE_REQUESTING = 14;
	public static final int STATE_WAITING_INCOMING = 15;
	public static final int STATE_RINGING = 16;
	public static final int STATE_BUSY = 17;

	public static final int DISCARD_REASON_HANGUP = 1;
	public static final int DISCARD_REASON_DISCONNECT = 2;
	public static final int DISCARD_REASON_MISSED = 3;
	public static final int DISCARD_REASON_LINE_BUSY = 4;

	private static final String TAG = "tg-voip-service";

	private static VoIPService sharedInstance;

	private int userID;
	private TLRPC.User user;
	private boolean isOutgoing;
	private TLRPC.PhoneCall call;
	private Notification ongoingCallNotification;
	private VoIPController controller;
	private int currentState = 0;
	private int endHash;
	private int callReqId;
	private int lastError;

	private byte[] g_a;
	private byte[] a_or_b;
	private byte[] g_a_hash;
	private byte[] authKey;
	private long keyFingerprint;

	public static TLRPC.PhoneCall callIShouldHavePutIntoIntent;

	private static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;

	private PowerManager.WakeLock proximityWakelock;
	private PowerManager.WakeLock cpuWakelock;
	private boolean isProximityNear, isHeadsetPlugged;
	private ArrayList<StateListener> stateListeners = new ArrayList<>();
	private MediaPlayer ringtonePlayer;
	private Vibrator vibrator;
	private SoundPool soundPool;
	private int spRingbackID, spFailedID, spEndId, spBusyId, spConnectingId;
	private int spPlayID;
	private boolean needPlayEndSound;
	private Runnable timeoutRunnable;
	private boolean haveAudioFocus;
	private boolean playingSound;
	private boolean micMute;
	private boolean controllerStarted;
	private long lastKnownDuration = 0;
	private VoIPController.Stats stats = new VoIPController.Stats(), prevStats = new VoIPController.Stats();
	private NetworkInfo lastNetInfo;
	private Boolean mHasEarpiece = null;
	private BluetoothAdapter btAdapter;
	private boolean isBtHeadsetConnected;
	private boolean needSendDebugLog=false;
	private boolean endCallAfterRequest=false;
	private boolean wasEstablished;
	private ArrayList<TLRPC.PhoneCall> pendingUpdates=new ArrayList<>();
	private Runnable afterSoundRunnable=new Runnable(){
		@Override
		public void run(){
			soundPool.release();
			if(isBtHeadsetConnected)
				((AudioManager)ApplicationLoader.applicationContext.getSystemService(AUDIO_SERVICE)).stopBluetoothSco();
			((AudioManager)ApplicationLoader.applicationContext.getSystemService(AUDIO_SERVICE)).setSpeakerphoneOn(false);
		}
	};

	public static final String ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG";

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_HEADSET_PLUG.equals(intent.getAction())) {
				isHeadsetPlugged = intent.getIntExtra("state", 0) == 1;
				if (isHeadsetPlugged && proximityWakelock != null && proximityWakelock.isHeld()) {
					proximityWakelock.release();
				}
				isProximityNear = false;
			} else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
				updateNetworkType();
			} else if ((getPackageName() + ".END_CALL").equals(intent.getAction())) {
				if (intent.getIntExtra("end_hash", 0) == endHash) {
					stopForeground(true);
					hangUp();
				}
			} else if ((getPackageName() + ".DECLINE_CALL").equals(intent.getAction())) {
				if (intent.getIntExtra("end_hash", 0) == endHash) {
					stopForeground(true);
					declineIncomingCall(DISCARD_REASON_LINE_BUSY, null);
				}
			} else if ((getPackageName() + ".ANSWER_CALL").equals(intent.getAction())) {
				if (intent.getIntExtra("end_hash", 0) == endHash) {
					showNotification();
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
						try {
							PendingIntent.getActivity(VoIPService.this, 0, new Intent(VoIPService.this, VoIPPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0).send();
						} catch (Exception x) {
							FileLog.e("Error starting permission activity", x);
						}
						return;
					}
					acceptIncomingCall();
					try {
						PendingIntent.getActivity(VoIPService.this, 0, new Intent(VoIPService.this, VoIPActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), 0).send();
					} catch (Exception x) {
						FileLog.e("Error starting incall activity", x);
					}
				}
			}else if(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())){
				//FileLog.e("bt headset state = "+intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0));
				updateBluetoothHeadsetState(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0)==BluetoothProfile.STATE_CONNECTED);
			}else if(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(intent.getAction())){
				for (StateListener l : stateListeners)
					l.onAudioSettingsChanged();
			}else if(TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())){
				String state=intent.getStringExtra(TelephonyManager.EXTRA_STATE);
				if(TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)){
					hangUp();
				}
			}
		}
	};

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(sharedInstance!=null){
			FileLog.e("Tried to start the VoIP service when it's already started");
			return START_NOT_STICKY;
		}
		userID = intent.getIntExtra("user_id", 0);
		isOutgoing = intent.getBooleanExtra("is_outgoing", false);
		user = MessagesController.getInstance().getUser(userID);

		if(user==null){
			FileLog.w("VoIPService: user==null");
			stopSelf();
			return START_NOT_STICKY;
		}

		if (isOutgoing) {
			startOutgoingCall();
			if (intent.getBooleanExtra("start_incall_activity", false)) {
				startActivity(new Intent(this, VoIPActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			}
		} else {
			NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeInCallActivity);
			call = callIShouldHavePutIntoIntent;
			callIShouldHavePutIntoIntent = null;
			acknowledgeCallAndStartRinging();
		}
		sharedInstance = this;


		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		FileLog.d("=============== VoIPService STARTING ===============");
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)!=null) {
			int outFramesPerBuffer = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
			VoIPController.setNativeBufferSize(outFramesPerBuffer);
		} else {
			VoIPController.setNativeBufferSize(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2);
		}
		final SharedPreferences preferences = getSharedPreferences("mainconfig", MODE_PRIVATE);
		VoIPServerConfig.setConfig(preferences.getString("voip_server_config", "{}"));
		if(System.currentTimeMillis()-preferences.getLong("voip_server_config_updated", 0)>24*3600000){
			ConnectionsManager.getInstance().sendRequest(new TLRPC.TL_phone_getCallConfig(), new RequestDelegate(){
				@Override
				public void run(TLObject response, TLRPC.TL_error error){
					if(error==null){
						String data=((TLRPC.TL_dataJSON) response).data;
						VoIPServerConfig.setConfig(data);
						preferences.edit().putString("voip_server_config", data).putLong("voip_server_config_updated", BuildConfig.DEBUG ? 0 : System.currentTimeMillis()).apply();
					}
				}
			});
		}
		try {
			controller = new VoIPController();
			controller.setConnectionStateListener(this);

			cpuWakelock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "telegram-voip");
			cpuWakelock.acquire();

			btAdapter=am.isBluetoothScoAvailableOffCall() ? BluetoothAdapter.getDefaultAdapter() : null;

			IntentFilter filter = new IntentFilter();
			filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
			filter.addAction(ACTION_HEADSET_PLUG);
			if(btAdapter!=null){
				filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
				filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
			}
			filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
			filter.addAction(getPackageName() + ".END_CALL");
			filter.addAction(getPackageName() + ".DECLINE_CALL");
			filter.addAction(getPackageName() + ".ANSWER_CALL");
			registerReceiver(receiver, filter);

			ConnectionsManager.getInstance().setAppPaused(false, false);

			soundPool = new SoundPool(1, AudioManager.STREAM_VOICE_CALL, 0);
			spConnectingId = soundPool.load(this, R.raw.voip_connecting, 1);
			spRingbackID = soundPool.load(this, R.raw.voip_ringback, 1);
			spFailedID = soundPool.load(this, R.raw.voip_failed, 1);
			spEndId = soundPool.load(this, R.raw.voip_end, 1);
			spBusyId = soundPool.load(this, R.raw.voip_busy, 1);

			am.registerMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));

			if(btAdapter!=null && btAdapter.isEnabled()){
				int headsetState=btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
				updateBluetoothHeadsetState(headsetState==BluetoothProfile.STATE_CONNECTED);
				if(headsetState==BluetoothProfile.STATE_CONNECTED)
					am.setBluetoothScoOn(true);
				for (StateListener l : stateListeners)
					l.onAudioSettingsChanged();
			}

			NotificationCenter.getInstance().addObserver(this, NotificationCenter.appDidLogout);
		} catch (Exception x) {
			FileLog.e("error initializing voip controller", x);
			callFailed();
		}
	}

	@Override
	public void onDestroy() {
		FileLog.d("=============== VoIPService STOPPING ===============");
		stopForeground(true);
		stopRinging();
		NotificationCenter.getInstance().removeObserver(this, NotificationCenter.appDidLogout);
		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		if (proximity != null) {
			sm.unregisterListener(this);
		}
		if (proximityWakelock != null && proximityWakelock.isHeld()) {
			proximityWakelock.release();
		}
		unregisterReceiver(receiver);
		if(timeoutRunnable!=null){
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
			timeoutRunnable=null;
		}
		super.onDestroy();
		sharedInstance = null;
		AndroidUtilities.runOnUIThread(new Runnable(){
			@Override
			public void run(){
				NotificationCenter.getInstance().postNotificationName(NotificationCenter.didEndedCall);
			}
		});
		if (controller != null && controllerStarted) {
			lastKnownDuration = controller.getCallDuration();
			updateStats();
			StatsController.getInstance().incrementTotalCallsTime(getStatsNetworkType(), (int) (lastKnownDuration / 1000) % 5);
			if(needSendDebugLog){
				String debugLog=controller.getDebugLog();
				TLRPC.TL_phone_saveCallDebug req=new TLRPC.TL_phone_saveCallDebug();
				req.debug=new TLRPC.TL_dataJSON();
				req.debug.data=debugLog;
				req.peer=new TLRPC.TL_inputPhoneCall();
				req.peer.access_hash=call.access_hash;
				req.peer.id=call.id;
				ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate(){
					@Override
					public void run(TLObject response, TLRPC.TL_error error){
						FileLog.d("Sent debug logs, response="+response);
					}
				});
			}
			controller.release();
			controller = null;
		}
		cpuWakelock.release();
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if(isBtHeadsetConnected && !playingSound){
			am.stopBluetoothSco();
			am.setSpeakerphoneOn(false);
		}
		try{
			am.setMode(AudioManager.MODE_NORMAL);
		}catch(SecurityException x){
			FileLog.e("Error setting audio more to normal", x);
		}
		am.unregisterMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));
		if (haveAudioFocus)
			am.abandonAudioFocus(this);

		if (!playingSound)
			soundPool.release();

		ConnectionsManager.getInstance().setAppPaused(true, false);
	}

	public static VoIPService getSharedInstance() {
		return sharedInstance;
	}

	public TLRPC.User getUser() {
		return user;
	}

	public void setMicMute(boolean mute) {
		controller.setMicMute(micMute = mute);
	}

	public boolean isMicMute() {
		return micMute;
	}

	public String getDebugString() {
		return controller.getDebugString();
	}

	public long getCallDuration() {
		if (!controllerStarted || controller == null)
			return lastKnownDuration;
		return lastKnownDuration = controller.getCallDuration();
	}

	public void hangUp() {
		declineIncomingCall(currentState == STATE_RINGING || (currentState==STATE_WAITING && isOutgoing) ? DISCARD_REASON_MISSED : DISCARD_REASON_HANGUP, null);
	}

	public void hangUp(Runnable onDone) {
		declineIncomingCall(currentState == STATE_RINGING || (currentState==STATE_WAITING && isOutgoing) ? DISCARD_REASON_MISSED : DISCARD_REASON_HANGUP, onDone);
	}

	public void registerStateListener(StateListener l) {
		stateListeners.add(l);
		if (currentState != 0)
			l.onStateChanged(currentState);
	}

	public void unregisterStateListener(StateListener l) {
		stateListeners.remove(l);
	}

	private void startOutgoingCall() {
		configureDeviceForCall();
		showNotification();
		startConnectingSound();
		dispatchStateChanged(STATE_REQUESTING);
		AndroidUtilities.runOnUIThread(new Runnable(){
			@Override
			public void run(){
				NotificationCenter.getInstance().postNotificationName(NotificationCenter.didStartedCall);
			}
		});
		final byte[] salt = new byte[256];
		Utilities.random.nextBytes(salt);

		TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
		req.random_length = 256;
		req.version = MessagesStorage.lastSecretVersion;
		callReqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
			@Override
			public void run(TLObject response, TLRPC.TL_error error) {
				callReqId = 0;
				if (error == null) {
					TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
					if (response instanceof TLRPC.TL_messages_dhConfig) {
						if (!Utilities.isGoodPrime(res.p, res.g)) {
							callFailed();
							return;
						}
						MessagesStorage.secretPBytes = res.p;
						MessagesStorage.secretG = res.g;
						MessagesStorage.lastSecretVersion = res.version;
						MessagesStorage.getInstance().saveSecretParams(MessagesStorage.lastSecretVersion, MessagesStorage.secretG, MessagesStorage.secretPBytes);
					}
					final byte[] salt = new byte[256];
					for (int a = 0; a < 256; a++) {
						salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
					}

					BigInteger i_g_a = BigInteger.valueOf(MessagesStorage.secretG);
					i_g_a = i_g_a.modPow(new BigInteger(1, salt), new BigInteger(1, MessagesStorage.secretPBytes));
					byte[] g_a = i_g_a.toByteArray();
					if (g_a.length > 256) {
						byte[] correctedAuth = new byte[256];
						System.arraycopy(g_a, 1, correctedAuth, 0, 256);
						g_a = correctedAuth;
					}

					TLRPC.TL_phone_requestCall reqCall = new TLRPC.TL_phone_requestCall();
					reqCall.user_id = MessagesController.getInputUser(user);
					reqCall.protocol = new TLRPC.TL_phoneCallProtocol();
					reqCall.protocol.udp_p2p = true;
					reqCall.protocol.udp_reflector = true;
					reqCall.protocol.min_layer = CALL_MIN_LAYER;
					reqCall.protocol.max_layer = CALL_MAX_LAYER;
					VoIPService.this.g_a=g_a;
					reqCall.g_a_hash = Utilities.computeSHA256(g_a, 0, g_a.length);
					reqCall.random_id = Utilities.random.nextInt();

					ConnectionsManager.getInstance().sendRequest(reqCall, new RequestDelegate() {
						@Override
						public void run(final TLObject response, final TLRPC.TL_error error) {
							AndroidUtilities.runOnUIThread(new Runnable(){
								@Override
								public void run(){
									if (error == null) {
										call = ((TLRPC.TL_phone_phoneCall) response).phone_call;
										a_or_b = salt;
										dispatchStateChanged(STATE_WAITING);
										if(endCallAfterRequest){
											hangUp();
											return;
										}
										if(pendingUpdates.size()>0 && call!=null){
											for(TLRPC.PhoneCall call:pendingUpdates){
												onCallUpdated(call);
											}
											pendingUpdates.clear();
										}
										timeoutRunnable = new Runnable() {
											@Override
											public void run() {
												timeoutRunnable=null;
												TLRPC.TL_phone_discardCall req = new TLRPC.TL_phone_discardCall();
												req.peer = new TLRPC.TL_inputPhoneCall();
												req.peer.access_hash = call.access_hash;
												req.peer.id = call.id;
												req.reason=new TLRPC.TL_phoneCallDiscardReasonMissed();
												ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
													@Override
													public void run(TLObject response, TLRPC.TL_error error) {
														if (error != null) {
															FileLog.e("error on phone.discardCall: " + error);
														} else {
															FileLog.d("phone.discardCall " + response);
														}
														AndroidUtilities.runOnUIThread(new Runnable(){
															@Override
															public void run(){
																callFailed();
															}
														});
													}
												}, ConnectionsManager.RequestFlagFailOnServerErrors);
											}
										};
										AndroidUtilities.runOnUIThread(timeoutRunnable, MessagesController.getInstance().callReceiveTimeout);
									} else {
										if (error.code == 400 && "PARTICIPANT_VERSION_OUTDATED".equals(error.text)) {
											callFailed(VoIPController.ERROR_PEER_OUTDATED);
										} else if(error.code==403 && "USER_PRIVACY_RESTRICTED".equals(error.text)){
											callFailed(VoIPController.ERROR_PRIVACY);
										}else if(error.code==406){
											callFailed(VoIPController.ERROR_LOCALIZED);
										}else {
											FileLog.e("Error on phone.requestCall: " + error);
											callFailed();
										}
									}
								}
							});
						}
					}, ConnectionsManager.RequestFlagFailOnServerErrors);
				} else {
					FileLog.e("Error on getDhConfig " + error);
					callFailed();
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors);
	}

	private void acknowledgeCallAndStartRinging(){
		if(call instanceof TLRPC.TL_phoneCallDiscarded){
			FileLog.w("Call "+call.id+" was discarded before the service started, stopping");
			stopSelf();
			return;
		}
		TLRPC.TL_phone_receivedCall req = new TLRPC.TL_phone_receivedCall();
		req.peer = new TLRPC.TL_inputPhoneCall();
		req.peer.id = call.id;
		req.peer.access_hash = call.access_hash;
		ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
			@Override
			public void run(final TLObject response, final TLRPC.TL_error error) {
				AndroidUtilities.runOnUIThread(new Runnable(){
					@Override
					public void run(){
						if(sharedInstance==null)
							return;
						FileLog.w("receivedCall response = " + response);
						if (error != null){
							FileLog.e("error on receivedCall: "+error);
							stopSelf();
						}else{
							startRinging();
						}
					}
				});
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors);
	}

	private void startRinging() {
		FileLog.d("starting ringing for call "+call.id);
		dispatchStateChanged(STATE_WAITING_INCOMING);
		//ringtone=RingtoneManager.getRingtone(this, Settings.System.DEFAULT_RINGTONE_URI);
		//ringtone.play();
		SharedPreferences prefs = getSharedPreferences("Notifications", MODE_PRIVATE);
		ringtonePlayer = new MediaPlayer();
		ringtonePlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mediaPlayer) {
				ringtonePlayer.start();
			}
		});
		ringtonePlayer.setLooping(true);
		ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
		try {
			String notificationUri;
			if (prefs.getBoolean("custom_" + user.id, false))
				notificationUri = prefs.getString("ringtone_path_" + user.id, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString());
			else
				notificationUri = prefs.getString("CallsRingtonePath", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString());
			ringtonePlayer.setDataSource(this, Uri.parse(notificationUri));
			ringtonePlayer.prepareAsync();
		} catch (Exception e) {
			FileLog.e(e);
			if(ringtonePlayer!=null){
				ringtonePlayer.release();
				ringtonePlayer=null;
			}
		}
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		int vibrate;
		if (prefs.getBoolean("custom_" + user.id, false))
			vibrate = prefs.getInt("calls_vibrate_" + user.id, 0);
		else
			vibrate = prefs.getInt("vibrate_calls", 0);
		if ((vibrate != 2 && vibrate != 4 && (am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE || am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL)) ||
				(vibrate == 4 && am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)) {
			vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			long duration = 700;
			if (vibrate == 1)
				duration /= 2;
			else if (vibrate == 3)
				duration *= 2;
			vibrator.vibrate(new long[]{0, duration, 500}, 0);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode() && NotificationManagerCompat.from(this).areNotificationsEnabled()) {
			showIncomingNotification();
			FileLog.d("Showing incoming call notification");
		} else {
			FileLog.d("Starting incall activity for incoming call");
			try {
				PendingIntent.getActivity(VoIPService.this, 12345, new Intent(VoIPService.this, VoIPActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0).send();
			} catch (Exception x) {
				FileLog.e("Error starting incall activity", x);
			}
		}

	}

	public void acceptIncomingCall() {
		stopRinging();
		showNotification();
		configureDeviceForCall();
		startConnectingSound();
		dispatchStateChanged(STATE_EXCHANGING_KEYS);
		AndroidUtilities.runOnUIThread(new Runnable(){
			@Override
			public void run(){
				NotificationCenter.getInstance().postNotificationName(NotificationCenter.didStartedCall);
			}
		});
		TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
		req.random_length = 256;
		req.version = MessagesStorage.lastSecretVersion;
		ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
			@Override
			public void run(TLObject response, TLRPC.TL_error error) {
				if (error == null) {
					TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
					if (response instanceof TLRPC.TL_messages_dhConfig) {
						if (!Utilities.isGoodPrime(res.p, res.g)) {
							/*acceptingChats.remove(encryptedChat.id);
							declineSecretChat(encryptedChat.id);*/
							FileLog.e("stopping VoIP service, bad prime");
							callFailed();
							return;
						}

						MessagesStorage.secretPBytes = res.p;
						MessagesStorage.secretG = res.g;
						MessagesStorage.lastSecretVersion = res.version;
						MessagesStorage.getInstance().saveSecretParams(MessagesStorage.lastSecretVersion, MessagesStorage.secretG, MessagesStorage.secretPBytes);
					}
					byte[] salt = new byte[256];
					for (int a = 0; a < 256; a++) {
						salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
					}
					a_or_b = salt;
					BigInteger g_b = BigInteger.valueOf(MessagesStorage.secretG);
					BigInteger p = new BigInteger(1, MessagesStorage.secretPBytes);
					g_b = g_b.modPow(new BigInteger(1, salt), p);
					g_a_hash=call.g_a_hash;

					byte[] g_b_bytes = g_b.toByteArray();
					if (g_b_bytes.length > 256) {
						byte[] correctedAuth = new byte[256];
						System.arraycopy(g_b_bytes, 1, correctedAuth, 0, 256);
						g_b_bytes = correctedAuth;
					}

					TLRPC.TL_phone_acceptCall req = new TLRPC.TL_phone_acceptCall();
					req.g_b = g_b_bytes;
					//req.key_fingerprint = Utilities.bytesToLong(authKeyId);
					req.peer = new TLRPC.TL_inputPhoneCall();
					req.peer.id = call.id;
					req.peer.access_hash = call.access_hash;
					req.protocol = new TLRPC.TL_phoneCallProtocol();
					req.protocol.udp_p2p = req.protocol.udp_reflector = true;
					req.protocol.min_layer = CALL_MIN_LAYER;
					req.protocol.max_layer = CALL_MAX_LAYER;
					ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
						@Override
						public void run(final TLObject response, final TLRPC.TL_error error) {
							AndroidUtilities.runOnUIThread(new Runnable(){
								@Override
								public void run(){
									if (error == null) {
										FileLog.w("accept call ok! " + response);
										call = ((TLRPC.TL_phone_phoneCall) response).phone_call;
										if(call instanceof TLRPC.TL_phoneCallDiscarded){
											onCallUpdated(call);
										}/*else{
									initiateActualEncryptedCall();
								}*/
									} else {
										FileLog.e("Error on phone.acceptCall: " + error);
										callFailed();
									}
								}
							});
						}
					}, ConnectionsManager.RequestFlagFailOnServerErrors);
				} else {
					//acceptingChats.remove(encryptedChat.id);
					callFailed();
				}
			}
		});
	}

	public void declineIncomingCall() {
		declineIncomingCall(DISCARD_REASON_HANGUP, null);
	}

	public void declineIncomingCall(int reason, final Runnable onDone) {
		if(currentState==STATE_REQUESTING){
			dispatchStateChanged(STATE_HANGING_UP);
			endCallAfterRequest=true;
			return;
		}
		if (currentState == STATE_HANGING_UP || currentState == STATE_ENDED)
			return;
		dispatchStateChanged(STATE_HANGING_UP);
		if (call == null) {
			if (onDone != null)
				onDone.run();
			callEnded();
			if (callReqId != 0) {
				ConnectionsManager.getInstance().cancelRequest(callReqId, false);
				callReqId = 0;
			}
			return;
		}
		TLRPC.TL_phone_discardCall req = new TLRPC.TL_phone_discardCall();
		req.peer = new TLRPC.TL_inputPhoneCall();
		req.peer.access_hash = call.access_hash;
		req.peer.id = call.id;
		req.duration = controller != null && controllerStarted ? (int) (controller.getCallDuration() / 1000) : 0;
		req.connection_id = controller != null && controllerStarted ? controller.getPreferredRelayID() : 0;
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
		final boolean wasNotConnected=ConnectionsManager.getInstance().getConnectionState()!=ConnectionsManager.ConnectionStateConnected;
		final Runnable stopper;
		if(wasNotConnected){
			if (onDone != null)
				onDone.run();
			callEnded();
			stopper=null;
		}else{
			stopper=new Runnable(){
				private boolean done=false;

				@Override
				public void run(){
					if(done)
						return;
					done=true;
					if(onDone!=null)
						onDone.run();
					callEnded();
				}
			};
			AndroidUtilities.runOnUIThread(stopper, (int) (VoIPServerConfig.getDouble("hangup_ui_timeout", 5)*1000));
		}
		ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
			@Override
			public void run(TLObject response, TLRPC.TL_error error) {
				if (error != null) {
					FileLog.e("error on phone.discardCall: " + error);
				} else {
					if (response instanceof TLRPC.TL_updates) {
						TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
						MessagesController.getInstance().processUpdates(updates, false);
					}
					FileLog.d("phone.discardCall " + response);
				}
				if (!wasNotConnected){
					AndroidUtilities.cancelRunOnUIThread(stopper);
					if(onDone!=null)
						onDone.run();
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors);
	}

	private void dumpCallObject(){
		try{
			Field[] flds=TLRPC.PhoneCall.class.getFields();
			for(Field f:flds){
				FileLog.d(f.getName()+" = "+f.get(call));
			}
		}catch(Exception x){
			FileLog.e(x);
		}
	}

	public void onCallUpdated(TLRPC.PhoneCall call) {
		if(this.call==null){
			pendingUpdates.add(call);
			return;
		}
		if(call==null)
			return;
		if(call.id!=this.call.id){
			if(BuildVars.DEBUG_VERSION)
				FileLog.w("onCallUpdated called with wrong call id (got "+call.id+", expected "+this.call.id+")");
			return;
		}
		if(call.access_hash==0)
			call.access_hash=this.call.access_hash;
		if(BuildVars.DEBUG_VERSION){
			FileLog.d("Call updated: "+call);
			dumpCallObject();
		}
		this.call = call;
		if (call instanceof TLRPC.TL_phoneCallDiscarded) {
			needSendDebugLog=call.need_debug;
			FileLog.d("call discarded, stopping service");
			if (call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
				dispatchStateChanged(STATE_BUSY);
				playingSound = true;
				soundPool.play(spBusyId, 1, 1, 0, -1, 1);
				AndroidUtilities.runOnUIThread(afterSoundRunnable, 1500);
				stopSelf();
			} else {
				callEnded();
			}
			if (call.need_rating) {
				startRatingActivity();
			}
		} else if (call instanceof TLRPC.TL_phoneCall && authKey == null){
			if(call.g_a_or_b==null){
				FileLog.w("stopping VoIP service, Ga == null");
				callFailed();
				return;
			}
			if(!Arrays.equals(g_a_hash, Utilities.computeSHA256(call.g_a_or_b, 0, call.g_a_or_b.length))){
				FileLog.w("stopping VoIP service, Ga hash doesn't match");
				callFailed();
				return;
			}
			g_a=call.g_a_or_b;
			BigInteger g_a = new BigInteger(1, call.g_a_or_b);
			BigInteger p = new BigInteger(1, MessagesStorage.secretPBytes);

			if (!Utilities.isGoodGaAndGb(g_a, p)) {
				FileLog.w("stopping VoIP service, bad Ga and Gb (accepting)");
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
					authKey[a] = 0;
				}
				authKey = correctedAuth;
			}
			byte[] authKeyHash = Utilities.computeSHA1(authKey);
			byte[] authKeyId = new byte[8];
			System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
			VoIPService.this.authKey = authKey;
			keyFingerprint = Utilities.bytesToLong(authKeyId);

			if(keyFingerprint!=call.key_fingerprint){
				FileLog.w("key fingerprints don't match");
				callFailed();
				return;
			}

			initiateActualEncryptedCall();
		} else if(call instanceof TLRPC.TL_phoneCallAccepted && authKey==null){
			processAcceptedCall();
		} else {
			if (currentState == STATE_WAITING && call.receive_date != 0) {
				dispatchStateChanged(STATE_RINGING);
				FileLog.d("!!!!!! CALL RECEIVED");
				if (spPlayID != 0)
					soundPool.stop(spPlayID);
				spPlayID = soundPool.play(spRingbackID, 1, 1, 0, -1, 1);
				if (timeoutRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
					timeoutRunnable=null;
				}
				timeoutRunnable = new Runnable() {
					@Override
					public void run() {
						timeoutRunnable=null;
						declineIncomingCall(DISCARD_REASON_MISSED, null);
					}
				};
				AndroidUtilities.runOnUIThread(timeoutRunnable, MessagesController.getInstance().callRingTimeout);
			}
		}
	}

	private void startRatingActivity() {
		try {
			PendingIntent.getActivity(VoIPService.this, 0, new Intent(VoIPService.this, VoIPFeedbackActivity.class)
					.putExtra("call_id", call.id)
					.putExtra("call_access_hash", call.access_hash)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), 0).send();
		} catch (Exception x) {
			FileLog.e("Error starting incall activity", x);
		}
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

	public byte[] getEncryptionKey() {
		return authKey;
	}

	private void processAcceptedCall() {
		if(!isProximityNear){
			Vibrator vibrator=(Vibrator) getSystemService(VIBRATOR_SERVICE);
			if(vibrator.hasVibrator())
				vibrator.vibrate(100);
		}

		dispatchStateChanged(STATE_EXCHANGING_KEYS);
		BigInteger p = new BigInteger(1, MessagesStorage.secretPBytes);
		BigInteger i_authKey = new BigInteger(1, call.g_b);

		if (!Utilities.isGoodGaAndGb(i_authKey, p)) {
			FileLog.w("stopping VoIP service, bad Ga and Gb");
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
				authKey[a] = 0;
			}
			authKey = correctedAuth;
		}
		byte[] authKeyHash = Utilities.computeSHA1(authKey);
		byte[] authKeyId = new byte[8];
		System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
		long fingerprint = Utilities.bytesToLong(authKeyId);
		this.authKey=authKey;
		keyFingerprint=fingerprint;
		TLRPC.TL_phone_confirmCall req=new TLRPC.TL_phone_confirmCall();
		req.g_a=g_a;
		req.key_fingerprint=fingerprint;
		req.peer=new TLRPC.TL_inputPhoneCall();
		req.peer.id=call.id;
		req.peer.access_hash=call.access_hash;
		req.protocol=new TLRPC.TL_phoneCallProtocol();
		req.protocol.max_layer=CALL_MAX_LAYER;
		req.protocol.min_layer=CALL_MIN_LAYER;
		req.protocol.udp_p2p=req.protocol.udp_reflector=true;
		ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate(){
			@Override
			public void run(final TLObject response, final TLRPC.TL_error error){
				AndroidUtilities.runOnUIThread(new Runnable(){
					@Override
					public void run(){
						if(error!=null){
							callFailed();
						}else{
							call=((TLRPC.TL_phone_phoneCall)response).phone_call;
							initiateActualEncryptedCall();
						}
					}
				});
			}
		});
	}

	private void initiateActualEncryptedCall() {
		if (timeoutRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
			timeoutRunnable = null;
		}
		try {
			FileLog.d("InitCall: keyID=" + keyFingerprint);
			SharedPreferences nprefs=getSharedPreferences("notifications", MODE_PRIVATE);
			HashSet<String> hashes=new HashSet<>(nprefs.getStringSet("calls_access_hashes", Collections.EMPTY_SET));
			hashes.add(call.id+" "+call.access_hash+" "+System.currentTimeMillis());
			while(hashes.size()>20){
				String oldest=null;
				long oldestTime=Long.MAX_VALUE;
				Iterator<String> itr=hashes.iterator();
				while(itr.hasNext()){
					String item=itr.next();
					String[] s=item.split(" ");
					if(s.length<2){
						itr.remove();
					}else{
						try{
							long t=Long.parseLong(s[2]);
							if(t<oldestTime){
								oldestTime=t;
								oldest=item;
							}
						}catch(Exception x){
							itr.remove();
						}
					}
				}
				if(oldest!=null)
					hashes.remove(oldest);
			}
			nprefs.edit().putStringSet("calls_access_hashes", hashes).apply();
			final SharedPreferences preferences = getSharedPreferences("mainconfig", MODE_PRIVATE);
			controller.setConfig(MessagesController.getInstance().callPacketTimeout / 1000.0, MessagesController.getInstance().callConnectTimeout / 1000.0,
					preferences.getInt("VoipDataSaving", VoIPController.DATA_SAVING_NEVER), call.id);
			controller.setEncryptionKey(authKey, isOutgoing);
			TLRPC.TL_phoneConnection[] endpoints = new TLRPC.TL_phoneConnection[1 + call.alternative_connections.size()];
			endpoints[0] = call.connection;
			for (int i = 0; i < call.alternative_connections.size(); i++)
				endpoints[i + 1] = call.alternative_connections.get(i);

			controller.setRemoteEndpoints(endpoints, call.protocol.udp_p2p && getSharedPreferences("mainconfig", MODE_PRIVATE).getBoolean("calls_p2p", true));
			SharedPreferences prefs=ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
			if(prefs.getBoolean("proxy_enabled", false) && prefs.getBoolean("proxy_enabled_calls", false)){
				String server=prefs.getString("proxy_ip", null);
				if(server!=null){
					controller.setProxy(server, prefs.getInt("proxy_port", 0), prefs.getString("proxy_user", null), prefs.getString("proxy_pass", null));
				}
			}
			controller.start();
			updateNetworkType();
			controller.connect();
			controllerStarted = true;
			AndroidUtilities.runOnUIThread(new Runnable() {
				@Override
				public void run() {
					if (controller == null)
						return;
					updateStats();
					AndroidUtilities.runOnUIThread(this, 5000);
				}
			}, 5000);
		} catch (Exception x) {
			FileLog.e("error starting call", x);
			callFailed();
		}
	}

	private void showNotification() {
		Intent intent = new Intent(this, VoIPActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		Notification.Builder builder = new Notification.Builder(this)
				.setContentTitle(LocaleController.getString("VoipOutgoingCall", R.string.VoipOutgoingCall))
				.setContentText(ContactsController.formatName(user.first_name, user.last_name))
				.setSmallIcon(R.drawable.notification)
				.setContentIntent(PendingIntent.getActivity(this, 0, intent, 0));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			Intent endIntent = new Intent();
			endIntent.setAction(getPackageName() + ".END_CALL");
			endIntent.putExtra("end_hash", endHash = Utilities.random.nextInt());
			builder.addAction(R.drawable.ic_call_end_white_24dp, LocaleController.getString("VoipEndCall", R.string.VoipEndCall), PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT));
			builder.setPriority(Notification.PRIORITY_MAX);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			builder.setShowWhen(false);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setColor(0xff2ca5e0);
		}
		if (user.photo != null) {
			TLRPC.FileLocation photoPath = user.photo.photo_small;
			if (photoPath != null) {
				BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50");
				if (img != null) {
					builder.setLargeIcon(img.getBitmap());
				} else {
					try {
						float scaleFactor = 160.0f / AndroidUtilities.dp(50);
						BitmapFactory.Options options = new BitmapFactory.Options();
						options.inSampleSize = scaleFactor < 1 ? 1 : (int) scaleFactor;
						Bitmap bitmap = BitmapFactory.decodeFile(FileLoader.getPathToAttach(photoPath, true).toString(), options);
						if (bitmap != null) {
							builder.setLargeIcon(bitmap);
						}
					} catch (Throwable e) {
						FileLog.e(e);
					}
				}
			}
		}
		ongoingCallNotification = builder.getNotification();
		startForeground(ID_ONGOING_CALL_NOTIFICATION, ongoingCallNotification);
	}

	private void showIncomingNotification() {
		Intent intent = new Intent(this, VoIPActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		Notification.Builder builder = new Notification.Builder(this)
				.setContentTitle(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding))
				.setContentText(ContactsController.formatName(user.first_name, user.last_name))
				.setSmallIcon(R.drawable.notification)
				.setContentIntent(PendingIntent.getActivity(this, 0, intent, 0));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			endHash = Utilities.random.nextInt();
			Intent endIntent = new Intent();
			endIntent.setAction(getPackageName() + ".DECLINE_CALL");
			endIntent.putExtra("end_hash", endHash);
			CharSequence endTitle=LocaleController.getString("VoipDeclineCall", R.string.VoipDeclineCall);
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
				endTitle=new SpannableString(endTitle);
				((SpannableString)endTitle).setSpan(new ForegroundColorSpan(0xFFF44336), 0, endTitle.length(), 0);
			}
			builder.addAction(R.drawable.ic_call_end_white_24dp, endTitle, PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT));
			Intent answerIntent = new Intent();
			answerIntent.setAction(getPackageName() + ".ANSWER_CALL");
			answerIntent.putExtra("end_hash", endHash);
			CharSequence answerTitle=LocaleController.getString("VoipAnswerCall", R.string.VoipAnswerCall);
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
				answerTitle=new SpannableString(answerTitle);
				((SpannableString)answerTitle).setSpan(new ForegroundColorSpan(0xFF00AA00), 0, answerTitle.length(), 0);
			}
			builder.addAction(R.drawable.ic_call_white_24dp, answerTitle, PendingIntent.getBroadcast(this, 0, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT));
			builder.setPriority(Notification.PRIORITY_MAX);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			builder.setShowWhen(false);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setColor(0xff2ca5e0);
			builder.setVibrate(new long[0]);
			builder.setCategory(Notification.CATEGORY_CALL);
			builder.setFullScreenIntent(PendingIntent.getActivity(this, 0, intent, 0), true);
		}
		if (user.photo != null) {
			TLRPC.FileLocation photoPath = user.photo.photo_small;
			if (photoPath != null) {
				BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50");
				if (img != null) {
					builder.setLargeIcon(img.getBitmap());
				} else {
					try {
						float scaleFactor = 160.0f / AndroidUtilities.dp(50);
						BitmapFactory.Options options = new BitmapFactory.Options();
						options.inSampleSize = scaleFactor < 1 ? 1 : (int) scaleFactor;
						Bitmap bitmap = BitmapFactory.decodeFile(FileLoader.getPathToAttach(photoPath, true).toString(), options);
						if (bitmap != null) {
							builder.setLargeIcon(bitmap);
						}
					} catch (Throwable e) {
						FileLog.e(e);
					}
				}
			}
		}
		Notification incomingNotification = builder.getNotification();
		startForeground(ID_INCOMING_CALL_NOTIFICATION, incomingNotification);
	}

	private void startConnectingSound() {
		if (spPlayID != 0)
			soundPool.stop(spPlayID);
		spPlayID = soundPool.play(spConnectingId, 1, 1, 0, -1, 1);
		if (spPlayID == 0) {
			AndroidUtilities.runOnUIThread(new Runnable() {
				@Override
				public void run() {
					if (sharedInstance == null)
						return;
					if (spPlayID == 0)
						spPlayID = soundPool.play(spConnectingId, 1, 1, 0, -1, 1);
					if (spPlayID == 0)
						AndroidUtilities.runOnUIThread(this, 100);
				}
			}, 100);
		}
	}

	private void callFailed() {
		callFailed(controller != null && controllerStarted ? controller.getLastError() : VoIPController.ERROR_UNKNOWN);
	}

	private void callFailed(int errorCode) {
		try{
			throw new Exception("Call "+(call!=null ? call.id : 0)+" failed with error code "+errorCode);
		}catch(Exception x){
			FileLog.e(x);
		}
		lastError = errorCode;
		if (call != null) {
			FileLog.d("Discarding failed call");
			TLRPC.TL_phone_discardCall req = new TLRPC.TL_phone_discardCall();
			req.peer = new TLRPC.TL_inputPhoneCall();
			req.peer.access_hash = call.access_hash;
			req.peer.id = call.id;
			req.duration = controller != null && controllerStarted ? (int) (controller.getCallDuration() / 1000) : 0;
			req.connection_id = controller != null && controllerStarted ? controller.getPreferredRelayID() : 0;
			req.reason = new TLRPC.TL_phoneCallDiscardReasonDisconnect();
			ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
				@Override
				public void run(TLObject response, TLRPC.TL_error error) {
					if (error != null) {
						FileLog.e("error on phone.discardCall: " + error);
					} else {
						FileLog.d("phone.discardCall " + response);
					}
				}
			});
		}
		dispatchStateChanged(STATE_FAILED);
		if(errorCode!=VoIPController.ERROR_LOCALIZED && soundPool!=null){
			playingSound=true;
			soundPool.play(spFailedID, 1, 1, 0, 0, 1);
			AndroidUtilities.runOnUIThread(afterSoundRunnable, 1000);
		}
		stopSelf();
	}

	private void callEnded() {
		FileLog.d("Call "+(call!=null ? call.id : 0)+" ended");
		dispatchStateChanged(STATE_ENDED);
		if (needPlayEndSound) {
			playingSound = true;
			soundPool.play(spEndId, 1, 1, 0, 0, 1);
			AndroidUtilities.runOnUIThread(afterSoundRunnable, 700);
		}
		if(timeoutRunnable!=null){
			AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
			timeoutRunnable=null;
		}
		stopSelf();
	}

	@Override
	public void onConnectionStateChanged(int newState) {
		if (newState == STATE_FAILED) {
			callFailed();
			return;
		}
		if (newState == STATE_ESTABLISHED) {
			if (spPlayID != 0) {
				soundPool.stop(spPlayID);
				spPlayID = 0;
			}
			if(!wasEstablished){
				wasEstablished=true;
				AndroidUtilities.runOnUIThread(new Runnable(){
					@Override
					public void run(){
						if(controller==null)
							return;
						int netType=StatsController.TYPE_WIFI;
						if(lastNetInfo!=null){
							if(lastNetInfo.getType()==ConnectivityManager.TYPE_MOBILE)
								netType=lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE;
						}
						StatsController.getInstance().incrementTotalCallsTime(netType, 5);
						AndroidUtilities.runOnUIThread(this, 5000);
					}
				}, 5000);
				if(isOutgoing)
					StatsController.getInstance().incrementSentItemsCount(getStatsNetworkType(), StatsController.TYPE_CALLS, 1);
				else
					StatsController.getInstance().incrementReceivedItemsCount(getStatsNetworkType(), StatsController.TYPE_CALLS, 1);
			}
		}
		if(newState==STATE_RECONNECTING){
			if(spPlayID!=0)
				soundPool.stop(spPlayID);
			spPlayID=soundPool.play(spConnectingId, 1, 1, 0, -1, 1);
		}
		dispatchStateChanged(newState);
	}

	public boolean isOutgoing(){
		return isOutgoing;
	}

	@SuppressLint("NewApi")
	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
			AudioManager am=(AudioManager) getSystemService(AUDIO_SERVICE);
			if (isHeadsetPlugged || am.isSpeakerphoneOn() || (isBluetoothHeadsetConnected() && am.isBluetoothScoOn())) {
				return;
			}
			boolean newIsNear = event.values[0] < Math.min(event.sensor.getMaximumRange(), 3);
			if (newIsNear != isProximityNear) {
				FileLog.d("proximity " + newIsNear);
				isProximityNear = newIsNear;
				try{
					if(isProximityNear){
						proximityWakelock.acquire();
					}else{
						proximityWakelock.release(1); // this is non-public API before L
					}
				}catch(Exception x){
					FileLog.e(x);
				}
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	private void updateNetworkType() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo info = cm.getActiveNetworkInfo();
		lastNetInfo = info;
		int type = VoIPController.NET_TYPE_UNKNOWN;
		if (info != null) {
			switch (info.getType()) {
				case ConnectivityManager.TYPE_MOBILE:
					switch (info.getSubtype()) {
						case TelephonyManager.NETWORK_TYPE_GPRS:
							type = VoIPController.NET_TYPE_GPRS;
							break;
						case TelephonyManager.NETWORK_TYPE_EDGE:
						case TelephonyManager.NETWORK_TYPE_1xRTT:
							type = VoIPController.NET_TYPE_EDGE;
							break;
						case TelephonyManager.NETWORK_TYPE_UMTS:
						case TelephonyManager.NETWORK_TYPE_EVDO_0:
							type = VoIPController.NET_TYPE_3G;
							break;
						case TelephonyManager.NETWORK_TYPE_HSDPA:
						case TelephonyManager.NETWORK_TYPE_HSPA:
						case TelephonyManager.NETWORK_TYPE_HSPAP:
						case TelephonyManager.NETWORK_TYPE_HSUPA:
						case TelephonyManager.NETWORK_TYPE_EVDO_A:
						case TelephonyManager.NETWORK_TYPE_EVDO_B:
							type = VoIPController.NET_TYPE_HSPA;
							break;
						case TelephonyManager.NETWORK_TYPE_LTE:
							type = VoIPController.NET_TYPE_LTE;
							break;
						default:
							type = VoIPController.NET_TYPE_OTHER_MOBILE;
							break;
					}
					break;
				case ConnectivityManager.TYPE_WIFI:
					type = VoIPController.NET_TYPE_WIFI;
					break;
				case ConnectivityManager.TYPE_ETHERNET:
					type = VoIPController.NET_TYPE_ETHERNET;
					break;
			}
		}
		if (controller != null) {
			controller.setNetworkType(type);
		}
	}

	private void updateBluetoothHeadsetState(boolean connected){
		if(connected==isBtHeadsetConnected)
			return;
		isBtHeadsetConnected=connected;
		AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
		if(connected)
			am.startBluetoothSco();
		else
			am.stopBluetoothSco();
		for (StateListener l : stateListeners)
			l.onAudioSettingsChanged();
	}

	public boolean isBluetoothHeadsetConnected(){
		return isBtHeadsetConnected;
	}

	private void configureDeviceForCall() {
		needPlayEndSound = true;
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		am.setMode(AudioManager.MODE_IN_COMMUNICATION);
		am.setSpeakerphoneOn(false);
		am.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);

		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		try{
			if(proximity!=null){
				proximityWakelock=((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "telegram-voip-prx");
				sm.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
			}
		}catch(Exception x){
			FileLog.e("Error initializing proximity sensor", x);
		}
	}

	private void dispatchStateChanged(int state) {
		FileLog.d("== Call "+(call!=null ? call.id : 0)+" state changed to "+state+" ==");
		currentState = state;
		for (int a = 0; a < stateListeners.size(); a++) {
			StateListener l = stateListeners.get(a);
			l.onStateChanged(state);
		}
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			haveAudioFocus = true;
		} else {
			haveAudioFocus = false;
		}
	}

	public void onUIForegroundStateChanged(boolean isForeground) {
		if (currentState == STATE_WAITING_INCOMING) {
			if (isForeground) {
				stopForeground(true);
			} else {
				if (!((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode()) {
					showIncomingNotification();
				} else {
					AndroidUtilities.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							Intent intent = new Intent(VoIPService.this, VoIPActivity.class);
							intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
							try {
								PendingIntent.getActivity(VoIPService.this, 0, intent, 0).send();
							} catch (PendingIntent.CanceledException e) {
								FileLog.e("error restarting activity", e);
							}
						}
					}, 500);
				}
			}
		}
	}

	/*package*/ void onMediaButtonEvent(KeyEvent ev) {
		if (ev.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) {
			if (ev.getAction() == KeyEvent.ACTION_UP) {
				if (currentState == STATE_WAITING_INCOMING) {
					acceptIncomingCall();
				} else {
					setMicMute(!isMicMute());
					for (StateListener l : stateListeners)
						l.onAudioSettingsChanged();
				}
			}
		}
	}

	public void debugCtl(int request, int param) {
		if (controller != null)
			controller.debugCtl(request, param);
	}

	public int getLastError() {
		return lastError;
	}

	private void updateStats() {
		controller.getStats(stats);
		long wifiSentDiff = stats.bytesSentWifi - prevStats.bytesSentWifi;
		long wifiRecvdDiff = stats.bytesRecvdWifi - prevStats.bytesRecvdWifi;
		long mobileSentDiff = stats.bytesSentMobile - prevStats.bytesSentMobile;
		long mobileRecvdDiff = stats.bytesRecvdMobile - prevStats.bytesRecvdMobile;
		VoIPController.Stats tmp = stats;
		stats = prevStats;
		prevStats = tmp;
		if (wifiSentDiff > 0)
			StatsController.getInstance().incrementSentBytesCount(StatsController.TYPE_WIFI, StatsController.TYPE_CALLS, wifiSentDiff);
		if (wifiRecvdDiff > 0)
			StatsController.getInstance().incrementReceivedBytesCount(StatsController.TYPE_WIFI, StatsController.TYPE_CALLS, wifiRecvdDiff);
		if (mobileSentDiff > 0)
			StatsController.getInstance().incrementSentBytesCount(lastNetInfo != null && lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE,
					StatsController.TYPE_CALLS, mobileSentDiff);
		if (mobileRecvdDiff > 0)
			StatsController.getInstance().incrementReceivedBytesCount(lastNetInfo != null && lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE,
					StatsController.TYPE_CALLS, mobileRecvdDiff);
	}

	private int getStatsNetworkType() {
		int netType = StatsController.TYPE_WIFI;
		if (lastNetInfo != null) {
			if (lastNetInfo.getType() == ConnectivityManager.TYPE_MOBILE)
				netType = lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE;
		}
		return netType;
	}

	public boolean hasEarpiece() {
		if(((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).getPhoneType()!=TelephonyManager.PHONE_TYPE_NONE)
			return true;
		if (mHasEarpiece != null) {
			return mHasEarpiece;
		}

		// not calculated yet, do it now
		try {
			AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
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
			FileLog.e("Error while checking earpiece! ", error);
			mHasEarpiece = Boolean.TRUE;
		}

		return mHasEarpiece;
	}

	public int getCallState(){
		return currentState;
	}

	public byte[] getGA(){
		return g_a;
	}

	@Override
	public void didReceivedNotification(int id, Object... args){
		if(id==NotificationCenter.appDidLogout){
			callEnded();
		}
	}

	public interface StateListener {
		void onStateChanged(int state);

		void onAudioSettingsChanged();
	}
}
