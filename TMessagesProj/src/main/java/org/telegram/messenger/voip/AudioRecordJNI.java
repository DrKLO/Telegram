/*
 * This is the source code of Telegram for Android v. 5.x.x.
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
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.text.TextUtils;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

public class AudioRecordJNI {

	private AudioRecord audioRecord;
	private ByteBuffer buffer;
	private boolean running;
	private Thread thread;
	private int bufferSize;
	private AutomaticGainControl agc;
	private NoiseSuppressor ns;
	private AcousticEchoCanceler aec;
	private boolean needResampling = false;

	private long nativeInst;

	public AudioRecordJNI(long ptr) {
		nativeInst = ptr;
	}

	private int getBufferSize(int min, int sampleRate) {
		return Math.max(AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), min);
	}

	public void init(int sampleRate, int bitsPerSample, int channels, int bufferSize) {
		if (audioRecord != null) {
			throw new IllegalStateException("already inited");
		}
		this.bufferSize = bufferSize;
		boolean res = tryInit(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 48000);
		if (!res)
			res = tryInit(MediaRecorder.AudioSource.MIC, 48000);
		if (!res)
			res = tryInit(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 44100);
		if (!res)
			res = tryInit(MediaRecorder.AudioSource.MIC, 44100);
		if (!res)
			return;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			try {
				if (AutomaticGainControl.isAvailable()) {
					agc = AutomaticGainControl.create(audioRecord.getAudioSessionId());
					if (agc != null)
						agc.setEnabled(false);
				} else {
					VLog.w("AutomaticGainControl is not available on this device :(");
				}
			} catch (Throwable x) {
				VLog.e("error creating AutomaticGainControl", x);
			}
			try {
				if (NoiseSuppressor.isAvailable()) {
					ns = NoiseSuppressor.create(audioRecord.getAudioSessionId());
					if (ns != null) {
						ns.setEnabled(Instance.getGlobalServerConfig().useSystemNs && isGoodAudioEffect(ns));
					}
				} else {
					VLog.w("NoiseSuppressor is not available on this device :(");
				}
			} catch (Throwable x) {
				VLog.e("error creating NoiseSuppressor", x);
			}
			try {
				if (AcousticEchoCanceler.isAvailable()) {
					aec = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
					if (aec != null) {
						aec.setEnabled(Instance.getGlobalServerConfig().useSystemAec && isGoodAudioEffect(aec));
					}
				} else {
					VLog.w("AcousticEchoCanceler is not available on this device");
				}
			} catch (Throwable x) {
				VLog.e("error creating AcousticEchoCanceler", x);
			}
		}

		buffer = ByteBuffer.allocateDirect(bufferSize);
	}

	private boolean tryInit(int source, int sampleRate) {
		if (audioRecord != null) {
			try {
				audioRecord.release();
			} catch (Exception ignore) {
			}
		}
		VLog.i("Trying to initialize AudioRecord with source=" + source + " and sample rate=" + sampleRate);
		int size = getBufferSize(bufferSize, 48000);
		try {
			audioRecord = new AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
		} catch (Exception x) {
			VLog.e("AudioRecord init failed!", x);
		}
		needResampling = sampleRate != 48000;
		return audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED;
	}

	public void stop() {
		try {
			if (audioRecord != null)
				audioRecord.stop();
		} catch (Exception ignore) {
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
		if (audioRecord != null) {
			audioRecord.release();
			audioRecord = null;
		}
		if (agc != null) {
			agc.release();
			agc = null;
		}
		if (ns != null) {
			ns.release();
			ns = null;
		}
		if (aec != null) {
			aec.release();
			aec = null;
		}
	}

	public boolean start() {
		if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
			return false;
		try {
			if (thread == null) {
				if (audioRecord == null) {
					return false;
				}
				audioRecord.startRecording();
				startThread();
			} else {
				audioRecord.startRecording();
			}
			return true;
		} catch (Exception x) {
			VLog.e("Error initializing AudioRecord", x);
		}
		return false;
	}

	private void startThread() {
		if (thread != null) {
			throw new IllegalStateException("thread already started");
		}
		running = true;
		final ByteBuffer tmpBuf = needResampling ? ByteBuffer.allocateDirect(882 * 2) : null;
		thread = new Thread(() -> {
			while (running) {
				try {
					if (!needResampling) {
						audioRecord.read(buffer, 960 * 2);
					} else {
						audioRecord.read(tmpBuf, 882 * 2);
						Resampler.convert44to48(tmpBuf, buffer);
					}
					if (!running) {
						audioRecord.stop();
						break;
					}
					nativeCallback(buffer);
				} catch (Exception e) {
					VLog.e(e);
				}
			}
			VLog.i("audiorecord thread exits");
		});
		thread.start();
	}

	public int getEnabledEffectsMask() {
		int r = 0;
		if (aec != null && aec.getEnabled()) {
			r |= 1;
		}
		if (ns != null && ns.getEnabled()) {
			r |= 2;
		}
		return r;
	}

	private static Pattern makeNonEmptyRegex(String configKey) {
		final String r = Instance.getGlobalServerConfig().getString(configKey);
		if (!TextUtils.isEmpty(r)) {
			try {
				return Pattern.compile(r);
			} catch (Exception x) {
				VLog.e(x);
			}
		}
		return null;
	}

	private static boolean isGoodAudioEffect(AudioEffect effect) {
		Pattern globalImpl = makeNonEmptyRegex("adsp_good_impls"), globalName = makeNonEmptyRegex("adsp_good_names");
		AudioEffect.Descriptor desc = effect.getDescriptor();
		VLog.d(effect.getClass().getSimpleName() + ": implementor=" + desc.implementor + ", name=" + desc.name);
		if (globalImpl != null && globalImpl.matcher(desc.implementor).find()) {
			return true;
		}
		if (globalName != null && globalName.matcher(desc.name).find()) {
			return true;
		}
		if (effect instanceof AcousticEchoCanceler) {
			Pattern impl = makeNonEmptyRegex("aaec_good_impls"), name = makeNonEmptyRegex("aaec_good_names");
			if (impl != null && impl.matcher(desc.implementor).find()) {
				return true;
			}
			if (name != null && name.matcher(desc.name).find()) {
				return true;
			}
		}
		if (effect instanceof NoiseSuppressor) {
			Pattern impl = makeNonEmptyRegex("ans_good_impls"), name = makeNonEmptyRegex("ans_good_names");
			if (impl != null && impl.matcher(desc.implementor).find()) {
				return true;
			}
			if (name != null && name.matcher(desc.name).find()) {
				return true;
			}
		}
		return false;
	}

	private native void nativeCallback(ByteBuffer buf);
}
