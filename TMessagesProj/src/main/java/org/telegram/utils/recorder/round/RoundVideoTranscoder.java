package org.telegram.utils.recorder.round;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Re-encodes a video range of a writer-produced MP4 with the recording encoder configuration.
 * Decoding starts at a sync sample, but only frames inside the range are drawn and encoded, so
 * nothing before the range start reaches the output.
 */
final class RoundVideoTranscoder {

    interface Sink {
        void onFormat(@NonNull MediaFormat format) throws IOException;

        void onSample(@NonNull ByteBuffer data, @NonNull MediaCodec.BufferInfo info) throws IOException;
    }

    private static final long CODEC_TIMEOUT_US = 10_000;
    private static final long FRAME_TIMEOUT_MS = 2_500;
    private static final long STALL_TIMEOUT_MS = 5_000;

    private RoundVideoTranscoder() {}

    /**
     * Decodes from {@code decodeFromUs}, a sync sample, and encodes the frames presented in
     * [{@code startUs}, {@code endUs}). Sample times keep the source timeline.
     *
     * @return the number of encoded frames
     */
    static int transcode(
            @NonNull File source,
            long decodeFromUs,
            long startUs,
            long endUs,
            int outputSize,
            int videoBitrate,
            int videoFrameRate,
            @NonNull Sink sink
    ) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        Surface encoderSurface = null;
        FrameRenderer renderer = null;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int track = findVideoTrack(extractor);
            if (track < 0) throw new IOException("Source file has no video track");
            extractor.selectTrack(track);
            extractor.seekTo(decodeFromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            MediaFormat inputFormat = extractor.getTrackFormat(track);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(
                    RoundVideoCodecRecorder.createVideoEncoderFormat(
                            outputSize,
                            videoBitrate,
                            videoFrameRate
                    ),
                    null,
                    null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE
            );
            encoderSurface = encoder.createInputSurface();
            encoder.start();
            renderer = new FrameRenderer(encoderSurface, outputSize);

            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
            decoder.configure(inputFormat, renderer.getInputSurface(), null, 0);
            decoder.start();

            return run(extractor, decoder, encoder, renderer, startUs, endUs, sink);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new IOException("Video re-encoding failed", e);
        } finally {
            releaseCodec(decoder);
            if (renderer != null) renderer.release();
            releaseCodec(encoder);
            if (encoderSurface != null) encoderSurface.release();
            extractor.release();
        }
    }

    private static int run(
            @NonNull MediaExtractor extractor,
            @NonNull MediaCodec decoder,
            @NonNull MediaCodec encoder,
            @NonNull FrameRenderer renderer,
            long startUs,
            long endUs,
            @NonNull Sink sink
    ) throws IOException {
        MediaCodec.BufferInfo decoderInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo encoderInfo = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean decoderDone = false;
        boolean encoderDone = false;
        int prependHeaderSize = 0;
        int encodedFrames = 0;
        long lastProgressMs = SystemClock.elapsedRealtime();
        while (!encoderDone) {
            boolean progress = false;
            if (!inputDone) {
                int index = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                if (index >= 0) {
                    long sampleTimeUs = extractor.getSampleTime();
                    boolean sync = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
                    // Stop at the first sync sample past the range, so frames reordered before
                    // it in decode order are still decoded.
                    if (sampleTimeUs < 0 || sampleTimeUs >= endUs && sync) {
                        decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        ByteBuffer input = decoder.getInputBuffer(index);
                        int size = input == null ? -1 : extractor.readSampleData(input, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(index, 0, size, sampleTimeUs, 0);
                            extractor.advance();
                        }
                    }
                    progress = true;
                }
            }
            if (!decoderDone) {
                int index = decoder.dequeueOutputBuffer(decoderInfo, CODEC_TIMEOUT_US);
                if (index >= 0) {
                    long presentationUs = decoderInfo.presentationTimeUs;
                    boolean render = decoderInfo.size > 0
                            && presentationUs >= startUs
                            && presentationUs < endUs;
                    decoder.releaseOutputBuffer(index, render);
                    if (render) {
                        renderer.awaitFrame();
                        renderer.drawFrame(presentationUs * 1000L);
                    }
                    if ((decoderInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoder.signalEndOfInputStream();
                        decoderDone = true;
                    }
                    progress = true;
                }
            }
            int index = encoder.dequeueOutputBuffer(encoderInfo, decoderDone ? CODEC_TIMEOUT_US : 0);
            while (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = encoder.getOutputFormat();
                    prependHeaderSize = RoundVideoCodecRecorder.getPrependedHeaderSize(format);
                    sink.onFormat(format);
                } else if (index >= 0) {
                    ByteBuffer output = encoder.getOutputBuffer(index);
                    boolean config = (encoderInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (output != null && encoderInfo.size > 0 && !config) {
                        if (prependHeaderSize > 0
                                && (encoderInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                                && encoderInfo.size > prependHeaderSize) {
                            encoderInfo.offset += prependHeaderSize;
                            encoderInfo.size -= prependHeaderSize;
                        }
                        sink.onSample(output, encoderInfo);
                        encodedFrames++;
                    }
                    encoderDone = (encoderInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(index, false);
                    if (encoderDone) break;
                }
                progress = true;
                index = encoder.dequeueOutputBuffer(encoderInfo, 0);
            }
            long nowMs = SystemClock.elapsedRealtime();
            if (progress) {
                lastProgressMs = nowMs;
            } else if (nowMs - lastProgressMs > STALL_TIMEOUT_MS) {
                throw new IOException("Video re-encoding stalled");
            }
        }
        return encodedFrames;
    }

    /** Copies encoded samples so they outlive the codec buffers. */
    static final class CollectingSink implements Sink {
        final ArrayList<ByteBuffer> samples = new ArrayList<>();
        final ArrayList<MediaCodec.BufferInfo> infos = new ArrayList<>();
        MediaFormat format;

        @Override
        public void onFormat(@NonNull MediaFormat format) {
            this.format = format;
        }

        @Override
        public void onSample(@NonNull ByteBuffer data, @NonNull MediaCodec.BufferInfo info) {
            ByteBuffer copy = ByteBuffer.allocateDirect(info.size);
            data.position(info.offset);
            data.limit(info.offset + info.size);
            copy.put(data).flip();
            MediaCodec.BufferInfo copyInfo = new MediaCodec.BufferInfo();
            copyInfo.set(0, info.size, info.presentationTimeUs, info.flags);
            samples.add(copy);
            infos.add(copyInfo);
        }
    }

    private static int findVideoTrack(@NonNull MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) return i;
        }
        return -1;
    }

    private static void releaseCodec(@Nullable MediaCodec codec) {
        if (codec == null) return;
        try {
            codec.stop();
        } catch (IllegalStateException ignore) {
        }
        codec.release();
    }

    /** Draws decoder output into the encoder input surface on the calling thread. */
    private static final class FrameRenderer {
        private static final int EGL_RECORDABLE_ANDROID = 0x3142;
        private static final String VERTEX_SHADER =
                "uniform mat4 uTextureMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "    gl_Position = aPosition;\n" +
                "    vTextureCoord = (uTextureMatrix * aTextureCoord).xy;\n" +
                "}\n";
        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                "}\n";
        private static final float[] QUAD = {
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f
        };

        private final int outputSize;
        private final Object frameLock = new Object();
        private final float[] textureMatrix = new float[16];
        private final FloatBuffer quad;
        private final HandlerThread listenerThread;
        private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        private EGLContext context = EGL14.EGL_NO_CONTEXT;
        private EGLSurface surface = EGL14.EGL_NO_SURFACE;
        private int program;
        private int textureId;
        private SurfaceTexture surfaceTexture;
        private Surface inputSurface;
        private boolean frameAvailable;

        FrameRenderer(@NonNull Surface encoderSurface, int outputSize) {
            this.outputSize = outputSize;
            quad = ByteBuffer.allocateDirect(QUAD.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            quad.put(QUAD).position(0);
            listenerThread = new HandlerThread("RoundVideoTranscoderFrames");
            listenerThread.start();
            try {
                initializeEgl(encoderSurface);
                program = createProgram();
                int[] textures = new int[1];
                GLES20.glGenTextures(1, textures, 0);
                textureId = textures[0];
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                surfaceTexture = new SurfaceTexture(textureId);
                surfaceTexture.setOnFrameAvailableListener(texture -> {
                    synchronized (frameLock) {
                        frameAvailable = true;
                        frameLock.notifyAll();
                    }
                }, new Handler(listenerThread.getLooper()));
                inputSurface = new Surface(surfaceTexture);
            } catch (RuntimeException e) {
                release();
                throw e;
            }
        }

        @NonNull
        Surface getInputSurface() {
            return inputSurface;
        }

        void awaitFrame() throws IOException {
            synchronized (frameLock) {
                long deadline = SystemClock.elapsedRealtime() + FRAME_TIMEOUT_MS;
                while (!frameAvailable) {
                    long remaining = deadline - SystemClock.elapsedRealtime();
                    if (remaining <= 0) throw new IOException("Decoded frame did not arrive");
                    try {
                        frameLock.wait(remaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Video re-encoding was interrupted", e);
                    }
                }
                frameAvailable = false;
            }
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(textureMatrix);
        }

        void drawFrame(long presentationTimeNs) {
            GLES20.glViewport(0, 0, outputSize, outputSize);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            int position = GLES20.glGetAttribLocation(program, "aPosition");
            int textureCoord = GLES20.glGetAttribLocation(program, "aTextureCoord");
            quad.position(0);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(position);
            quad.position(2);
            GLES20.glVertexAttribPointer(textureCoord, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(textureCoord);
            GLES20.glUniformMatrix4fv(
                    GLES20.glGetUniformLocation(program, "uTextureMatrix"),
                    1,
                    false,
                    textureMatrix,
                    0
            );
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            EGLExt.eglPresentationTimeANDROID(display, surface, presentationTimeNs);
            if (!EGL14.eglSwapBuffers(display, surface)) {
                throw new IllegalStateException("eglSwapBuffers failed: 0x"
                        + Integer.toHexString(EGL14.eglGetError()));
            }
        }

        void release() {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, surface, surface, context);
                if (program != 0) GLES20.glDeleteProgram(program);
                if (textureId != 0) GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                EGL14.eglReleaseThread();
            }
            display = EGL14.EGL_NO_DISPLAY;
            context = EGL14.EGL_NO_CONTEXT;
            surface = EGL14.EGL_NO_SURFACE;
            program = 0;
            textureId = 0;
            if (inputSurface != null) {
                inputSurface.release();
                inputSurface = null;
            }
            if (surfaceTexture != null) {
                surfaceTexture.release();
                surfaceTexture = null;
            }
            listenerThread.quitSafely();
        }

        private void initializeEgl(@NonNull Surface encoderSurface) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, version, 0, version, 1)) {
                throw new IllegalStateException("Unable to initialize EGL");
            }
            int[] configAttributes = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL_RECORDABLE_ANDROID, 1,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, count, 0)
                    || count[0] == 0) {
                throw new IllegalStateException("Unable to choose EGL config");
            }
            context = EGL14.eglCreateContext(
                    display,
                    configs[0],
                    EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE},
                    0
            );
            if (context == EGL14.EGL_NO_CONTEXT) throw new IllegalStateException("Unable to create EGL context");
            surface = EGL14.eglCreateWindowSurface(display, configs[0], encoderSurface, new int[]{EGL14.EGL_NONE}, 0);
            if (surface == EGL14.EGL_NO_SURFACE) throw new IllegalStateException("Unable to create EGL surface");
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                throw new IllegalStateException("Unable to make EGL context current");
            }
        }

        private static int createProgram() {
            int vertex = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertex);
            GLES20.glAttachShader(program, fragment);
            GLES20.glLinkProgram(program);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            int[] status = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                throw new IllegalStateException("Unable to link program: " + log);
            }
            return program;
        }

        private static int compileShader(int type, @NonNull String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("Unable to compile shader: " + log);
            }
            return shader;
        }
    }
}
