/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package org.telegram.messenger.voip;

import android.content.SharedPreferences;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Build;
import android.os.SystemClock;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public class VoIPController{

	public static final int NET_TYPE_UNKNOWN=0;
	public static final int NET_TYPE_GPRS=1;
	public static final int NET_TYPE_EDGE=2;
	public static final int NET_TYPE_3G=3;
	public static final int NET_TYPE_HSPA=4;
	public static final int NET_TYPE_LTE=5;
	public static final int NET_TYPE_WIFI=6;
	public static final int NET_TYPE_ETHERNET=7;
	public static final int NET_TYPE_OTHER_HIGH_SPEED=8;
	public static final int NET_TYPE_OTHER_LOW_SPEED=9;
	public static final int NET_TYPE_DIALUP=10;
	public static final int NET_TYPE_OTHER_MOBILE=11;

	public static final int STATE_WAIT_INIT=1;
	public static final int STATE_WAIT_INIT_ACK=2;
	public static final int STATE_ESTABLISHED=3;
	public static final int STATE_FAILED=4;
	public static final int STATE_RECONNECTING=5;

	public static final int DATA_SAVING_NEVER=0;
	public static final int DATA_SAVING_MOBILE=1;
	public static final int DATA_SAVING_ALWAYS=2;

	public static final int ERROR_CONNECTION_SERVICE=-5;
	public static final int ERROR_INSECURE_UPGRADE=-4;
	public static final int ERROR_LOCALIZED=-3;
	public static final int ERROR_PRIVACY=-2;
	public static final int ERROR_PEER_OUTDATED=-1;
	public static final int ERROR_UNKNOWN=0;
	public static final int ERROR_INCOMPATIBLE=1;
	public static final int ERROR_TIMEOUT=2;
	public static final int ERROR_AUDIO_IO=3;

	public static final int PEER_CAP_GROUP_CALLS=1;

	protected long nativeInst=0;
	protected long callStartTime;
	protected ConnectionStateListener listener;

	public VoIPController(){
		nativeInst=nativeInit();
	}

	public void start(){
		ensureNativeInstance();
		nativeStart(nativeInst);
	}

	public void connect(){
		ensureNativeInstance();
		nativeConnect(nativeInst);
	}

	public void setRemoteEndpoints(TLRPC.TL_phoneConnection[] endpoints, boolean allowP2p, boolean tcp, int connectionMaxLayer){
		if(endpoints.length==0){
			throw new IllegalArgumentException("endpoints size is 0");
		}
		for(int a=0; a<endpoints.length; a++){
			TLRPC.TL_phoneConnection endpoint=endpoints[a];
			if(endpoint.ip==null || endpoint.ip.length()==0){
				throw new IllegalArgumentException("endpoint "+endpoint+" has empty/null ipv4");
			}
			if(endpoint.peer_tag!=null && endpoint.peer_tag.length!=16){
				throw new IllegalArgumentException("endpoint "+endpoint+" has peer_tag of wrong length");
			}
		}
		ensureNativeInstance();
		nativeSetRemoteEndpoints(nativeInst, endpoints, allowP2p, tcp, connectionMaxLayer);
	}

	public void setEncryptionKey(byte[] key, boolean isOutgoing){
		if(key.length!=256){
			throw new IllegalArgumentException("key length must be exactly 256 bytes but is "+key.length);
		}
		ensureNativeInstance();
		nativeSetEncryptionKey(nativeInst, key, isOutgoing);
	}

	public static void setNativeBufferSize(int size){
		nativeSetNativeBufferSize(size);
	}

	public void release(){
		ensureNativeInstance();
		nativeRelease(nativeInst);
		nativeInst=0;
	}

	public String getDebugString(){
		ensureNativeInstance();
		return nativeGetDebugString(nativeInst);
	}

	protected void ensureNativeInstance(){
		if(nativeInst==0){
			throw new IllegalStateException("Native instance is not valid");
		}
	}

	public void setConnectionStateListener(ConnectionStateListener connectionStateListener){
		listener=connectionStateListener;
	}

	// called from native code
	private void handleStateChange(int state){
		if(state==STATE_ESTABLISHED && callStartTime==0)
			callStartTime=SystemClock.elapsedRealtime();
		if(listener!=null){
			listener.onConnectionStateChanged(state);
		}
	}

	// called from native code
	private void handleSignalBarsChange(int count){
		if(listener!=null)
			listener.onSignalBarCountChanged(count);
	}

	// called from native code
	private void groupCallKeyReceived(byte[] key){
		if(listener!=null)
			listener.onGroupCallKeyReceived(key);
	}

	// called from native code
	private void groupCallKeySent(){
		if(listener!=null)
			listener.onGroupCallKeySent();
	}

	// called from native code
	private void callUpgradeRequestReceived(){
		if(listener!=null)
			listener.onCallUpgradeRequestReceived();
	}

	public void setNetworkType(int type){
		ensureNativeInstance();
		nativeSetNetworkType(nativeInst, type);
	}

	public long getCallDuration(){
		return SystemClock.elapsedRealtime()-callStartTime;
	}

	public void setMicMute(boolean mute){
		ensureNativeInstance();
		nativeSetMicMute(nativeInst, mute);
	}

	public void setConfig(double recvTimeout, double initTimeout, int dataSavingOption, long callID){
		ensureNativeInstance();
		boolean sysAecAvailable=false, sysNsAvailable=false;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN){
			try{
				sysAecAvailable=AcousticEchoCanceler.isAvailable();
				sysNsAvailable=AcousticEchoCanceler.isAvailable();
			}catch(Throwable x){

			}
		}
		SharedPreferences preferences=MessagesController.getGlobalMainSettings();
		boolean dump=preferences.getBoolean("dbg_dump_call_stats", false);
		nativeSetConfig(nativeInst, recvTimeout, initTimeout, dataSavingOption,
				!(sysAecAvailable && VoIPServerConfig.getBoolean("use_system_aec", true)),
				!(sysNsAvailable && VoIPServerConfig.getBoolean("use_system_ns", true)),
				true, BuildConfig.DEBUG ? getLogFilePath("voip"+callID) : getLogFilePath(callID), BuildConfig.DEBUG && dump ? getLogFilePath("voipStats") : null);
	}

	public void debugCtl(int request, int param){
		ensureNativeInstance();
		nativeDebugCtl(nativeInst, request, param);
	}

	public long getPreferredRelayID(){
		ensureNativeInstance();
		return nativeGetPreferredRelayID(nativeInst);
	}

	public int getLastError(){
		ensureNativeInstance();
		return nativeGetLastError(nativeInst);
	}

	public void getStats(Stats stats){
		ensureNativeInstance();
		if(stats==null)
			throw new NullPointerException("You're not supposed to pass null here");
		nativeGetStats(nativeInst, stats);
	}

	public static String getVersion(){
		return nativeGetVersion();
	}

	private String getLogFilePath(String name){
		Calendar c=Calendar.getInstance();
		return new File(ApplicationLoader.applicationContext.getExternalFilesDir(null),
				String.format(Locale.US, "logs/%02d_%02d_%04d_%02d_%02d_%02d_%s.txt",
						c.get(Calendar.DATE), c.get(Calendar.MONTH)+1, c.get(Calendar.YEAR),
						c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), name)).getAbsolutePath();
	}

	private String getLogFilePath(long callID){
		File dir=VoIPHelper.getLogsDir();
		if(!BuildConfig.DEBUG){
			File[] _logs=dir.listFiles();
			ArrayList<File> logs=new ArrayList<>();
			logs.addAll(Arrays.asList(_logs));
			while(logs.size()>20){
				File oldest=logs.get(0);
				for(File file : logs){
					if(file.getName().endsWith(".log") && file.lastModified()<oldest.lastModified())
						oldest=file;
				}
				oldest.delete();
				logs.remove(oldest);
			}
		}
		return new File(dir, callID+".log").getAbsolutePath();
	}

	public String getDebugLog(){
		ensureNativeInstance();
		return nativeGetDebugLog(nativeInst);
	}

	public void setProxy(String address, int port, String username, String password){
		ensureNativeInstance();
		if(address==null)
			throw new NullPointerException("address can't be null");
		nativeSetProxy(nativeInst, address, port, username, password);
	}

	public void setAudioOutputGainControlEnabled(boolean enabled){
		ensureNativeInstance();
		nativeSetAudioOutputGainControlEnabled(nativeInst, enabled);
	}

	public int getPeerCapabilities(){
		ensureNativeInstance();
		return nativeGetPeerCapabilities(nativeInst);
	}

	public void sendGroupCallKey(byte[] key){
		if(key==null)
			throw new NullPointerException("key can not be null");
		if(key.length!=256)
			throw new IllegalArgumentException("key must be 256 bytes long, got "+key.length);
		ensureNativeInstance();
		nativeSendGroupCallKey(nativeInst, key);
	}

	public void requestCallUpgrade(){
		ensureNativeInstance();
		nativeRequestCallUpgrade(nativeInst);
	}

	public void setEchoCancellationStrength(int strength){
		ensureNativeInstance();
		nativeSetEchoCancellationStrength(nativeInst, strength);
	}

	private native long nativeInit();
	private native void nativeStart(long inst);
	private native void nativeConnect(long inst);
	private static native void nativeSetNativeBufferSize(int size);
	private native void nativeSetRemoteEndpoints(long inst, TLRPC.TL_phoneConnection[] endpoints, boolean allowP2p, boolean tcp, int connectionMaxLayer);
	private native void nativeRelease(long inst);
	private native void nativeSetNetworkType(long inst, int type);
	private native void nativeSetMicMute(long inst, boolean mute);
	private native void nativeDebugCtl(long inst, int request, int param);
	private native void nativeGetStats(long inst, Stats stats);
	private native void nativeSetConfig(long inst, double recvTimeout, double initTimeout, int dataSavingOption, boolean enableAEC, boolean enableNS, boolean enableAGC, String logFilePath, String statsDumpPath);
	private native void nativeSetEncryptionKey(long inst, byte[] key, boolean isOutgoing);
	private native void nativeSetProxy(long inst, String address, int port, String username, String password);
	private native long nativeGetPreferredRelayID(long inst);
	private native int nativeGetLastError(long inst);
	private native String nativeGetDebugString(long inst);
	private static native String nativeGetVersion();
	private native void nativeSetAudioOutputGainControlEnabled(long inst, boolean enabled);
	private native void nativeSetEchoCancellationStrength(long inst, int strength);
	private native String nativeGetDebugLog(long inst);
	private native int nativeGetPeerCapabilities(long inst);
	private native void nativeSendGroupCallKey(long inst, byte[] key);
	private native void nativeRequestCallUpgrade(long inst);

	public interface ConnectionStateListener{
		void onConnectionStateChanged(int newState);
		void onSignalBarCountChanged(int newCount);
		void onGroupCallKeyReceived(byte[] key);
		void onGroupCallKeySent();
		void onCallUpgradeRequestReceived();
	}

	public static class Stats{
		public long bytesSentWifi;
		public long bytesRecvdWifi;
		public long bytesSentMobile;
		public long bytesRecvdMobile;

		@Override
		public String toString(){
			return "Stats{"+
					"bytesRecvdMobile="+bytesRecvdMobile+
					", bytesSentWifi="+bytesSentWifi+
					", bytesRecvdWifi="+bytesRecvdWifi+
					", bytesSentMobile="+bytesSentMobile+
					'}';
		}
	}
}
