/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package org.telegram.messenger.voip;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
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
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.VoIPActivity;
import org.telegram.ui.VoIPFeedbackActivity;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

public class VoIPService extends VoIPBaseService implements NotificationCenter.NotificationCenterDelegate{

	private static final int CALL_MIN_LAYER = 65;
	private static final int CALL_MAX_LAYER = 65;

	public static final int STATE_HANGING_UP = 10;
	public static final int STATE_EXCHANGING_KEYS = 12;
	public static final int STATE_WAITING = 13;
	public static final int STATE_REQUESTING = 14;
	public static final int STATE_WAITING_INCOMING = 15;
	public static final int STATE_RINGING = 16;
	public static final int STATE_BUSY = 17;

	private static final String TAG = "tg-voip-service";

	private TLRPC.User user;
	private TLRPC.PhoneCall call;
	private int callReqId;

	private byte[] g_a;
	private byte[] a_or_b;
	private byte[] g_a_hash;
	private byte[] authKey;
	private long keyFingerprint;
	private boolean forceRating;

	public static TLRPC.PhoneCall callIShouldHavePutIntoIntent;

	private boolean needSendDebugLog=false;
	private boolean endCallAfterRequest=false;
	private ArrayList<TLRPC.PhoneCall> pendingUpdates=new ArrayList<>();
	private Runnable delayedStartOutgoingCall;

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

		int userID=intent.getIntExtra("user_id", 0);
		isOutgoing = intent.getBooleanExtra("is_outgoing", false);
		user = MessagesController.getInstance().getUser(userID);

		if(user==null){
			FileLog.w("VoIPService: user==null");
			stopSelf();
			return START_NOT_STICKY;
		}

		if (isOutgoing) {
			dispatchStateChanged(STATE_REQUESTING);
			delayedStartOutgoingCall=new Runnable(){
				@Override
				public void run(){
					delayedStartOutgoingCall=null;
					startOutgoingCall();
				}
			};
			AndroidUtilities.runOnUIThread(delayedStartOutgoingCall, 2000);
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
	protected void updateServerConfig(){
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
	}

	@Override
	protected void onControllerPreRelease(){
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
	}

	public static VoIPService getSharedInstance() {
		return sharedInstance instanceof VoIPService ? ((VoIPService)sharedInstance) : null;
	}

	public TLRPC.User getUser() {
		return user;
	}

	public void hangUp() {
		declineIncomingCall(currentState == STATE_RINGING || (currentState==STATE_WAITING && isOutgoing) ? DISCARD_REASON_MISSED : DISCARD_REASON_HANGUP, null);
	}

	public void hangUp(Runnable onDone) {
		declineIncomingCall(currentState == STATE_RINGING || (currentState==STATE_WAITING && isOutgoing) ? DISCARD_REASON_MISSED : DISCARD_REASON_HANGUP, onDone);
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
			if(delayedStartOutgoingCall!=null){
				AndroidUtilities.cancelRunOnUIThread(delayedStartOutgoingCall);
				callEnded();
			}else{
				dispatchStateChanged(STATE_HANGING_UP);
				endCallAfterRequest=true;
			}
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
			if (call.need_rating || forceRating) {
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

	public byte[] getEncryptionKey() {
		return authKey;
	}

	private void processAcceptedCall() {

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

			VoIPHelper.upgradeP2pSetting();
			boolean allowP2p=true;
			switch(getSharedPreferences("mainconfig", MODE_PRIVATE).getInt("calls_p2p_new", MessagesController.getInstance().defaultP2pContacts ? 1 : 0)){
				case 0:
					allowP2p=true;
					break;
				case 2:
					allowP2p=false;
					break;
				case 1:
					allowP2p=ContactsController.getInstance().contactsDict.get(user.id)!=null;
					break;
			}
			controller.setRemoteEndpoints(endpoints, call.protocol.udp_p2p && allowP2p);
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

	protected void showNotification(){
		showNotification(ContactsController.formatName(user.first_name, user.last_name), user.photo!=null ? user.photo.photo_small : null, VoIPActivity.class);
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
			Intent endIntent = new Intent(this, VoIPActionsReceiver.class);
			endIntent.setAction(getPackageName() + ".DECLINE_CALL");
			endIntent.putExtra("call_id", getCallID());
			CharSequence endTitle=LocaleController.getString("VoipDeclineCall", R.string.VoipDeclineCall);
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
				endTitle=new SpannableString(endTitle);
				((SpannableString)endTitle).setSpan(new ForegroundColorSpan(0xFFF44336), 0, endTitle.length(), 0);
			}
			builder.addAction(R.drawable.ic_call_end_white_24dp, endTitle, PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT));
			Intent answerIntent = new Intent(this, VoIPActionsReceiver.class);
			answerIntent.setAction(getPackageName() + ".ANSWER_CALL");
			answerIntent.putExtra("call_id", getCallID());
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

	protected void callFailed(int errorCode) {
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
		super.callFailed(errorCode);
	}

	@Override
	protected long getCallID(){
		return call!=null ? call.id : 0;
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

	public byte[] getGA(){
		return g_a;
	}

	@Override
	public void didReceivedNotification(int id, Object... args){
		if(id==NotificationCenter.appDidLogout){
			callEnded();
		}
	}

	public void forceRating(){
		forceRating=true;
	}

}
