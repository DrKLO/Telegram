package org.telegram.messenger.voip;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.util.Log;

import org.telegram.messenger.FileLog;

import java.nio.ByteBuffer;

/**
 * Created by grishka on 20.12.16.
 */

public class AudioTrackJNI{
	private AudioTrack audioTrack;
	private byte[] buffer=new byte[960*2];
	private boolean running;
	private Thread thread;
	private int bufferSize;
	private long nativeInst;

	public AudioTrackJNI(long nativeInst) {
		this.nativeInst = nativeInst;
	}

	private int getBufferSize(int min) {
		return Math.max(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), min);
	}

	public void init(int sampleRate, int bitsPerSample, int channels, int bufferSize) {
		if (audioTrack != null) {
			throw new IllegalStateException("already inited");
		}
		int size = getBufferSize(bufferSize);
		this.bufferSize = bufferSize;
		audioTrack=new AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRate, channels==1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM);
	}

	public void stop() {
		if(audioTrack!=null)
			audioTrack.stop();
	}

	public void release() {
		running = false;
		if(thread!=null){
			try{
				thread.join();
			}catch(InterruptedException e){
				FileLog.e(e);
			}
			thread=null;
		}
		if(audioTrack!=null){
			audioTrack.release();
			audioTrack=null;
		}
	}

	public void start() {
		if (thread == null) {
			startThread();
		} else {
			audioTrack.play();
		}
	}

	private void startThread() {
		if (thread != null) {
			throw new IllegalStateException("thread already started");
		}
		running = true;
		thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try{
					audioTrack.play();
				}catch(Exception x){
					FileLog.e("error starting AudioTrack", x);
					return;
				}
				while (running) {
					try {
						nativeCallback(buffer);
						audioTrack.write(buffer, 0, 960*2);
						if (!running) {
							audioTrack.stop();
							break;
						}
					} catch (Exception e) {
						FileLog.e(e);
					}
				}
				Log.i("tg-voip", "audiotrack thread exits");
			}
		});
		thread.start();
	}

	private native void nativeCallback(byte[] buf);
}
