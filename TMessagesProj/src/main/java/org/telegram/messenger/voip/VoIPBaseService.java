package org.telegram.messenger.voip;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;
import android.telephony.TelephonyManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.StatsController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.VoIPActivity;
import org.telegram.ui.VoIPPermissionActivity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Created by grishka on 21.07.17.
 */

public abstract class VoIPBaseService extends Service implements SensorEventListener, AudioManager.OnAudioFocusChangeListener, VoIPController.ConnectionStateListener{

	public static final int STATE_WAIT_INIT = 1;
	public static final int STATE_WAIT_INIT_ACK = 2;
	public static final int STATE_ESTABLISHED = 3;
	public static final int STATE_FAILED = 4;
	public static final int STATE_RECONNECTING = 5;
	public static final int STATE_ENDED = 11;
	public static final String ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG";

	protected static final int ID_ONGOING_CALL_NOTIFICATION = 201;
	protected static final int ID_INCOMING_CALL_NOTIFICATION = 202;

	public static final int DISCARD_REASON_HANGUP = 1;
	public static final int DISCARD_REASON_DISCONNECT = 2;
	public static final int DISCARD_REASON_MISSED = 3;
	public static final int DISCARD_REASON_LINE_BUSY = 4;


	protected static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
	protected static VoIPBaseService sharedInstance;
	protected NetworkInfo lastNetInfo;
	protected int currentState = 0;
	protected Notification ongoingCallNotification;
	protected VoIPController controller;
	protected int lastError;
	protected PowerManager.WakeLock proximityWakelock;
	protected PowerManager.WakeLock cpuWakelock;
	protected boolean isProximityNear;
	protected boolean isHeadsetPlugged;
	protected ArrayList<StateListener> stateListeners = new ArrayList<>();
	protected MediaPlayer ringtonePlayer;
	protected Vibrator vibrator;
	protected SoundPool soundPool;
	protected int spRingbackID;
	protected int spFailedID;
	protected int spEndId;
	protected int spBusyId;
	protected int spConnectingId;
	protected int spPlayID;
	protected boolean needPlayEndSound;
	protected boolean haveAudioFocus;
	protected boolean micMute;
	protected boolean controllerStarted;
	protected BluetoothAdapter btAdapter;
	protected VoIPController.Stats stats = new VoIPController.Stats();
	protected VoIPController.Stats prevStats = new VoIPController.Stats();
	protected boolean isBtHeadsetConnected;
	protected Runnable afterSoundRunnable=new Runnable(){
		@Override
		public void run(){
			soundPool.release();
			if(isBtHeadsetConnected)
				((AudioManager) ApplicationLoader.applicationContext.getSystemService(AUDIO_SERVICE)).stopBluetoothSco();
			((AudioManager)ApplicationLoader.applicationContext.getSystemService(AUDIO_SERVICE)).setSpeakerphoneOn(false);
		}
	};
	protected long lastKnownDuration = 0;
	protected boolean playingSound;
	protected boolean isOutgoing;
	protected Runnable timeoutRunnable;
	protected BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_HEADSET_PLUG.equals(intent.getAction())) {
				isHeadsetPlugged = intent.getIntExtra("state", 0) == 1;
				if (isHeadsetPlugged && proximityWakelock != null && proximityWakelock.isHeld()) {
					proximityWakelock.release();
				}
				isProximityNear = false;
				updateOutputGainControlState();
			} else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
				updateNetworkType();
			} else if(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())){
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
	private Boolean mHasEarpiece = null;
	private boolean wasEstablished;
	protected int signalBarCount;

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

	protected int getStatsNetworkType() {
		int netType = StatsController.TYPE_WIFI;
		if (lastNetInfo != null) {
			if (lastNetInfo.getType() == ConnectivityManager.TYPE_MOBILE)
				netType = lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE;
		}
		return netType;
	}

	public void registerStateListener(StateListener l) {
		stateListeners.add(l);
		if (currentState != 0)
			l.onStateChanged(currentState);
		if(signalBarCount!=0)
			l.onSignalBarsCountChanged(signalBarCount);
	}

	public void unregisterStateListener(StateListener l) {
		stateListeners.remove(l);
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

	public static VoIPBaseService getSharedInstance(){
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

	protected void showNotification(String name, TLRPC.FileLocation photo, Class<? extends Activity> activity) {
		Intent intent = new Intent(this, activity);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		Notification.Builder builder = new Notification.Builder(this)
				.setContentTitle(LocaleController.getString("VoipOutgoingCall", R.string.VoipOutgoingCall))
				.setContentText(name)
				.setSmallIcon(R.drawable.notification)
				.setContentIntent(PendingIntent.getActivity(this, 0, intent, 0));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			Intent endIntent = new Intent(this, VoIPActionsReceiver.class);
			endIntent.setAction(getPackageName() + ".END_CALL");
			builder.addAction(R.drawable.ic_call_end_white_24dp, LocaleController.getString("VoipEndCall", R.string.VoipEndCall), PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT));
			builder.setPriority(Notification.PRIORITY_MAX);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			builder.setShowWhen(false);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setColor(0xff2ca5e0);
		}
		if (photo!= null) {
			BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photo, null, "50_50");
			if (img != null) {
				builder.setLargeIcon(img.getBitmap());
			} else {
				try {
					float scaleFactor = 160.0f / AndroidUtilities.dp(50);
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inSampleSize = scaleFactor < 1 ? 1 : (int) scaleFactor;
					Bitmap bitmap = BitmapFactory.decodeFile(FileLoader.getPathToAttach(photo, true).toString(), options);
					if (bitmap != null) {
						builder.setLargeIcon(bitmap);
					}
				} catch (Throwable e) {
					FileLog.e(e);
				}
			}
		}
		ongoingCallNotification = builder.getNotification();
		startForeground(ID_ONGOING_CALL_NOTIFICATION, ongoingCallNotification);
	}

	protected abstract long getCallID();
	public abstract void hangUp();
	public abstract void acceptIncomingCall();
	public abstract void declineIncomingCall(int reason, Runnable onDone);
	public abstract void declineIncomingCall();

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
			onControllerPreRelease();
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
		VoIPHelper.lastCallTime=System.currentTimeMillis();
	}

	protected void onControllerPreRelease(){

	}

	protected VoIPController createController(){
		return new VoIPController();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		FileLog.d("=============== VoIPService STARTING ===============");
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)!=null) {
			int outFramesPerBuffer = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
			VoIPController.setNativeBufferSize(outFramesPerBuffer);
		} else {
			VoIPController.setNativeBufferSize(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2);
		}
		updateServerConfig();
		try {
			controller = createController();
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

	protected abstract void updateServerConfig();
	protected abstract void showNotification();

	protected void dispatchStateChanged(int state) {
		FileLog.d("== Call "+getCallID()+" state changed to "+state+" ==");
		currentState = state;
		for (int a = 0; a < stateListeners.size(); a++) {
			StateListener l = stateListeners.get(a);
			l.onStateChanged(state);
		}
	}

	protected void updateStats() {
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

	protected void configureDeviceForCall() {
		needPlayEndSound = true;
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		am.setMode(AudioManager.MODE_IN_COMMUNICATION);
		am.setSpeakerphoneOn(false);
		am.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
		updateOutputGainControlState();

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

	public boolean isBluetoothHeadsetConnected(){
		return isBtHeadsetConnected;
	}

	public void onAudioFocusChange(int focusChange) {
		if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			haveAudioFocus = true;
		} else {
			haveAudioFocus = false;
		}
	}

	protected void updateBluetoothHeadsetState(boolean connected){
		if(connected==isBtHeadsetConnected)
			return;
		isBtHeadsetConnected=connected;
		AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
		if(connected){
			am.startBluetoothSco();
			am.setSpeakerphoneOn(false);
			am.setBluetoothScoOn(true);
		}else
			am.stopBluetoothSco();
		for (StateListener l : stateListeners)
			l.onAudioSettingsChanged();
	}

	public int getLastError() {
		return lastError;
	}

	public int getCallState(){
		return currentState;
	}

	protected void updateNetworkType() {
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

	protected void callFailed() {
		callFailed(controller != null && controllerStarted ? controller.getLastError() : VoIPController.ERROR_UNKNOWN);
	}

	protected void callFailed(int errorCode){
		try{
			throw new Exception("Call "+getCallID()+" failed with error code "+errorCode);
		}catch(Exception x){
			FileLog.e(x);
		}
		lastError = errorCode;
		dispatchStateChanged(STATE_FAILED);
		if(errorCode!=VoIPController.ERROR_LOCALIZED && soundPool!=null){
			playingSound=true;
			soundPool.play(spFailedID, 1, 1, 0, 0, 1);
			AndroidUtilities.runOnUIThread(afterSoundRunnable, 1000);
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
				if(!isProximityNear){
					Vibrator vibrator=(Vibrator) getSystemService(VIBRATOR_SERVICE);
					if(vibrator.hasVibrator())
						vibrator.vibrate(100);
				}
				AndroidUtilities.runOnUIThread(new Runnable(){
					@Override
					public void run(){
						if(controller==null)
							return;
						int netType=getStatsNetworkType();
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

	@Override
	public void onSignalBarCountChanged(int newCount){
		signalBarCount=newCount;
		for (int a = 0; a < stateListeners.size(); a++) {
			StateListener l = stateListeners.get(a);
			l.onSignalBarsCountChanged(newCount);
		}
	}

	protected void callEnded() {
		FileLog.d("Call "+getCallID()+" ended");
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

	public boolean isOutgoing(){
		return isOutgoing;
	}

	public void handleNotificationAction(Intent intent){
		if ((getPackageName() + ".END_CALL").equals(intent.getAction())) {
			stopForeground(true);
			hangUp();
		} else if ((getPackageName() + ".DECLINE_CALL").equals(intent.getAction())) {
			stopForeground(true);
			declineIncomingCall(DISCARD_REASON_LINE_BUSY, null);
		} else if ((getPackageName() + ".ANSWER_CALL").equals(intent.getAction())) {
			showNotification();
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
				try{
					PendingIntent.getActivity(VoIPBaseService.this, 0, new Intent(VoIPBaseService.this, VoIPPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0).send();
				}catch(Exception x){
					FileLog.e("Error starting permission activity", x);
				}
				return;
			}
			acceptIncomingCall();
			try{
				PendingIntent.getActivity(VoIPBaseService.this, 0, new Intent(VoIPBaseService.this, VoIPActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP), 0).send();
			}catch(Exception x){
				FileLog.e("Error starting incall activity", x);
			}
		}
	}

	public void updateOutputGainControlState(){
		AudioManager am=(AudioManager) getSystemService(AUDIO_SERVICE);
		controller.setAudioOutputGainControlEnabled(hasEarpiece() && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged);
	}

	public interface StateListener {
		void onStateChanged(int state);
		void onSignalBarsCountChanged(int count);
		void onAudioSettingsChanged();
	}
}
