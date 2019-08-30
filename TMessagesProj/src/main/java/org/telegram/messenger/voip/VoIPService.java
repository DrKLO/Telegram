/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package org.telegram.messenger.voip;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.VoIPActivity;
import org.telegram.ui.VoIPFeedbackActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class VoIPService extends VoIPBaseService{

	public static final int CALL_MIN_LAYER = 65;
	public static final int CALL_MAX_LAYER = VoIPController.getConnectionMaxLayer();

	public static final int STATE_HANGING_UP = 10;
	public static final int STATE_EXCHANGING_KEYS = 12;
	public static final int STATE_WAITING = 13;
	public static final int STATE_REQUESTING = 14;
	public static final int STATE_WAITING_INCOMING = 15;
	public static final int STATE_RINGING = 16;
	public static final int STATE_BUSY = 17;

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
	private int peerCapabilities;

	private byte[] groupCallEncryptionKey;
	private long groupCallKeyFingerprint;
	private List<Integer> groupUsersToAdd=new ArrayList<>();
	private boolean upgrading;
	private boolean joiningGroupCall;
	private String debugLog;

	private boolean startedRinging=false;

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


	@SuppressLint("MissingPermission")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(sharedInstance!=null){
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("Tried to start the VoIP service when it's already started");
            }
			return START_NOT_STICKY;
		}

		currentAccount=intent.getIntExtra("account", -1);
		if(currentAccount==-1)
			throw new IllegalStateException("No account specified when starting VoIP service");
		int userID=intent.getIntExtra("user_id", 0);
		isOutgoing = intent.getBooleanExtra("is_outgoing", false);
		user = MessagesController.getInstance(currentAccount).getUser(userID);

		if(user==null){
            if (BuildVars.LOGS_ENABLED) {
                FileLog.w("VoIPService: user==null");
            }
			stopSelf();
			return START_NOT_STICKY;
		}
		sharedInstance = this;

		if (isOutgoing) {
			dispatchStateChanged(STATE_REQUESTING);
			if(USE_CONNECTION_SERVICE){
				TelecomManager tm=(TelecomManager) getSystemService(TELECOM_SERVICE);
				Bundle extras=new Bundle();
				Bundle myExtras=new Bundle();
				extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, addAccountToTelecomManager());
				myExtras.putInt("call_type", 1);
				extras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, myExtras);
				ContactsController.getInstance(currentAccount).createOrUpdateConnectionServiceContact(user.id, user.first_name, user.last_name);
				tm.placeCall(Uri.fromParts("tel", "+99084"+user.id, null), extras);
			}else{
				delayedStartOutgoingCall=new Runnable(){
					@Override
					public void run(){
						delayedStartOutgoingCall=null;
						startOutgoingCall();
					}
				};
				AndroidUtilities.runOnUIThread(delayedStartOutgoingCall, 2000);
			}
			if (intent.getBooleanExtra("start_incall_activity", false)) {
				startActivity(new Intent(this, VoIPActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			}
		} else {
			NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeInCallActivity);
			call = callIShouldHavePutIntoIntent;
			callIShouldHavePutIntoIntent = null;
			if(USE_CONNECTION_SERVICE){
				acknowledgeCall(false);
				showNotification();
			}else{
				acknowledgeCall(true);
			}
		}
		initializeAccountRelatedThings();

		return START_NOT_STICKY;
	}


	@Override
	public void onCreate(){
		super.onCreate();
		if(callIShouldHavePutIntoIntent!=null && Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
			NotificationsController.checkOtherNotificationsChannel();
			Notification.Builder bldr=new Notification.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
					.setSmallIcon(R.drawable.notification)
					.setContentTitle(LocaleController.getString("VoipOutgoingCall", R.string.VoipOutgoingCall))
					.setShowWhen(false);
			startForeground(ID_ONGOING_CALL_NOTIFICATION, bldr.build());
		}
	}

	@Override
	protected void updateServerConfig(){
		final SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
		VoIPServerConfig.setConfig(preferences.getString("voip_server_config", "{}"));
		ConnectionsManager.getInstance(currentAccount).sendRequest(new TLRPC.TL_phone_getCallConfig(), new RequestDelegate(){
			@Override
			public void run(TLObject response, TLRPC.TL_error error){
				if(error==null){
					String data=((TLRPC.TL_dataJSON) response).data;
					VoIPServerConfig.setConfig(data);
					preferences.edit().putString("voip_server_config", data).commit();
				}
			}
		});
	}

	@Override
	protected void onControllerPreRelease(){
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
		if(debugLog==null)
			debugLog=controller.getDebugLog();
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
		if(USE_CONNECTION_SERVICE && systemCallConnection!=null)
			systemCallConnection.setDialing();
		configureDeviceForCall();
		showNotification();
		startConnectingSound();
		dispatchStateChanged(STATE_REQUESTING);
		AndroidUtilities.runOnUIThread(new Runnable(){
			@Override
			public void run(){
				NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall);
			}
		});
		final byte[] salt = new byte[256];
		Utilities.random.nextBytes(salt);

		TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
		req.random_length = 256;
		final MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
		req.version = messagesStorage.getLastSecretVersion();
		callReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
			@Override
			public void run(TLObject response, TLRPC.TL_error error) {
				callReqId = 0;
				if(endCallAfterRequest){
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
					final byte[] salt = new byte[256];
					for (int a = 0; a < 256; a++) {
						salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
					}

					BigInteger i_g_a = BigInteger.valueOf(messagesStorage.getSecretG());
					i_g_a = i_g_a.modPow(new BigInteger(1, salt), new BigInteger(1, messagesStorage.getSecretPBytes()));
					byte[] g_a = i_g_a.toByteArray();
					if (g_a.length > 256) {
						byte[] correctedAuth = new byte[256];
						System.arraycopy(g_a, 1, correctedAuth, 0, 256);
						g_a = correctedAuth;
					}

					TLRPC.TL_phone_requestCall reqCall = new TLRPC.TL_phone_requestCall();
					reqCall.user_id = MessagesController.getInstance(currentAccount).getInputUser(user);
					reqCall.protocol = new TLRPC.TL_phoneCallProtocol();
					reqCall.protocol.udp_p2p = true;
					reqCall.protocol.udp_reflector = true;
					reqCall.protocol.min_layer = CALL_MIN_LAYER;
					reqCall.protocol.max_layer = CALL_MAX_LAYER;
					VoIPService.this.g_a=g_a;
					reqCall.g_a_hash = Utilities.computeSHA256(g_a, 0, g_a.length);
					reqCall.random_id = Utilities.random.nextInt();

					ConnectionsManager.getInstance(currentAccount).sendRequest(reqCall, new RequestDelegate() {
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
												ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
													@Override
													public void run(TLObject response, TLRPC.TL_error error) {
                                                        if (BuildVars.LOGS_ENABLED) {
                                                            if (error != null) {
                                                                FileLog.e("error on phone.discardCall: " + error);
                                                            } else {
                                                                FileLog.d("phone.discardCall " + response);
                                                            }
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
										AndroidUtilities.runOnUIThread(timeoutRunnable, MessagesController.getInstance(currentAccount).callReceiveTimeout);
									} else {
										if (error.code == 400 && "PARTICIPANT_VERSION_OUTDATED".equals(error.text)) {
											callFailed(VoIPController.ERROR_PEER_OUTDATED);
										} else if(error.code==403){
											callFailed(VoIPController.ERROR_PRIVACY);
										}else if(error.code==406){
											callFailed(VoIPController.ERROR_LOCALIZED);
										}else {
                                            if (BuildVars.LOGS_ENABLED) {
                                                FileLog.e("Error on phone.requestCall: " + error);
                                            }
											callFailed();
										}
									}
								}
							});
						}
					}, ConnectionsManager.RequestFlagFailOnServerErrors);
				} else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("Error on getDhConfig " + error);
                    }
					callFailed();
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors);
	}

	private void acknowledgeCall(final boolean startRinging){
		if(call instanceof TLRPC.TL_phoneCallDiscarded){
            if (BuildVars.LOGS_ENABLED) {
                FileLog.w("Call " + call.id + " was discarded before the service started, stopping");
            }
			stopSelf();
			return;
		}
		if(Build.VERSION.SDK_INT>=19 && XiaomiUtilities.isMIUI() && !XiaomiUtilities.isCustomPermissionGranted(XiaomiUtilities.OP_SHOW_WHEN_LOCKED)){
			if(((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode()){
				if(BuildVars.LOGS_ENABLED)
					FileLog.e("MIUI: no permission to show when locked but the screen is locked. ¯\\_(ツ)_/¯");
				stopSelf();
				return;
			}
		}
		TLRPC.TL_phone_receivedCall req = new TLRPC.TL_phone_receivedCall();
		req.peer = new TLRPC.TL_inputPhoneCall();
		req.peer.id = call.id;
		req.peer.access_hash = call.access_hash;
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
			@Override
			public void run(final TLObject response, final TLRPC.TL_error error) {
				AndroidUtilities.runOnUIThread(new Runnable(){
					@Override
					public void run(){
						if(sharedInstance==null)
							return;
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.w("receivedCall response = " + response);
                        }
						if (error != null){
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.e("error on receivedCall: " + error);
                            }
							stopSelf();
						}else{
							if(USE_CONNECTION_SERVICE){
								ContactsController.getInstance(currentAccount).createOrUpdateConnectionServiceContact(user.id, user.first_name, user.last_name);
								TelecomManager tm=(TelecomManager) getSystemService(TELECOM_SERVICE);
								Bundle extras=new Bundle();
								extras.putInt("call_type", 1);
								tm.addNewIncomingCall(addAccountToTelecomManager(), extras);
							}
							if(startRinging)
								startRinging();
						}
					}
				});
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors);
	}

	protected void startRinging() {
		if(currentState==STATE_WAITING_INCOMING){
			return;
		}
		if(USE_CONNECTION_SERVICE && systemCallConnection!=null)
			systemCallConnection.setRinging();
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("starting ringing for call " + call.id);
        }
		dispatchStateChanged(STATE_WAITING_INCOMING);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
			showIncomingNotification(ContactsController.formatName(user.first_name, user.last_name), null, user, null, 0, VoIPActivity.class);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("Showing incoming call notification");
            }
		} else {
			startRingtoneAndVibration(user.id);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("Starting incall activity for incoming call");
            }
			try {
				PendingIntent.getActivity(VoIPService.this, 12345, new Intent(VoIPService.this, VoIPActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0).send();
			} catch (Exception x) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("Error starting incall activity", x);
                }
			}
		}
	}

	@Override
	public void startRingtoneAndVibration(){
		if(!startedRinging){
			startRingtoneAndVibration(user.id);
			startedRinging=true;
		}
	}

	@Override
	protected boolean isRinging(){
		return currentState==STATE_WAITING_INCOMING;
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
				NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall);
			}
		});
		final MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
		TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
		req.random_length = 256;
		req.version = messagesStorage.getLastSecretVersion();
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
			@Override
			public void run(TLObject response, TLRPC.TL_error error) {
				if (error == null) {
					TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
					if (response instanceof TLRPC.TL_messages_dhConfig) {
						if (!Utilities.isGoodPrime(res.p, res.g)) {
							/*acceptingChats.remove(encryptedChat.id);
							declineSecretChat(encryptedChat.id);*/
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
					if(call==null){
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
					ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
						@Override
						public void run(final TLObject response, final TLRPC.TL_error error) {
							AndroidUtilities.runOnUIThread(new Runnable(){
								@Override
								public void run(){
									if (error == null) {
                                        if (BuildVars.LOGS_ENABLED) {
                                            FileLog.w("accept call ok! " + response);
                                        }
										call = ((TLRPC.TL_phone_phoneCall) response).phone_call;
										if(call instanceof TLRPC.TL_phoneCallDiscarded){
											onCallUpdated(call);
										}/*else{
									initiateActualEncryptedCall();
								}*/
									} else {
                                        if (BuildVars.LOGS_ENABLED) {
                                            FileLog.e("Error on phone.acceptCall: " + error);
                                        }
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

	@Override
	protected Class<? extends Activity> getUIActivityClass(){
		return VoIPActivity.class;
	}

	public void declineIncomingCall(int reason, final Runnable onDone) {
		stopRinging();
		callDiscardReason=reason;
		if(currentState==STATE_REQUESTING){
			if(delayedStartOutgoingCall!=null){
				AndroidUtilities.cancelRunOnUIThread(delayedStartOutgoingCall);
				callEnded();
			}else{
				dispatchStateChanged(STATE_HANGING_UP);
				endCallAfterRequest=true;
				AndroidUtilities.runOnUIThread(new Runnable(){
					@Override
					public void run(){
						if(currentState==STATE_HANGING_UP){
							callEnded();
						}
					}
				}, 5000);
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
				ConnectionsManager.getInstance(currentAccount).cancelRequest(callReqId, false);
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
		final boolean wasNotConnected=ConnectionsManager.getInstance(currentAccount).getConnectionState()!=ConnectionsManager.ConnectionStateConnected;
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
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
			@Override
			public void run(TLObject response, TLRPC.TL_error error) {
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
				if (!wasNotConnected){
					AndroidUtilities.cancelRunOnUIThread(stopper);
					if(onDone!=null)
						onDone.run();
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors);
	}

	private void dumpCallObject() {
        try {
            if (BuildVars.LOGS_ENABLED) {
                Field[] flds = TLRPC.PhoneCall.class.getFields();
                for (Field f : flds) {
                    FileLog.d(f.getName() + " = " + f.get(call));
                }
            }
        } catch (Exception x) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e(x);
            }
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
			if(BuildVars.LOGS_ENABLED) {
                FileLog.w("onCallUpdated called with wrong call id (got " + call.id + ", expected " + this.call.id + ")");
            }
			return;
		}
		if(call.access_hash==0)
			call.access_hash=this.call.access_hash;
		if(BuildVars.LOGS_ENABLED) {
            FileLog.d("Call updated: " + call);
            dumpCallObject();
        }
		this.call = call;
		if (call instanceof TLRPC.TL_phoneCallDiscarded) {
			needSendDebugLog=call.need_debug;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("call discarded, stopping service");
            }
			if (call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
				dispatchStateChanged(STATE_BUSY);
				playingSound = true;
				soundPool.play(spBusyId, 1, 1, 0, -1, 1);
				AndroidUtilities.runOnUIThread(afterSoundRunnable, 1500);
				endConnectionServiceCall(1500);
				stopSelf();
			} else {
				callEnded();
			}
			if (call.need_rating || forceRating || (controller!=null && VoIPServerConfig.getBoolean("bad_call_rating", true) && controller.needRate())) {
				startRatingActivity();
			}
			if(debugLog==null && controller!=null){
            	debugLog=controller.getDebugLog();
			}
			if(needSendDebugLog && debugLog!=null){
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
			}
		} else if (call instanceof TLRPC.TL_phoneCall && authKey == null){
			if(call.g_a_or_b==null){
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.w("stopping VoIP service, Ga == null");
                }
				callFailed();
				return;
			}
			if(!Arrays.equals(g_a_hash, Utilities.computeSHA256(call.g_a_or_b, 0, call.g_a_or_b.length))){
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.w("stopping VoIP service, Ga hash doesn't match");
                }
				callFailed();
				return;
			}
			g_a=call.g_a_or_b;
			BigInteger g_a = new BigInteger(1, call.g_a_or_b);
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

			if(keyFingerprint!=call.key_fingerprint){
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.w("key fingerprints don't match");
                }
				callFailed();
				return;
			}

			initiateActualEncryptedCall();
		} else if(call instanceof TLRPC.TL_phoneCallAccepted && authKey==null){
			processAcceptedCall();
		} else {
			if (currentState == STATE_WAITING && call.receive_date != 0) {
				dispatchStateChanged(STATE_RINGING);
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("!!!!!! CALL RECEIVED");
                }
                if(connectingSoundRunnable!=null){
					AndroidUtilities.cancelRunOnUIThread(connectingSoundRunnable);
					connectingSoundRunnable=null;
				}
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
				AndroidUtilities.runOnUIThread(timeoutRunnable, MessagesController.getInstance(currentAccount).callRingTimeout);
			}
		}
	}

	private void startRatingActivity() {
		try {
			PendingIntent.getActivity(VoIPService.this, 0, new Intent(VoIPService.this, VoIPFeedbackActivity.class)
					.putExtra("call_id", call.id)
					.putExtra("call_access_hash", call.access_hash)
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
		BigInteger i_authKey = new BigInteger(1, call.g_b);

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
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate(){
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

	private int convertDataSavingMode(int mode){
		if(mode!=VoIPController.DATA_SAVING_ROAMING)
			return mode;
		return ApplicationLoader.isRoaming() ? VoIPController.DATA_SAVING_MOBILE : VoIPController.DATA_SAVING_NEVER;
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
			SharedPreferences nprefs=MessagesController.getNotificationsSettings(currentAccount);
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
			nprefs.edit().putStringSet("calls_access_hashes", hashes).commit();
			final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
			controller.setConfig(MessagesController.getInstance(currentAccount).callPacketTimeout / 1000.0, MessagesController.getInstance(currentAccount).callConnectTimeout / 1000.0,
					convertDataSavingMode(preferences.getInt("VoipDataSaving", VoIPHelper.getDataSavingDefault())), call.id);
			controller.setEncryptionKey(authKey, isOutgoing);
			TLRPC.TL_phoneConnection[] endpoints=call.connections.toArray(new TLRPC.TL_phoneConnection[call.connections.size()]);

			SharedPreferences prefs=MessagesController.getGlobalMainSettings();

			controller.setRemoteEndpoints(endpoints, call.p2p_allowed, prefs.getBoolean("dbg_force_tcp_in_calls", false), call.protocol.max_layer);
			if(prefs.getBoolean("dbg_force_tcp_in_calls", false)){
				AndroidUtilities.runOnUIThread(new Runnable(){
					@Override
					public void run(){
						Toast.makeText(VoIPService.this, "This call uses TCP which will degrade its quality.", Toast.LENGTH_SHORT).show();
					}
				});
			}
			if(prefs.getBoolean("proxy_enabled", false) && prefs.getBoolean("proxy_enabled_calls", false)) {
				String server = prefs.getString("proxy_ip", null);
				String secret = prefs.getString("proxy_secret", null);
				if (!TextUtils.isEmpty(server) && TextUtils.isEmpty(secret)) {
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
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("error starting call", x);
            }
			callFailed();
		}
	}

	protected void showNotification(){
		showNotification(ContactsController.formatName(user.first_name, user.last_name), user.photo!=null ? user.photo.photo_small : null, VoIPActivity.class);
	}

	private void startConnectingSound() {
		if (spPlayID != 0)
			soundPool.stop(spPlayID);
		spPlayID = soundPool.play(spConnectingId, 1, 1, 0, -1, 1);
		if (spPlayID == 0) {
			AndroidUtilities.runOnUIThread(connectingSoundRunnable=new Runnable() {
				@Override
				public void run() {
					if (sharedInstance == null)
						return;
					if (spPlayID == 0)
						spPlayID = soundPool.play(spConnectingId, 1, 1, 0, -1, 1);
					if (spPlayID == 0)
						AndroidUtilities.runOnUIThread(this, 100);
					else
						connectingSoundRunnable=null;
				}
			}, 100);
		}
	}

	protected void callFailed(int errorCode) {
		if (call != null) {
			if (BuildVars.LOGS_ENABLED) {
				FileLog.d("Discarding failed call");
			}
			TLRPC.TL_phone_discardCall req = new TLRPC.TL_phone_discardCall();
			req.peer = new TLRPC.TL_inputPhoneCall();
			req.peer.access_hash = call.access_hash;
			req.peer.id = call.id;
			req.duration = controller != null && controllerStarted ? (int) (controller.getCallDuration() / 1000) : 0;
			req.connection_id = controller != null && controllerStarted ? controller.getPreferredRelayID() : 0;
			req.reason = new TLRPC.TL_phoneCallDiscardReasonDisconnect();
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
				@Override
				public void run(TLObject response, TLRPC.TL_error error) {
					if (error != null) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.e("error on phone.discardCall: " + error);
                        }
					} else {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("phone.discardCall " + response);
                        }
					}
				}
			});
		}
		super.callFailed(errorCode);
	}

	@Override
	public long getCallID(){
		return call!=null ? call.id : 0;
	}

	public void onUIForegroundStateChanged(boolean isForeground) {
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP)
			return;

		if (currentState == STATE_WAITING_INCOMING) {
			if (isForeground) {
				stopForeground(true);
			} else {
				if (!((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode()) {
					if(NotificationManagerCompat.from(this).areNotificationsEnabled())
						showIncomingNotification(ContactsController.formatName(user.first_name, user.last_name), null, user, null, 0, VoIPActivity.class);
					else
						declineIncomingCall(DISCARD_REASON_LINE_BUSY, null);
				} else {
					AndroidUtilities.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							Intent intent = new Intent(VoIPService.this, VoIPActivity.class);
							intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
							try {
								PendingIntent.getActivity(VoIPService.this, 0, intent, 0).send();
							} catch (PendingIntent.CanceledException e) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.e("error restarting activity", e);
                                }
								declineIncomingCall(DISCARD_REASON_LINE_BUSY, null);
							}
							if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
								showNotification();
							}
						}
					}, 500);
				}
			}
		}
	}

	/*package*/ void onMediaButtonEvent(KeyEvent ev) {
		if (ev.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK || ev.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PAUSE || ev.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
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
	public void didReceivedNotification(int id, int account, Object... args){
		if(id==NotificationCenter.appDidLogout){
			callEnded();
		}
	}

	public void forceRating(){
		forceRating=true;
	}

	private String[] getEmoji(){
		ByteArrayOutputStream os=new ByteArrayOutputStream();
		try{
			os.write(authKey);
			os.write(g_a);
		}catch(IOException ignore){}
		return EncryptionKeyEmojifier.emojifyForCall(Utilities.computeSHA256(os.toByteArray(), 0, os.size()));
	}

	public boolean canUpgrate(){
		return (peerCapabilities & VoIPController.PEER_CAP_GROUP_CALLS)==VoIPController.PEER_CAP_GROUP_CALLS;
	}

	public void upgradeToGroupCall(List<Integer> usersToAdd){
		if(upgrading)
			return;
		groupUsersToAdd=usersToAdd;
		if(!isOutgoing){
			controller.requestCallUpgrade();
			return;
		}
		upgrading=true;
		groupCallEncryptionKey=new byte[256];
		Utilities.random.nextBytes(groupCallEncryptionKey);
		groupCallEncryptionKey[0]&=0x7F;
		byte[] authKeyHash = Utilities.computeSHA1(groupCallEncryptionKey);
		byte[] authKeyId = new byte[8];
		System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
		groupCallKeyFingerprint=Utilities.bytesToLong(authKeyId);

		controller.sendGroupCallKey(groupCallEncryptionKey);
	}

	/*public void upgradedToGroupCall(TLRPC.TL_updateGroupCall update){
		if(upgrading){
			FileLog.w("Received an update about call upgrade but we're upgrading it ourselves; ignoring update");
			return;
		}
		VoIPGroupService.waitingToStart=true;
		TLRPC.TL_phone_getGroupCall req=new TLRPC.TL_phone_getGroupCall();
		req.call=new TLRPC.TL_inputGroupCall();
		req.call.id=update.call.id;
		req.call.access_hash=update.call.access_hash;
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate(){
			@Override
			public void run(TLObject response, TLRPC.TL_error error){
				if(response!=null){
					TLRPC.TL_phone_groupCall call=(TLRPC.TL_phone_groupCall) response;
					if((call.call.flags & 2)==1 || call.call.key_fingerprint!=groupCallKeyFingerprint){
						callFailed(VoIPController.ERROR_INSECURE_UPGRADE);
						return;
					}
					stopSelf();
					VoIPGroupService.callToStartFor=call;
					VoIPGroupService.secretCallEncryptionKey=groupCallEncryptionKey;
					Intent intent=new Intent(ApplicationLoader.applicationContext, VoIPGroupService.class);
					intent.putExtra("account", currentAccount);
					intent.putExtra("use_existing_call", true);
					intent.putExtra("start_incall_activity", true);
					intent.putExtra("need_update_self_streams", true);
					intent.putExtra("private_key_emoji", getEmoji());
					//intent.putExtra("forced_admin_id", user.id);
					int[] uids=new int[groupUsersToAdd.size()];
					for(int i=0;i<uids.length;i++)
						uids[i]=groupUsersToAdd.get(i);
					intent.putExtra("invite_users", uids);
					ApplicationLoader.applicationContext.startService(intent);
				}else{
					VoIPGroupService.waitingToStart=false;
					callFailed();
				}
			}
		});
	}*/

	@Override
	public void onConnectionStateChanged(int newState){
		if(newState==STATE_ESTABLISHED){
			peerCapabilities=controller.getPeerCapabilities();
		}
		super.onConnectionStateChanged(newState);
	}

	@Override
	public void onGroupCallKeyReceived(byte[] key){
		joiningGroupCall=true;
		groupCallEncryptionKey=key;
		byte[] authKeyHash = Utilities.computeSHA1(groupCallEncryptionKey);
		byte[] authKeyId = new byte[8];
		System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
		groupCallKeyFingerprint=Utilities.bytesToLong(authKeyId);
	}

	@Override
	public void onGroupCallKeySent(){
		if(isOutgoing){
			//actuallyUpgradeToGroupCall();
		}
	}

	@Override
	public void onCallUpgradeRequestReceived(){
		upgradeToGroupCall(new ArrayList<Integer>());
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public CallConnection getConnectionAndStartCall(){
		if(systemCallConnection==null){
			if(BuildVars.LOGS_ENABLED)
				FileLog.d("creating call connection");
			systemCallConnection=new CallConnection();
			systemCallConnection.setInitializing();
			if(isOutgoing){
				delayedStartOutgoingCall=new Runnable(){
					@Override
					public void run(){
						delayedStartOutgoingCall=null;
						startOutgoingCall();
					}
				};
				AndroidUtilities.runOnUIThread(delayedStartOutgoingCall, 2000);
			}
			systemCallConnection.setAddress(Uri.fromParts("tel", "+99084"+user.id, null), TelecomManager.PRESENTATION_ALLOWED);
			systemCallConnection.setCallerDisplayName(ContactsController.formatName(user.first_name, user.last_name), TelecomManager.PRESENTATION_ALLOWED);
		}
		return systemCallConnection;
	}

	/*private void actuallyUpgradeToGroupCall(){
		TLRPC.TL_phone_upgradePhoneCall req=new TLRPC.TL_phone_upgradePhoneCall();
		req.peer=new TLRPC.TL_inputPhoneCall();
		req.peer.id=call.id;
		req.peer.access_hash=call.access_hash;
		req.key_fingerprint=groupCallKeyFingerprint;
		req.streams=VoIPGroupController.getInitialStreams();
		upgrading=true;
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate(){
			@Override
			public void run(TLObject response, TLRPC.TL_error error){
				FileLog.d("upgrade call response = "+response);
				if(error!=null){
					FileLog.e("Failed to upgrade call, error: "+error.code+" "+error.text);
					callFailed();
					return;
				}
				stopSelf();
				TLRPC.TL_phone_groupCall call=(TLRPC.TL_phone_groupCall) response;
				VoIPGroupService.callToStartFor=call;
				VoIPGroupService.secretCallEncryptionKey=groupCallEncryptionKey;
				VoIPGroupService.waitingToStart=true;
				Intent intent=new Intent(ApplicationLoader.applicationContext, VoIPGroupService.class);
				intent.putExtra("account", currentAccount);
				intent.putExtra("use_existing_call", true);
				intent.putExtra("start_incall_activity", true);
				intent.putExtra("forced_admin_id", user.id);
				intent.putExtra("private_key_emoji", getEmoji());
				int[] uids=new int[groupUsersToAdd.size()];
				for(int i=0;i<uids.length;i++)
					uids[i]=groupUsersToAdd.get(i);
				intent.putExtra("invite_users", uids);
				ApplicationLoader.applicationContext.startService(intent);
			}
		});
	}*/
}
