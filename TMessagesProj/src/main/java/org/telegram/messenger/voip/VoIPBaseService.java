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
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
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
import android.view.ViewGroup;
import android.widget.RemoteViews;

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
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.VoIPPermissionActivity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by grishka on 21.07.17.
 */

public abstract class VoIPBaseService extends Service implements SensorEventListener, AudioManager.OnAudioFocusChangeListener, VoIPController.ConnectionStateListener, NotificationCenter.NotificationCenterDelegate{

	protected int currentAccount = -1;
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

	public static final int AUDIO_ROUTE_EARPIECE=0;
	public static final int AUDIO_ROUTE_SPEAKER=1;
	public static final int AUDIO_ROUTE_BLUETOOTH=2;

	protected static final boolean USE_CONNECTION_SERVICE=isDeviceCompatibleWithConnectionServiceAPI();


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
			if(USE_CONNECTION_SERVICE)
				return;
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
				if(BuildVars.LOGS_ENABLED)
					FileLog.e("bt headset state = "+intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0));
				updateBluetoothHeadsetState(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)==BluetoothProfile.STATE_CONNECTED);
			}else if(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(intent.getAction())){
				int state=intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
				if(BuildVars.LOGS_ENABLED)
					FileLog.e("Bluetooth SCO state updated: "+state);
				if(state==AudioManager.SCO_AUDIO_STATE_DISCONNECTED && isBtHeadsetConnected){
					if(!btAdapter.isEnabled() || btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)!=BluetoothProfile.STATE_CONNECTED){
						updateBluetoothHeadsetState(false);
						return;
					}
				}
				bluetoothScoActive=state==AudioManager.SCO_AUDIO_STATE_CONNECTED;
				if(bluetoothScoActive && needSwitchToBluetoothAfterScoActivates){
					needSwitchToBluetoothAfterScoActivates=false;
					AudioManager am=(AudioManager) getSystemService(AUDIO_SERVICE);
					am.setSpeakerphoneOn(false);
					am.setBluetoothScoOn(true);
				}
				for(StateListener l : stateListeners)
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
	protected boolean audioConfigured;
	protected int audioRouteToSet=AUDIO_ROUTE_BLUETOOTH;
	protected boolean speakerphoneStateToSet;
	protected CallConnection systemCallConnection;
	protected int callDiscardReason;
	protected boolean bluetoothScoActive=false;
	protected boolean needSwitchToBluetoothAfterScoActivates=false;
	protected boolean didDeleteConnectionServiceContact=false;
	protected Runnable connectingSoundRunnable;

	public boolean hasEarpiece() {
		if(USE_CONNECTION_SERVICE){
			if(systemCallConnection!=null && systemCallConnection.getCallAudioState()!=null){
				int routeMask=systemCallConnection.getCallAudioState().getSupportedRouteMask();
				return (routeMask & (CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_WIRED_HEADSET))!=0;
			}
		}
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
		micMute=mute;
		if(controller!=null)
			controller.setMicMute(mute);
	}

	public boolean isMicMute() {
		return micMute;
	}

	public void toggleSpeakerphoneOrShowRouteSheet(Activity activity){
		if(isBluetoothHeadsetConnected() && hasEarpiece()){
			BottomSheet.Builder bldr=new BottomSheet.Builder(activity)
					.setItems(new CharSequence[]{LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth),
									LocaleController.getString("VoipAudioRoutingEarpiece", R.string.VoipAudioRoutingEarpiece),
									LocaleController.getString("VoipAudioRoutingSpeaker", R.string.VoipAudioRoutingSpeaker)},
							new int[]{R.drawable.ic_bluetooth_white_24dp,
									R.drawable.ic_phone_in_talk_white_24dp,
									R.drawable.ic_volume_up_white_24dp}, new DialogInterface.OnClickListener(){
								@Override
								public void onClick(DialogInterface dialog, int which){
									AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
									if(getSharedInstance()==null)
										return;
									if(USE_CONNECTION_SERVICE && systemCallConnection!=null){
										switch(which){
											case 0:
												systemCallConnection.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
												break;
											case 1:
												systemCallConnection.setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
												break;
											case 2:
												systemCallConnection.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
												break;
										}
									}else if(audioConfigured && !USE_CONNECTION_SERVICE){
										switch(which){
											case 0:
												if(!bluetoothScoActive){
													needSwitchToBluetoothAfterScoActivates=true;
													try {
														am.startBluetoothSco();
													} catch (Throwable ignore) {

													}
												}else{
													am.setBluetoothScoOn(true);
													am.setSpeakerphoneOn(false);
												}
												break;
											case 1:
												if(bluetoothScoActive)
													am.stopBluetoothSco();
												am.setSpeakerphoneOn(false);
												am.setBluetoothScoOn(false);
												break;
											case 2:
												if(bluetoothScoActive)
													am.stopBluetoothSco();
												am.setBluetoothScoOn(false);
												am.setSpeakerphoneOn(true);
												break;
										}
										updateOutputGainControlState();
									}else{
										switch(which){
											case 0:
												audioRouteToSet=AUDIO_ROUTE_BLUETOOTH;
												break;
											case 1:
												audioRouteToSet=AUDIO_ROUTE_EARPIECE;
												break;
											case 2:
												audioRouteToSet=AUDIO_ROUTE_SPEAKER;
												break;
										}
									}
									for(StateListener l:stateListeners)
										l.onAudioSettingsChanged();
								}
							});
			BottomSheet sheet=bldr.create();
			sheet.setBackgroundColor(0xff2b2b2b);
			sheet.show();
			ViewGroup container=sheet.getSheetContainer();
			for(int i=0;i<container.getChildCount();i++){
				BottomSheet.BottomSheetCell cell=(BottomSheet.BottomSheetCell) container.getChildAt(i);
				cell.setTextColor(0xFFFFFFFF);
			}
			return;
		}
		if(USE_CONNECTION_SERVICE && systemCallConnection!=null && systemCallConnection.getCallAudioState()!=null){
			if(hasEarpiece())
				systemCallConnection.setAudioRoute(systemCallConnection.getCallAudioState().getRoute()==CallAudioState.ROUTE_SPEAKER ? CallAudioState.ROUTE_WIRED_OR_EARPIECE : CallAudioState.ROUTE_SPEAKER);
			else
				systemCallConnection.setAudioRoute(systemCallConnection.getCallAudioState().getRoute()==CallAudioState.ROUTE_BLUETOOTH ? CallAudioState.ROUTE_WIRED_OR_EARPIECE : CallAudioState.ROUTE_BLUETOOTH);
		}else if(audioConfigured && !USE_CONNECTION_SERVICE){
			AudioManager am=(AudioManager) getSystemService(AUDIO_SERVICE);
			if(hasEarpiece()){
				am.setSpeakerphoneOn(!am.isSpeakerphoneOn());
			}else{
				am.setBluetoothScoOn(!am.isBluetoothScoOn());
			}
			updateOutputGainControlState();
		}else{
			speakerphoneStateToSet=!speakerphoneStateToSet;
		}
		for(StateListener l:stateListeners)
			l.onAudioSettingsChanged();
	}

	public boolean isSpeakerphoneOn(){
		if(USE_CONNECTION_SERVICE && systemCallConnection!=null && systemCallConnection.getCallAudioState()!=null){
			int route=systemCallConnection.getCallAudioState().getRoute();
			return hasEarpiece() ? route==CallAudioState.ROUTE_SPEAKER : route==CallAudioState.ROUTE_BLUETOOTH;
		}else if(audioConfigured && !USE_CONNECTION_SERVICE){
			AudioManager am=(AudioManager) getSystemService(AUDIO_SERVICE);
			return hasEarpiece() ? am.isSpeakerphoneOn() : am.isBluetoothScoOn();
		}
		return speakerphoneStateToSet;
	}

	public int getCurrentAudioRoute(){
		if(USE_CONNECTION_SERVICE){
			if(systemCallConnection!=null && systemCallConnection.getCallAudioState()!=null){
				switch(systemCallConnection.getCallAudioState().getRoute()){
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
		if(audioConfigured){
			AudioManager am=(AudioManager) getSystemService(AUDIO_SERVICE);
			if(am.isBluetoothScoOn())
				return AUDIO_ROUTE_BLUETOOTH;
			else if(am.isSpeakerphoneOn())
				return AUDIO_ROUTE_SPEAKER;
			else
				return AUDIO_ROUTE_EARPIECE;
		}
		return audioRouteToSet;
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
		if (Build.VERSION.SDK_INT >= 26) {
			NotificationsController.checkOtherNotificationsChannel();
			builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
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

	protected void startRingtoneAndVibration(int chatID){
		SharedPreferences prefs = MessagesController.getNotificationsSettings(currentAccount);
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		boolean needRing=am.getRingerMode()!=AudioManager.RINGER_MODE_SILENT;
		/*if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
			try{
				int mode=Settings.Global.getInt(getContentResolver(), "zen_mode");
				if(needRing)
					needRing=mode==0;
			}catch(Exception ignore){}
		}*/
		if(needRing){
			if(!USE_CONNECTION_SERVICE){
				am.requestAudioFocus(this, AudioManager.STREAM_RING, AudioManager.AUDIOFOCUS_GAIN);
			}
			ringtonePlayer=new MediaPlayer();
			ringtonePlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){
				@Override
				public void onPrepared(MediaPlayer mediaPlayer){
					ringtonePlayer.start();
				}
			});
			ringtonePlayer.setLooping(true);
			ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
			try{
				String notificationUri;
				if(prefs.getBoolean("custom_"+chatID, false))
					notificationUri=prefs.getString("ringtone_path_"+chatID, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString());
				else
					notificationUri=prefs.getString("CallsRingtonePath", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString());
				ringtonePlayer.setDataSource(this, Uri.parse(notificationUri));
				ringtonePlayer.prepareAsync();
			}catch(Exception e){
				FileLog.e(e);
				if(ringtonePlayer!=null){
					ringtonePlayer.release();
					ringtonePlayer=null;
				}
			}
			int vibrate;
			if(prefs.getBoolean("custom_"+chatID, false))
				vibrate=prefs.getInt("calls_vibrate_"+chatID, 0);
			else
				vibrate=prefs.getInt("vibrate_calls", 0);
			if((vibrate!=2 && vibrate!=4 && (am.getRingerMode()==AudioManager.RINGER_MODE_VIBRATE || am.getRingerMode()==AudioManager.RINGER_MODE_NORMAL)) ||
					(vibrate==4 && am.getRingerMode()==AudioManager.RINGER_MODE_VIBRATE)){
				vibrator=(Vibrator) getSystemService(VIBRATOR_SERVICE);
				long duration=700;
				if(vibrate==1)
					duration/=2;
				else if(vibrate==3)
					duration*=2;
				vibrator.vibrate(new long[]{0, duration, 500}, 0);
			}
		}
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

	@Override
	public void onDestroy() {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("=============== VoIPService STOPPING ===============");
		}
		stopForeground(true);
		stopRinging();
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout);
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
				NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didEndedCall);
			}
		});
		if (controller != null && controllerStarted) {
			lastKnownDuration = controller.getCallDuration();
			updateStats();
			StatsController.getInstance(currentAccount).incrementTotalCallsTime(getStatsNetworkType(), (int) (lastKnownDuration / 1000) % 5);
			onControllerPreRelease();
			controller.release();
			controller = null;
		}
		cpuWakelock.release();
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if(!USE_CONNECTION_SERVICE){
			if(isBtHeadsetConnected && !playingSound){
				am.stopBluetoothSco();
				am.setSpeakerphoneOn(false);
			}
			try{
				am.setMode(AudioManager.MODE_NORMAL);
			}catch(SecurityException x){
				if(BuildVars.LOGS_ENABLED){
					FileLog.e("Error setting audio more to normal", x);
				}
			}
			am.abandonAudioFocus(this);
		}
		am.unregisterMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));
		if (haveAudioFocus)
			am.abandonAudioFocus(this);

		if (!playingSound)
			soundPool.release();

		if(USE_CONNECTION_SERVICE){
			if(!didDeleteConnectionServiceContact)
				ContactsController.getInstance(currentAccount).deleteConnectionServiceContact();
			if(systemCallConnection!=null && !playingSound){
				systemCallConnection.destroy();
			}
		}

		ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
		VoIPHelper.lastCallTime=System.currentTimeMillis();
	}

	protected void onControllerPreRelease(){

	}

	protected VoIPController createController(){
		return new VoIPController();
	}

	protected void initializeAccountRelatedThings(){
		updateServerConfig();
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appDidLogout);
		ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
		controller = createController();
		controller.setConnectionStateListener(this);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("=============== VoIPService STARTING ===============");
		}
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)!=null) {
			int outFramesPerBuffer = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
			VoIPController.setNativeBufferSize(outFramesPerBuffer);
		} else {
			VoIPController.setNativeBufferSize(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2);
		}
		try {
			cpuWakelock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "telegram-voip");
			cpuWakelock.acquire();

			btAdapter=am.isBluetoothScoAvailableOffCall() ? BluetoothAdapter.getDefaultAdapter() : null;

			IntentFilter filter = new IntentFilter();
			filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
			if(!USE_CONNECTION_SERVICE){
				filter.addAction(ACTION_HEADSET_PLUG);
				if(btAdapter!=null){
					filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
					filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
				}
				filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
			}
			registerReceiver(receiver, filter);

			soundPool = new SoundPool(1, AudioManager.STREAM_VOICE_CALL, 0);
			spConnectingId = soundPool.load(this, R.raw.voip_connecting, 1);
			spRingbackID = soundPool.load(this, R.raw.voip_ringback, 1);
			spFailedID = soundPool.load(this, R.raw.voip_failed, 1);
			spEndId = soundPool.load(this, R.raw.voip_end, 1);
			spBusyId = soundPool.load(this, R.raw.voip_busy, 1);

			am.registerMediaButtonEventReceiver(new ComponentName(this, VoIPMediaButtonReceiver.class));

			if(!USE_CONNECTION_SERVICE && btAdapter!=null && btAdapter.isEnabled()){
				int headsetState=btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
				updateBluetoothHeadsetState(headsetState==BluetoothProfile.STATE_CONNECTED);
				//if(headsetState==BluetoothProfile.STATE_CONNECTED)
				//	am.setBluetoothScoOn(true);
				for (StateListener l : stateListeners)
					l.onAudioSettingsChanged();
			}

		} catch (Exception x) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("error initializing voip controller", x);
			}
			callFailed();
		}
	}

	protected abstract void updateServerConfig();
	protected abstract void showNotification();

	protected void dispatchStateChanged(int state) {
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("== Call " + getCallID() + " state changed to " + state + " ==");
		}
		currentState = state;
		if(USE_CONNECTION_SERVICE && state==STATE_ESTABLISHED /*&& !wasEstablished*/ && systemCallConnection!=null){
			systemCallConnection.setActive();
		}
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
			StatsController.getInstance(currentAccount).incrementSentBytesCount(StatsController.TYPE_WIFI, StatsController.TYPE_CALLS, wifiSentDiff);
		if (wifiRecvdDiff > 0)
			StatsController.getInstance(currentAccount).incrementReceivedBytesCount(StatsController.TYPE_WIFI, StatsController.TYPE_CALLS, wifiRecvdDiff);
		if (mobileSentDiff > 0)
			StatsController.getInstance(currentAccount).incrementSentBytesCount(lastNetInfo != null && lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE,
					StatsController.TYPE_CALLS, mobileSentDiff);
		if (mobileRecvdDiff > 0)
			StatsController.getInstance(currentAccount).incrementReceivedBytesCount(lastNetInfo != null && lastNetInfo.isRoaming() ? StatsController.TYPE_ROAMING : StatsController.TYPE_MOBILE,
					StatsController.TYPE_CALLS, mobileRecvdDiff);
	}

	protected void configureDeviceForCall() {
		needPlayEndSound = true;
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		if(!USE_CONNECTION_SERVICE){
			am.setMode(AudioManager.MODE_IN_COMMUNICATION);
			am.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
			if(isBluetoothHeadsetConnected() && hasEarpiece()){
				switch(audioRouteToSet){
					case AUDIO_ROUTE_BLUETOOTH:
						if(!bluetoothScoActive){
							needSwitchToBluetoothAfterScoActivates=true;
							try {
								am.startBluetoothSco();
							} catch (Throwable ignore) {

							}
						}else{
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
			}else if(isBluetoothHeadsetConnected()){
				am.setBluetoothScoOn(speakerphoneStateToSet);
			}else{
				am.setSpeakerphoneOn(speakerphoneStateToSet);
			}
		}/*else{
			if(isBluetoothHeadsetConnected() && hasEarpiece()){
				switch(audioRouteToSet){
					case AUDIO_ROUTE_BLUETOOTH:
						systemCallConnection.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
						break;
					case AUDIO_ROUTE_EARPIECE:
						systemCallConnection.setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
						break;
					case AUDIO_ROUTE_SPEAKER:
						systemCallConnection.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
						break;
				}
			}else{
				if(hasEarpiece())
					systemCallConnection.setAudioRoute(!speakerphoneStateToSet ? CallAudioState.ROUTE_WIRED_OR_EARPIECE : CallAudioState.ROUTE_SPEAKER);
				else
					systemCallConnection.setAudioRoute(!speakerphoneStateToSet ? CallAudioState.ROUTE_WIRED_OR_EARPIECE : CallAudioState.ROUTE_BLUETOOTH);
			}
		}*/
		updateOutputGainControlState();
		audioConfigured=true;

		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		try{
			if(proximity!=null){
				proximityWakelock=((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "telegram-voip-prx");
				sm.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
			}
		}catch(Exception x){
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("Error initializing proximity sensor", x);
			}
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
				if (BuildVars.LOGS_ENABLED) {
					FileLog.d("proximity " + newIsNear);
				}
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
		if(USE_CONNECTION_SERVICE && systemCallConnection!=null && systemCallConnection.getCallAudioState()!=null)
			return (systemCallConnection.getCallAudioState().getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH)!=0;
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
		if(BuildVars.LOGS_ENABLED)
			FileLog.d("updateBluetoothHeadsetState: "+connected);
		isBtHeadsetConnected=connected;
		final AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
		if(connected && !isRinging() && currentState!=0){
			if(bluetoothScoActive){
				if(BuildVars.LOGS_ENABLED)
					FileLog.d("SCO already active, setting audio routing");
				am.setSpeakerphoneOn(false);
				am.setBluetoothScoOn(true);
			}else{
				if(BuildVars.LOGS_ENABLED)
					FileLog.d("startBluetoothSco");
				needSwitchToBluetoothAfterScoActivates=true;
				// some devices ignore startBluetoothSco when called immediately after the headset is connected, so delay it
				AndroidUtilities.runOnUIThread(new Runnable(){
					@Override
					public void run(){
						try {
							am.startBluetoothSco();
						} catch (Throwable ignore) {

						}
					}
				}, 500);
			}
		}else{
			bluetoothScoActive=false;
		}
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

	protected Bitmap getRoundAvatarBitmap(TLObject userOrChat){
		Bitmap bitmap=null;
		if(userOrChat instanceof TLRPC.User){
			TLRPC.User user=(TLRPC.User) userOrChat;
			if(user.photo!=null && user.photo.photo_small!=null){
				BitmapDrawable img=ImageLoader.getInstance().getImageFromMemory(user.photo.photo_small, null, "50_50");
				if(img!=null){
					bitmap=img.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
				}else{
					try{
						BitmapFactory.Options opts=new BitmapFactory.Options();
						opts.inMutable=true;
						bitmap=BitmapFactory.decodeFile(FileLoader.getPathToAttach(user.photo.photo_small, true).toString(), opts);
					}catch(Throwable e){
						FileLog.e(e);
					}
				}
			}
		}else{
			TLRPC.Chat chat=(TLRPC.Chat) userOrChat;
			if(chat.photo!=null && chat.photo.photo_small!=null){
				BitmapDrawable img=ImageLoader.getInstance().getImageFromMemory(chat.photo.photo_small, null, "50_50");
				if(img!=null){
					bitmap=img.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
				}else{
					try{
						BitmapFactory.Options opts=new BitmapFactory.Options();
						opts.inMutable=true;
						bitmap=BitmapFactory.decodeFile(FileLoader.getPathToAttach(chat.photo.photo_small, true).toString(), opts);
					}catch(Throwable e){
						FileLog.e(e);
					}
				}
			}
		}
		if(bitmap==null){
			Theme.createDialogsResources(this);
			AvatarDrawable placeholder;
			if(userOrChat instanceof TLRPC.User)
				placeholder=new AvatarDrawable((TLRPC.User)userOrChat);
			else
				placeholder=new AvatarDrawable((TLRPC.Chat)userOrChat);
			bitmap=Bitmap.createBitmap(AndroidUtilities.dp(42), AndroidUtilities.dp(42), Bitmap.Config.ARGB_8888);
			placeholder.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
			placeholder.draw(new Canvas(bitmap));
		}

		Canvas canvas=new Canvas(bitmap);
		Path circlePath=new Path();
		circlePath.addCircle(bitmap.getWidth()/2, bitmap.getHeight()/2, bitmap.getWidth()/2, Path.Direction.CW);
		circlePath.toggleInverseFillType();
		Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		canvas.drawPath(circlePath, paint);
		return bitmap;
	}

	protected void showIncomingNotification(String name, CharSequence subText, TLObject userOrChat, List<TLRPC.User> groupUsers, int additionalMemberCount, Class<? extends Activity> activityOnClick) {
		Intent intent = new Intent(this, activityOnClick);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		Notification.Builder builder = new Notification.Builder(this)
				.setContentTitle(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding))
				.setContentText(name)
				.setSmallIcon(R.drawable.notification)
				.setSubText(subText)
				.setContentIntent(PendingIntent.getActivity(this, 0, intent, 0));
		Uri soundProviderUri=Uri.parse("content://"+BuildConfig.APPLICATION_ID+".call_sound_provider/start_ringing");
		if (Build.VERSION.SDK_INT >= 26) {
			SharedPreferences nprefs=MessagesController.getGlobalNotificationsSettings();
			int chanIndex=nprefs.getInt("calls_notification_channel", 0);
			NotificationManager nm=(NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			NotificationChannel oldChannel=nm.getNotificationChannel("incoming_calls"+chanIndex);
			if(oldChannel!=null)
				nm.deleteNotificationChannel(oldChannel.getId());
			NotificationChannel existingChannel=nm.getNotificationChannel("incoming_calls2"+chanIndex);
			boolean needCreate=true;
			if(existingChannel!=null){
				if(existingChannel.getImportance()<NotificationManager.IMPORTANCE_HIGH || !soundProviderUri.equals(existingChannel.getSound()) || existingChannel.getVibrationPattern()!=null || existingChannel.shouldVibrate()){
					if(BuildVars.LOGS_ENABLED)
						FileLog.d("User messed up the notification channel; deleting it and creating a proper one");
					nm.deleteNotificationChannel("incoming_calls2"+chanIndex);
					chanIndex++;
					nprefs.edit().putInt("calls_notification_channel", chanIndex).commit();
				}else{
					needCreate=false;
				}
			}
			if(needCreate){
				AudioAttributes attrs=new AudioAttributes.Builder()
						.setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
						.build();
				NotificationChannel chan=new NotificationChannel("incoming_calls2"+chanIndex, LocaleController.getString("IncomingCalls", R.string.IncomingCalls), NotificationManager.IMPORTANCE_HIGH);
				chan.setSound(soundProviderUri, attrs);
				chan.enableVibration(false);
				chan.enableLights(false);
				nm.createNotificationChannel(chan);
			}
			builder.setChannelId("incoming_calls2"+chanIndex);
		}else if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
			builder.setSound(soundProviderUri, AudioManager.STREAM_RING);
		}
		Intent endIntent = new Intent(this, VoIPActionsReceiver.class);
		endIntent.setAction(getPackageName() + ".DECLINE_CALL");
		endIntent.putExtra("call_id", getCallID());
		CharSequence endTitle=LocaleController.getString("VoipDeclineCall", R.string.VoipDeclineCall);
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
			endTitle=new SpannableString(endTitle);
			((SpannableString)endTitle).setSpan(new ForegroundColorSpan(0xFFF44336), 0, endTitle.length(), 0);
		}
		PendingIntent endPendingIntent=PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		builder.addAction(R.drawable.ic_call_end_white_24dp, endTitle, endPendingIntent);
		Intent answerIntent = new Intent(this, VoIPActionsReceiver.class);
		answerIntent.setAction(getPackageName() + ".ANSWER_CALL");
		answerIntent.putExtra("call_id", getCallID());
		CharSequence answerTitle=LocaleController.getString("VoipAnswerCall", R.string.VoipAnswerCall);
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
			answerTitle=new SpannableString(answerTitle);
			((SpannableString)answerTitle).setSpan(new ForegroundColorSpan(0xFF00AA00), 0, answerTitle.length(), 0);
		}
		PendingIntent answerPendingIntent=PendingIntent.getBroadcast(this, 0, answerIntent, PendingIntent.FLAG_CANCEL_CURRENT);
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
			if(userOrChat instanceof TLRPC.User){
				TLRPC.User user=(TLRPC.User) userOrChat;
				if(!TextUtils.isEmpty(user.phone)){
					builder.addPerson("tel:"+user.phone);
				}
			}
		}
		/*Bitmap photoBitmap=null;
		if (photo != null) {
			BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photo, null, "50_50");
			if (img != null) {
				builder.setLargeIcon(photoBitmap=img.getBitmap());
			} else {
				try {
					float scaleFactor = 160.0f / AndroidUtilities.dp(50);
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inSampleSize = scaleFactor < 1 ? 1 : (int) scaleFactor;
					Bitmap bitmap = BitmapFactory.decodeFile(FileLoader.getPathToAttach(photo, true).toString(), options);
					if (bitmap != null) {
						builder.setLargeIcon(photoBitmap=bitmap);
					}
				} catch (Throwable e) {
					FileLog.e(e);
				}
			}
		}*/
		Notification incomingNotification = builder.getNotification();
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
			RemoteViews customView=new RemoteViews(getPackageName(), LocaleController.isRTL ? R.layout.call_notification_rtl : R.layout.call_notification);
			customView.setTextViewText(R.id.name, name);
			boolean subtitleVisible=true;
			if(TextUtils.isEmpty(subText)){
				customView.setViewVisibility(R.id.subtitle, View.GONE);
				subtitleVisible=false;
				if(UserConfig.getActivatedAccountsCount()>1){
					TLRPC.User self=UserConfig.getInstance(currentAccount).getCurrentUser();
					customView.setTextViewText(R.id.title, LocaleController.formatString("VoipInCallBrandingWithName", R.string.VoipInCallBrandingWithName, ContactsController.formatName(self.first_name, self.last_name)));
				}else{
					customView.setTextViewText(R.id.title, LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding));
				}
			}else{
				if(UserConfig.getActivatedAccountsCount()>1){
					TLRPC.User self=UserConfig.getInstance(currentAccount).getCurrentUser();
					customView.setTextViewText(R.id.subtitle, LocaleController.formatString("VoipAnsweringAsAccount", R.string.VoipAnsweringAsAccount, ContactsController.formatName(self.first_name, self.last_name)));
				}else{
					customView.setViewVisibility(R.id.subtitle, View.GONE);
					subtitleVisible=false;
				}
				customView.setTextViewText(R.id.title, subText);
			}
			Bitmap avatar=getRoundAvatarBitmap(userOrChat);
			customView.setTextViewText(R.id.answer_text, LocaleController.getString("VoipAnswerCall", R.string.VoipAnswerCall));
			customView.setTextViewText(R.id.decline_text, LocaleController.getString("VoipDeclineCall", R.string.VoipDeclineCall));
			customView.setImageViewBitmap(R.id.photo, avatar);
			customView.setOnClickPendingIntent(R.id.answer_btn, answerPendingIntent);
			customView.setOnClickPendingIntent(R.id.decline_btn, endPendingIntent);
			builder.setLargeIcon(avatar);

			/*if(groupUsers==null || groupUsers.size()==0){
				customView.setViewVisibility(R.id.group_photos, View.GONE);
			}else{
				int[] ids={R.id.group_photo1, R.id.group_photo2, R.id.group_photo3};
				for(int i=0;i<3;i++){
					if(i<groupUsers.size()){
						customView.setImageViewBitmap(ids[i], getRoundAvatarBitmap(groupUsers.get(i)));
					}else{
						customView.setViewVisibility(ids[i], View.GONE);
					}
				}
				if(additionalMemberCount>0){
					customView.setTextViewText(R.id.group_more, LocaleController.formatString("VoipGroupMoreMembers", R.string.VoipGroupMoreMembers, additionalMemberCount));
				}else{
					customView.setViewVisibility(R.id.group_more, View.GONE);
				}
				int viewCount=Math.min(groupUsers.size(), 3)+(additionalMemberCount>0 ? 1 : 0);
				int padding=AndroidUtilities.dp(22*viewCount+4*(viewCount-1));
				customView.setViewPadding(R.id.name, LocaleController.isRTL ? padding : 0, 0, LocaleController.isRTL ? 0 : padding, 0);
				if(subtitleVisible){
					customView.setViewPadding(R.id.title, LocaleController.isRTL ? padding : 0, 0, LocaleController.isRTL ? 0 : padding, 0);
				}
			}*/

			incomingNotification.headsUpContentView=incomingNotification.bigContentView=customView;
		}
		startForeground(ID_INCOMING_CALL_NOTIFICATION, incomingNotification);
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
		if(USE_CONNECTION_SERVICE && systemCallConnection!=null){
			systemCallConnection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
			systemCallConnection.destroy();
			systemCallConnection=null;
		}
		stopSelf();
	}

	/*package*/ void callFailedFromConnectionService(){
		if(isOutgoing)
			callFailed(VoIPController.ERROR_CONNECTION_SERVICE);
		else
			hangUp();
	}

	@Override
	public void onConnectionStateChanged(int newState) {
		if (newState == STATE_FAILED) {
			callFailed();
			return;
		}
		if (newState == STATE_ESTABLISHED) {
			if(connectingSoundRunnable!=null){
				AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable);
				connectingSoundRunnable=null;
			}
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
						StatsController.getInstance(currentAccount).incrementTotalCallsTime(netType, 5);
						AndroidUtilities.runOnUIThread(this, 5000);
					}
				}, 5000);
				if(isOutgoing)
					StatsController.getInstance(currentAccount).incrementSentItemsCount(getStatsNetworkType(), StatsController.TYPE_CALLS, 1);
				else
					StatsController.getInstance(currentAccount).incrementReceivedItemsCount(getStatsNetworkType(), StatsController.TYPE_CALLS, 1);
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
		if (BuildVars.LOGS_ENABLED) {
			FileLog.d("Call " + getCallID() + " ended");
		}
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
		endConnectionServiceCall(needPlayEndSound ? 700 : 0);
		stopSelf();
	}

	protected void endConnectionServiceCall(long delay){
		if(USE_CONNECTION_SERVICE){
			Runnable r=new Runnable(){
				@Override
				public void run(){
					if(systemCallConnection!=null){
						switch(callDiscardReason){
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
						systemCallConnection=null;
					}
				}
			};
			if(delay>0)
				AndroidUtilities.runOnUIThread(r, delay);
			else
				r.run();
		}
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
			acceptIncomingCallFromNotification();
		}
	}

	private void acceptIncomingCallFromNotification(){
		showNotification();
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
			try{
				PendingIntent.getActivity(VoIPBaseService.this, 0, new Intent(VoIPBaseService.this, VoIPPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0).send();
			}catch(Exception x){
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e("Error starting permission activity", x);
				}
			}
			return;
		}
		acceptIncomingCall();
		try{
			PendingIntent.getActivity(VoIPBaseService.this, 0, new Intent(VoIPBaseService.this, getUIActivityClass()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP), 0).send();
		}catch(Exception x){
			if (BuildVars.LOGS_ENABLED) {
				FileLog.e("Error starting incall activity", x);
			}
		}
	}

	public void updateOutputGainControlState(){
		if(controller==null || !controllerStarted)
			return;
		if(!USE_CONNECTION_SERVICE){
			AudioManager am=(AudioManager) getSystemService(AUDIO_SERVICE);
			controller.setAudioOutputGainControlEnabled(hasEarpiece() && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged);
			controller.setEchoCancellationStrength(isHeadsetPlugged || (hasEarpiece() && !am.isSpeakerphoneOn() && !am.isBluetoothScoOn() && !isHeadsetPlugged) ? 0 : 1);
		}else{
			boolean isEarpiece=systemCallConnection.getCallAudioState().getRoute()==CallAudioState.ROUTE_EARPIECE;
			controller.setAudioOutputGainControlEnabled(isEarpiece);
			controller.setEchoCancellationStrength(isEarpiece ? 0 : 1);
		}
	}

	public int getAccount(){
		return currentAccount;
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args){
		if(id==NotificationCenter.appDidLogout){
			callEnded();
		}
	}

	public static boolean isAnyKindOfCallActive(){
		//if(VoIPGroupService.getSharedInstance()!=null){
		//	return VoIPGroupService.getSharedInstance().getCallState()!=VoIPGroupService.STATE_INVITED;
		/*}else*/ if(VoIPService.getSharedInstance()!=null){
			return VoIPService.getSharedInstance().getCallState()!=VoIPService.STATE_WAITING_INCOMING;
		}
		return false;
	}

	protected boolean isFinished(){
		return currentState==STATE_ENDED || currentState==STATE_FAILED;
	}

	protected boolean isRinging(){
		return false;
	}

	@TargetApi(Build.VERSION_CODES.O)
	protected PhoneAccountHandle addAccountToTelecomManager(){
		TelecomManager tm=(TelecomManager) getSystemService(TELECOM_SERVICE);
		TLRPC.User self=UserConfig.getInstance(currentAccount).getCurrentUser();
		PhoneAccountHandle handle=new PhoneAccountHandle(new ComponentName(this, TelegramConnectionService.class), ""+self.id);
		PhoneAccount account=new PhoneAccount.Builder(handle, ContactsController.formatName(self.first_name, self.last_name))
				.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
				.setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_dr))
				.setHighlightColor(0xff2ca5e0)
				.addSupportedUriScheme("sip")
				.build();
		tm.registerPhoneAccount(account);
		return handle;
	}

	private static boolean isDeviceCompatibleWithConnectionServiceAPI(){
		if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)
			return false;
		// some non-Google devices don't implement the ConnectionService API correctly so, sadly,
		// we'll have to whitelist only a handful of known-compatible devices for now
		return "angler".equals(Build.PRODUCT) 			// Nexus 6P
				|| "bullhead".equals(Build.PRODUCT)		// Nexus 5X
				|| "sailfish".equals(Build.PRODUCT)		// Pixel
				|| "marlin".equals(Build.PRODUCT)		// Pixel XL
				|| "walleye".equals(Build.PRODUCT)		// Pixel 2
				|| "taimen".equals(Build.PRODUCT)		// Pixel 2 XL
				|| "blueline".equals(Build.PRODUCT)		// Pixel 3
				|| "crosshatch".equals(Build.PRODUCT)	// Pixel 3 XL
				|| MessagesController.getGlobalMainSettings().getBoolean("dbg_force_connection_service", false)
				;
	}

	public interface StateListener {
		void onStateChanged(int state);
		void onSignalBarsCountChanged(int count);
		void onAudioSettingsChanged();
	}

	@TargetApi(Build.VERSION_CODES.O)
	public class CallConnection extends Connection{
		public CallConnection(){
			setConnectionProperties(PROPERTY_SELF_MANAGED);
			setAudioModeIsVoip(true);
		}

		@Override
		public void onCallAudioStateChanged(CallAudioState state){
			if(BuildVars.LOGS_ENABLED)
				FileLog.d("ConnectionService call audio state changed: "+state);
			for(StateListener l:stateListeners)
				l.onAudioSettingsChanged();
		}

		@Override
		public void onDisconnect(){
			if(BuildVars.LOGS_ENABLED)
				FileLog.d("ConnectionService onDisconnect");
			setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
			destroy();
			systemCallConnection=null;
			hangUp();
		}

		@Override
		public void onAnswer(){
			acceptIncomingCallFromNotification();
		}

		@Override
		public void onReject(){
			needPlayEndSound=false;
			declineIncomingCall(DISCARD_REASON_HANGUP, null);
		}

		@Override
		public void onShowIncomingCallUi(){
			startRinging();
		}

		@Override
		public void onStateChanged(int state){
			super.onStateChanged(state);
			if(BuildVars.LOGS_ENABLED)
				FileLog.d("ConnectionService onStateChanged "+stateToString(state));
			if(state==Connection.STATE_ACTIVE){
				ContactsController.getInstance(currentAccount).deleteConnectionServiceContact();
				didDeleteConnectionServiceContact=true;
			}
		}

		@Override
		public void onCallEvent(String event, Bundle extras){
			super.onCallEvent(event, extras);
			if(BuildVars.LOGS_ENABLED)
				FileLog.d("ConnectionService onCallEvent "+event);
		}

		// undocumented API
		//@Override
		public void onSilence(){
			if(BuildVars.LOGS_ENABLED)
				FileLog.d("onSlience");
			stopRinging();
		}
	}
}
