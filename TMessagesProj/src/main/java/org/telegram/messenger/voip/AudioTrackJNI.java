package org.telegram.messenger.voip;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.nio.ByteBuffer;

/**
 * Created by grishka on 20.12.16.
 */

public class AudioTrackJNI {
	private AudioTrack audioTrack;
	private byte[] buffer = new byte[960 * 2];
	private boolean running;
	private Thread thread;
	private boolean needResampling;

	private long nativeInst;

	public AudioTrackJNI(long ptr) {
		nativeInst = ptr;
	}

	private int getBufferSize(int min, int sampleRate) {
		return Math.max(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), min);
	}

	public void init(int sampleRate, int bitsPerSample, int channels, int bufferSize) {
		if (audioTrack != null) {
			throw new IllegalStateException("already inited");
		}
		int size = getBufferSize(bufferSize, 48000);
		audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, 48000, channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM);
		if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
			VLog.w("Error initializing AudioTrack with 48k, trying 44.1k with resampling");
			try {
				audioTrack.release();
			} catch (Throwable ignore) {
			}
			size = getBufferSize(bufferSize * 6, 44100);
			VLog.d("buffer size: " + size);
			audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, 44100, channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM);
			needResampling = true;
		}
	}

	public void stop() {
		if (audioTrack != null) {
			try {
				audioTrack.stop();
			} catch (Exception ignore) {

			}
		}
	}

	public void release() {
		running = false;
		if (thread != null) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				VLog.e(e);
			}
			thread = null;
		}
		if (audioTrack != null) {
			audioTrack.release();
			audioTrack = null;
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
		thread = new Thread(() -> {
			try {
				audioTrack.play();
			} catch (Exception x) {
				VLog.e("error starting AudioTrack", x);
				return;
			}
			ByteBuffer tmp48 = needResampling ? ByteBuffer.allocateDirect(960 * 2) : null;
			ByteBuffer tmp44 = needResampling ? ByteBuffer.allocateDirect(882 * 2) : null;
			while (running) {
				try {
					if (needResampling) {
						nativeCallback(buffer);
						tmp48.rewind();
						tmp48.put(buffer);
						Resampler.convert48to44(tmp48, tmp44);
						tmp44.rewind();
						tmp44.get(buffer, 0, 882 * 2);
						audioTrack.write(buffer, 0, 882 * 2);
					} else {
						nativeCallback(buffer);
						audioTrack.write(buffer, 0, 960 * 2);
					}
					if (!running) {
						audioTrack.stop();
						break;
					}
				} catch (Exception e) {
					VLog.e(e);
				}
			}
			VLog.i("audiotrack thread exits");
		});
		thread.start();
	}

	private native void nativeCallback(byte[] buf);
}
