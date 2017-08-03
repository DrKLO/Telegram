/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package org.telegram.messenger.voip;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.util.Log;

import org.telegram.messenger.FileLog;

import java.nio.ByteBuffer;

public class AudioRecordJNI {

	private AudioRecord audioRecord;
	private ByteBuffer buffer;
	private boolean running;
	private Thread thread;
	private int bufferSize;
	private long nativeInst;
	private AutomaticGainControl agc;
	private NoiseSuppressor ns;
	private AcousticEchoCanceler aec;
	private boolean needResampling=false;

	public AudioRecordJNI(long nativeInst) {
		this.nativeInst = nativeInst;
	}

	private int getBufferSize(int min, int sampleRate) {
		return Math.max(AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), min);
	}

	public void init(int sampleRate, int bitsPerSample, int channels, int bufferSize) {
		if (audioRecord != null) {
			throw new IllegalStateException("already inited");
		}
		this.bufferSize = bufferSize;
		boolean res=tryInit(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 48000);
		if(!res)
			tryInit(MediaRecorder.AudioSource.MIC, 48000);
		if(!res)
			tryInit(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 44100);
		if(!res)
			tryInit(MediaRecorder.AudioSource.MIC, 44100);
		buffer = ByteBuffer.allocateDirect(bufferSize);
	}

	private boolean tryInit(int source, int sampleRate){
		if(audioRecord!=null){
			try{
				audioRecord.release();
			}catch(Exception x){}
		}
		FileLog.d("Trying to initialize AudioRecord with source="+source+" and sample rate="+sampleRate);
		int size = getBufferSize(bufferSize, 48000);
		try{
			audioRecord=new AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
		}catch(Exception x){
			FileLog.e("AudioRecord init failed!", x);
		}
		needResampling=sampleRate!=48000;
		return audioRecord!=null && audioRecord.getState()==AudioRecord.STATE_INITIALIZED;
	}

	public void stop() {
		if(audioRecord!=null)
			audioRecord.stop();
	}

	public void release() {
		running = false;
		if(thread!=null){
			try{
				thread.join();
			}catch(InterruptedException e){
				FileLog.e(e);
			}
			thread = null;
		}
		if(audioRecord!=null){
			audioRecord.release();
			audioRecord=null;
		}
		if(agc!=null){
			agc.release();
			agc=null;
		}
		if(ns!=null){
			ns.release();
			ns=null;
		}
		if(aec!=null){
			aec.release();
			aec=null;
		}
	}

	public boolean start() {
		try{
			if(thread==null){
					if(audioRecord==null)
						return false;
					audioRecord.startRecording();
					if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN){
						try{
							if(AutomaticGainControl.isAvailable()){
								agc=AutomaticGainControl.create(audioRecord.getAudioSessionId());
								if(agc!=null)
									agc.setEnabled(false);
							}else{
								FileLog.w("AutomaticGainControl is not available on this device :(");
							}
						}catch(Throwable x){
							FileLog.e("error creating AutomaticGainControl", x);
						}
						try{
							if(NoiseSuppressor.isAvailable()){
								ns=NoiseSuppressor.create(audioRecord.getAudioSessionId());
								if(ns!=null)
									ns.setEnabled(VoIPServerConfig.getBoolean("user_system_ns", true));
							}else{
								FileLog.w("NoiseSuppressor is not available on this device :(");
							}
						}catch(Throwable x){
							FileLog.e("error creating NoiseSuppressor", x);
						}
						try{
							if(AcousticEchoCanceler.isAvailable()){
								aec=AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
								if(aec!=null)
									aec.setEnabled(VoIPServerConfig.getBoolean("use_system_aec", true));
							}else{
								FileLog.w("AcousticEchoCanceler is not available on this device");
							}
						}catch(Throwable x){
							FileLog.e("error creating AcousticEchoCanceler", x);
						}
					}
				startThread();
			}else{
				audioRecord.startRecording();
			}
			return true;
		}catch(Exception x){
			FileLog.e("Error initializing AudioRecord", x);
		}
		return false;
	}

	private void startThread() {
		if (thread != null) {
			throw new IllegalStateException("thread already started");
		}
		running = true;
		final ByteBuffer tmpBuf=needResampling ? ByteBuffer.allocateDirect(882*2) : null;
		thread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (running) {
					try {
						if(!needResampling){
							audioRecord.read(buffer, 960*2);
						}else{
							audioRecord.read(tmpBuf, 882*2);
							Resampler.convert44to48(tmpBuf, buffer);
						}
						if (!running) {
							audioRecord.stop();
							break;
						}
						nativeCallback(buffer);
					} catch (Exception e) {
						FileLog.e(e);
					}
				}
				Log.i("tg-voip", "audiotrack thread exits");
			}
		});
		thread.start();
	}

	private native void nativeCallback(ByteBuffer buf);
}
