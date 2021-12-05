/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.telegram.messenger.FileLog;
import org.webrtc.ThreadUtils.ThreadChecker;

/**
 * Android hardware video decoder.
 */
@SuppressWarnings("deprecation")
// Cannot support API 16 without using deprecated methods.
// TODO(sakal): Rename to MediaCodecVideoDecoder once the deprecated implementation is removed.
class AndroidVideoDecoder implements VideoDecoder, VideoSink {
  private static final String TAG = "AndroidVideoDecoder";

  // TODO(magjed): Use MediaFormat.KEY_* constants when part of the public API.
  private static final String MEDIA_FORMAT_KEY_STRIDE = "stride";
  private static final String MEDIA_FORMAT_KEY_SLICE_HEIGHT = "slice-height";
  private static final String MEDIA_FORMAT_KEY_CROP_LEFT = "crop-left";
  private static final String MEDIA_FORMAT_KEY_CROP_RIGHT = "crop-right";
  private static final String MEDIA_FORMAT_KEY_CROP_TOP = "crop-top";
  private static final String MEDIA_FORMAT_KEY_CROP_BOTTOM = "crop-bottom";

  // MediaCodec.release() occasionally hangs.  Release stops waiting and reports failure after
  // this timeout.
  private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;

  // WebRTC queues input frames quickly in the beginning on the call. Wait for input buffers with a
  // long timeout (500 ms) to prevent this from causing the codec to return an error.
  private static final int DEQUEUE_INPUT_TIMEOUT_US = 500000;

  // Dequeuing an output buffer will block until a buffer is available (up to 100 milliseconds).
  // If this timeout is exceeded, the output thread will unblock and check if the decoder is still
  // running.  If it is, it will block on dequeue again.  Otherwise, it will stop and release the
  // MediaCodec.
  private static final int DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = 100000;

  private final MediaCodecWrapperFactory mediaCodecWrapperFactory;
  private final String codecName;
  private final VideoCodecMimeType codecType;

  private static class FrameInfo {
    final long decodeStartTimeMs;
    final int rotation;

    FrameInfo(long decodeStartTimeMs, int rotation) {
      this.decodeStartTimeMs = decodeStartTimeMs;
      this.rotation = rotation;
    }
  }

  private final BlockingDeque<FrameInfo> frameInfos;
  private int colorFormat;

  // Output thread runs a loop which polls MediaCodec for decoded output buffers.  It reformats
  // those buffers into VideoFrames and delivers them to the callback.  Variable is set on decoder
  // thread and is immutable while the codec is running.
  @Nullable private Thread outputThread;

  // Checker that ensures work is run on the output thread.
  private ThreadChecker outputThreadChecker;

  // Checker that ensures work is run on the decoder thread.  The decoder thread is owned by the
  // caller and must be used to call initDecode, decode, and release.
  private ThreadChecker decoderThreadChecker;

  private volatile boolean running;
  @Nullable private volatile Exception shutdownException;

  // Dimensions (width, height, stride, and sliceHeight) may be accessed by either the decode thread
  // or the output thread.  Accesses should be protected with this lock.
  private final Object dimensionLock = new Object();
  private int width;
  private int height;
  private int stride;
  private int sliceHeight;

  // Whether the decoder has finished the first frame.  The codec may not change output dimensions
  // after delivering the first frame.  Only accessed on the output thread while the decoder is
  // running.
  private boolean hasDecodedFirstFrame;
  // Whether the decoder has seen a key frame.  The first frame must be a key frame.  Only accessed
  // on the decoder thread.
  private boolean keyFrameRequired;

  private final @Nullable EglBase.Context sharedContext;
  // Valid and immutable while the decoder is running.
  @Nullable private SurfaceTextureHelper surfaceTextureHelper;
  @Nullable private Surface surface;

  private static class DecodedTextureMetadata {
    final long presentationTimestampUs;
    final Integer decodeTimeMs;

    DecodedTextureMetadata(long presentationTimestampUs, Integer decodeTimeMs) {
      this.presentationTimestampUs = presentationTimestampUs;
      this.decodeTimeMs = decodeTimeMs;
    }
  }

  // Metadata for the last frame rendered to the texture.
  private final Object renderedTextureMetadataLock = new Object();
  @Nullable private DecodedTextureMetadata renderedTextureMetadata;

  // Decoding proceeds asynchronously.  This callback returns decoded frames to the caller.  Valid
  // and immutable while the decoder is running.
  @Nullable private Callback callback;

  // Valid and immutable while the decoder is running.
  @Nullable private MediaCodecWrapper codec;

  AndroidVideoDecoder(MediaCodecWrapperFactory mediaCodecWrapperFactory, String codecName,
      VideoCodecMimeType codecType, int colorFormat, @Nullable EglBase.Context sharedContext) {
    if (!isSupportedColorFormat(colorFormat)) {
      throw new IllegalArgumentException("Unsupported color format: " + colorFormat);
    }
    Logging.d(TAG,
        "ctor name: " + codecName + " type: " + codecType + " color format: " + colorFormat
            + " context: " + sharedContext);
    this.mediaCodecWrapperFactory = mediaCodecWrapperFactory;
    this.codecName = codecName;
    this.codecType = codecType;
    this.colorFormat = colorFormat;
    this.sharedContext = sharedContext;
    this.frameInfos = new LinkedBlockingDeque<>();
  }

  @Override
  public VideoCodecStatus initDecode(Settings settings, Callback callback) {
    this.decoderThreadChecker = new ThreadChecker();

    this.callback = callback;
    if (sharedContext != null) {
      surfaceTextureHelper = createSurfaceTextureHelper();
      surface = new Surface(surfaceTextureHelper.getSurfaceTexture());
      surfaceTextureHelper.startListening(this);
    }
    return initDecodeInternal(settings.width, settings.height);
  }

  // Internal variant is used when restarting the codec due to reconfiguration.
  private VideoCodecStatus initDecodeInternal(int width, int height) {
    decoderThreadChecker.checkIsOnValidThread();
    Logging.d(TAG,
        "initDecodeInternal name: " + codecName + " type: " + codecType + " width: " + width
            + " height: " + height);
    if (outputThread != null) {
      Logging.e(TAG, "initDecodeInternal called while the codec is already running");
      return VideoCodecStatus.FALLBACK_SOFTWARE;
    }

    // Note:  it is not necessary to initialize dimensions under the lock, since the output thread
    // is not running.
    this.width = width;
    this.height = height;

    stride = width;
    sliceHeight = height;
    hasDecodedFirstFrame = false;
    keyFrameRequired = true;

    try {
      codec = mediaCodecWrapperFactory.createByCodecName(codecName);
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
      Logging.e(TAG, "Cannot create media decoder " + codecName);
      return VideoCodecStatus.FALLBACK_SOFTWARE;
    }
    try {
      MediaFormat format = MediaFormat.createVideoFormat(codecType.mimeType(), width, height);
      if (sharedContext == null) {
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
      }
      codec.configure(format, surface, null, 0);
      codec.start();
    } catch (IllegalStateException | IllegalArgumentException e) {
      Logging.e(TAG, "initDecode failed", e);
      release();
      return VideoCodecStatus.FALLBACK_SOFTWARE;
    }
    running = true;
    outputThread = createOutputThread();
    outputThread.start();

    Logging.d(TAG, "initDecodeInternal done");
    return VideoCodecStatus.OK;
  }

  @Override
  public VideoCodecStatus decode(EncodedImage frame, DecodeInfo info) {
    decoderThreadChecker.checkIsOnValidThread();
    if (codec == null || callback == null) {
      Logging.d(TAG, "decode uninitalized, codec: " + (codec != null) + ", callback: " + callback);
      return VideoCodecStatus.UNINITIALIZED;
    }

    if (frame.buffer == null) {
      Logging.e(TAG, "decode() - no input data");
      return VideoCodecStatus.ERR_PARAMETER;
    }

    int size = frame.buffer.remaining();
    if (size == 0) {
      Logging.e(TAG, "decode() - input buffer empty");
      return VideoCodecStatus.ERR_PARAMETER;
    }

    // Load dimensions from shared memory under the dimension lock.
    final int width;
    final int height;
    synchronized (dimensionLock) {
      width = this.width;
      height = this.height;
    }

    // Check if the resolution changed and reset the codec if necessary.
    if (frame.encodedWidth * frame.encodedHeight > 0
        && (frame.encodedWidth != width || frame.encodedHeight != height)) {
      VideoCodecStatus status = reinitDecode(frame.encodedWidth, frame.encodedHeight);
      if (status != VideoCodecStatus.OK) {
        return status;
      }
    }

    if (keyFrameRequired) {
      // Need to process a key frame first.
      if (frame.frameType != EncodedImage.FrameType.VideoFrameKey) {
        Logging.e(TAG, "decode() - key frame required first");
        return VideoCodecStatus.NO_OUTPUT;
      }
    }

    int index;
    try {
      index = codec.dequeueInputBuffer(DEQUEUE_INPUT_TIMEOUT_US);
    } catch (IllegalStateException e) {
      Logging.e(TAG, "dequeueInputBuffer failed", e);
      return VideoCodecStatus.ERROR;
    }
    if (index < 0) {
      // Decoder is falling behind.  No input buffers available.
      // The decoder can't simply drop frames; it might lose a key frame.
      Logging.e(TAG, "decode() - no HW buffers available; decoder falling behind");
      return VideoCodecStatus.ERROR;
    }

    ByteBuffer buffer;
    try {
      buffer = codec.getInputBuffers()[index];
    } catch (IllegalStateException e) {
      Logging.e(TAG, "getInputBuffers failed", e);
      return VideoCodecStatus.ERROR;
    }

    if (buffer.capacity() < size) {
      Logging.e(TAG, "decode() - HW buffer too small");
      return VideoCodecStatus.ERROR;
    }
    buffer.put(frame.buffer);

    frameInfos.offer(new FrameInfo(SystemClock.elapsedRealtime(), frame.rotation));
    try {
      codec.queueInputBuffer(index, 0 /* offset */, size,
          TimeUnit.NANOSECONDS.toMicros(frame.captureTimeNs), 0 /* flags */);
    } catch (IllegalStateException e) {
      Logging.e(TAG, "queueInputBuffer failed", e);
      frameInfos.pollLast();
      return VideoCodecStatus.ERROR;
    }
    if (keyFrameRequired) {
      keyFrameRequired = false;
    }
    return VideoCodecStatus.OK;
  }

  @Override
  public boolean getPrefersLateDecoding() {
    return true;
  }

  @Override
  public String getImplementationName() {
    return codecName;
  }

  @Override
  public VideoCodecStatus release() {
    // TODO(sakal): This is not called on the correct thread but is still called synchronously.
    // Re-enable the check once this is called on the correct thread.
    // decoderThreadChecker.checkIsOnValidThread();
    Logging.d(TAG, "release");
    VideoCodecStatus status = releaseInternal();
    if (surface != null) {
      releaseSurface();
      surface = null;
      surfaceTextureHelper.stopListening();
      surfaceTextureHelper.dispose();
      surfaceTextureHelper = null;
    }
    synchronized (renderedTextureMetadataLock) {
      renderedTextureMetadata = null;
    }
    callback = null;
    frameInfos.clear();
    return status;
  }

  // Internal variant is used when restarting the codec due to reconfiguration.
  private VideoCodecStatus releaseInternal() {
    if (!running) {
      Logging.d(TAG, "release: Decoder is not running.");
      return VideoCodecStatus.OK;
    }
    try {
      // The outputThread actually stops and releases the codec once running is false.
      running = false;
      if (!ThreadUtils.joinUninterruptibly(outputThread, MEDIA_CODEC_RELEASE_TIMEOUT_MS)) {
        // Log an exception to capture the stack trace and turn it into a TIMEOUT error.
        Logging.e(TAG, "Media decoder release timeout", new RuntimeException());
        return VideoCodecStatus.TIMEOUT;
      }
      if (shutdownException != null) {
        // Log the exception and turn it into an error.  Wrap the exception in a new exception to
        // capture both the output thread's stack trace and this thread's stack trace.
        Logging.e(TAG, "Media decoder release error", new RuntimeException(shutdownException));
        shutdownException = null;
        return VideoCodecStatus.ERROR;
      }
    } finally {
      codec = null;
      outputThread = null;
    }
    return VideoCodecStatus.OK;
  }

  private VideoCodecStatus reinitDecode(int newWidth, int newHeight) {
    decoderThreadChecker.checkIsOnValidThread();
    VideoCodecStatus status = releaseInternal();
    if (status != VideoCodecStatus.OK) {
      return status;
    }
    return initDecodeInternal(newWidth, newHeight);
  }

  private Thread createOutputThread() {
    return new Thread("AndroidVideoDecoder.outputThread") {
      @Override
      public void run() {
        outputThreadChecker = new ThreadChecker();
        while (running) {
          deliverDecodedFrame();
        }
        releaseCodecOnOutputThread();
      }
    };
  }

  // Visible for testing.
  protected void deliverDecodedFrame() {
    outputThreadChecker.checkIsOnValidThread();
    try {
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      // Block until an output buffer is available (up to 100 milliseconds).  If the timeout is
      // exceeded, deliverDecodedFrame() will be called again on the next iteration of the output
      // thread's loop.  Blocking here prevents the output thread from busy-waiting while the codec
      // is idle.
      int result = codec.dequeueOutputBuffer(info, DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US);
      if (result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        reformat(codec.getOutputFormat());
        return;
      }

      if (result < 0) {
        Logging.v(TAG, "dequeueOutputBuffer returned " + result);
        return;
      }

      FrameInfo frameInfo = frameInfos.poll();
      Integer decodeTimeMs = null;
      int rotation = 0;
      if (frameInfo != null) {
        decodeTimeMs = (int) (SystemClock.elapsedRealtime() - frameInfo.decodeStartTimeMs);
        rotation = frameInfo.rotation;
      }

      hasDecodedFirstFrame = true;

      if (surfaceTextureHelper != null) {
        deliverTextureFrame(result, info, rotation, decodeTimeMs);
      } else {
        deliverByteFrame(result, info, rotation, decodeTimeMs);
      }

    } catch (IllegalStateException e) {
      Logging.e(TAG, "deliverDecodedFrame failed", e);
    }
  }

  private void deliverTextureFrame(final int index, final MediaCodec.BufferInfo info,
      final int rotation, final Integer decodeTimeMs) {
    // Load dimensions from shared memory under the dimension lock.
    final int width;
    final int height;
    synchronized (dimensionLock) {
      width = this.width;
      height = this.height;
    }

    synchronized (renderedTextureMetadataLock) {
      if (renderedTextureMetadata != null) {
        codec.releaseOutputBuffer(index, false);
        return; // We are still waiting for texture for the previous frame, drop this one.
      }
      surfaceTextureHelper.setTextureSize(width, height);
      surfaceTextureHelper.setFrameRotation(rotation);
      renderedTextureMetadata = new DecodedTextureMetadata(info.presentationTimeUs, decodeTimeMs);
      codec.releaseOutputBuffer(index, /* render= */ true);
    }
  }

  @Override
  public void onFrame(VideoFrame frame) {
    final VideoFrame newFrame;
    final Integer decodeTimeMs;
    final long timestampNs;
    synchronized (renderedTextureMetadataLock) {
      if (renderedTextureMetadata == null) {
        throw new IllegalStateException(
            "Rendered texture metadata was null in onTextureFrameAvailable.");
      }
      timestampNs = renderedTextureMetadata.presentationTimestampUs * 1000;
      decodeTimeMs = renderedTextureMetadata.decodeTimeMs;
      renderedTextureMetadata = null;
    }
    // Change timestamp of frame.
    final VideoFrame frameWithModifiedTimeStamp =
        new VideoFrame(frame.getBuffer(), frame.getRotation(), timestampNs);
    callback.onDecodedFrame(frameWithModifiedTimeStamp, decodeTimeMs, null /* qp */);
  }

  private void deliverByteFrame(
      int result, MediaCodec.BufferInfo info, int rotation, Integer decodeTimeMs) {
    // Load dimensions from shared memory under the dimension lock.
    int width;
    int height;
    int stride;
    int sliceHeight;
    synchronized (dimensionLock) {
      width = this.width;
      height = this.height;
      stride = this.stride;
      sliceHeight = this.sliceHeight;
    }

    // Output must be at least width * height bytes for Y channel, plus (width / 2) * (height / 2)
    // bytes for each of the U and V channels.
    if (info.size < width * height * 3 / 2) {
      Logging.e(TAG, "Insufficient output buffer size: " + info.size);
      return;
    }

    if (info.size < stride * height * 3 / 2 && sliceHeight == height && stride > width) {
      // Some codecs (Exynos) report an incorrect stride.  Correct it here.
      // Expected size == stride * height * 3 / 2.  A bit of algebra gives the correct stride as
      // 2 * size / (3 * height).
      stride = info.size * 2 / (height * 3);
    }

    ByteBuffer buffer = codec.getOutputBuffers()[result];
    buffer.position(info.offset);
    buffer.limit(info.offset + info.size);
    buffer = buffer.slice();

    final VideoFrame.Buffer frameBuffer;
    if (colorFormat == CodecCapabilities.COLOR_FormatYUV420Planar) {
      frameBuffer = copyI420Buffer(buffer, stride, sliceHeight, width, height);
    } else {
      // All other supported color formats are NV12.
      frameBuffer = copyNV12ToI420Buffer(buffer, stride, sliceHeight, width, height);
    }
    codec.releaseOutputBuffer(result, /* render= */ false);

    long presentationTimeNs = info.presentationTimeUs * 1000;
    VideoFrame frame = new VideoFrame(frameBuffer, rotation, presentationTimeNs);

    // Note that qp is parsed on the C++ side.
    callback.onDecodedFrame(frame, decodeTimeMs, null /* qp */);
    frame.release();
  }

  private VideoFrame.Buffer copyNV12ToI420Buffer(
      ByteBuffer buffer, int stride, int sliceHeight, int width, int height) {
    // toI420 copies the buffer.
    return new NV12Buffer(width, height, stride, sliceHeight, buffer, null /* releaseCallback */)
        .toI420();
  }

  private VideoFrame.Buffer copyI420Buffer(
      ByteBuffer buffer, int stride, int sliceHeight, int width, int height) {
    if (stride % 2 != 0) {
      throw new AssertionError("Stride is not divisible by two: " + stride);
    }

    // Note that the case with odd |sliceHeight| is handled in a special way.
    // The chroma height contained in the payload is rounded down instead of
    // up, making it one row less than what we expect in WebRTC. Therefore, we
    // have to duplicate the last chroma rows for this case. Also, the offset
    // between the Y plane and the U plane is unintuitive for this case. See
    // http://bugs.webrtc.org/6651 for more info.
    final int chromaWidth = (width + 1) / 2;
    final int chromaHeight = (sliceHeight % 2 == 0) ? (height + 1) / 2 : height / 2;

    final int uvStride = stride / 2;

    final int yPos = 0;
    final int yEnd = yPos + stride * height;
    final int uPos = yPos + stride * sliceHeight;
    final int uEnd = uPos + uvStride * chromaHeight;
    final int vPos = uPos + uvStride * sliceHeight / 2;
    final int vEnd = vPos + uvStride * chromaHeight;

    VideoFrame.I420Buffer frameBuffer = allocateI420Buffer(width, height);
    try { //don't crash
      buffer.limit(yEnd);
      buffer.position(yPos);
      copyPlane(
              buffer.slice(), stride, frameBuffer.getDataY(), frameBuffer.getStrideY(), width, height);

      buffer.limit(uEnd);
      buffer.position(uPos);
      copyPlane(buffer.slice(), uvStride, frameBuffer.getDataU(), frameBuffer.getStrideU(),
              chromaWidth, chromaHeight);
      if (sliceHeight % 2 == 1) {
        buffer.position(uPos + uvStride * (chromaHeight - 1)); // Seek to beginning of last full row.

        ByteBuffer dataU = frameBuffer.getDataU();
        dataU.position(frameBuffer.getStrideU() * chromaHeight); // Seek to beginning of last row.
        dataU.put(buffer); // Copy the last row.
      }

      buffer.limit(vEnd);
      buffer.position(vPos);
      copyPlane(buffer.slice(), uvStride, frameBuffer.getDataV(), frameBuffer.getStrideV(),
              chromaWidth, chromaHeight);
      if (sliceHeight % 2 == 1) {
        buffer.position(vPos + uvStride * (chromaHeight - 1)); // Seek to beginning of last full row.

        ByteBuffer dataV = frameBuffer.getDataV();
        dataV.position(frameBuffer.getStrideV() * chromaHeight); // Seek to beginning of last row.
        dataV.put(buffer); // Copy the last row.
      }
    } catch (Throwable e) {
      FileLog.e(e);
    }

    return frameBuffer;
  }

  private void reformat(MediaFormat format) {
    outputThreadChecker.checkIsOnValidThread();
    Logging.d(TAG, "Decoder format changed: " + format.toString());
    final int newWidth;
    final int newHeight;
    if (format.containsKey(MEDIA_FORMAT_KEY_CROP_LEFT)
        && format.containsKey(MEDIA_FORMAT_KEY_CROP_RIGHT)
        && format.containsKey(MEDIA_FORMAT_KEY_CROP_BOTTOM)
        && format.containsKey(MEDIA_FORMAT_KEY_CROP_TOP)) {
      newWidth = 1 + format.getInteger(MEDIA_FORMAT_KEY_CROP_RIGHT)
          - format.getInteger(MEDIA_FORMAT_KEY_CROP_LEFT);
      newHeight = 1 + format.getInteger(MEDIA_FORMAT_KEY_CROP_BOTTOM)
          - format.getInteger(MEDIA_FORMAT_KEY_CROP_TOP);
    } else {
      newWidth = format.getInteger(MediaFormat.KEY_WIDTH);
      newHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
    }
    // Compare to existing width, height, and save values under the dimension lock.
    synchronized (dimensionLock) {
      if (newWidth != width || newHeight != height) {
        if (hasDecodedFirstFrame) {
          stopOnOutputThread(new RuntimeException("Unexpected size change. "
              + "Configured " + width + "*" + height + ". "
              + "New " + newWidth + "*" + newHeight));
          return;
        } else if (newWidth <= 0 || newHeight <= 0) {
          Logging.w(TAG,
              "Unexpected format dimensions. Configured " + width + "*" + height + ". "
                  + "New " + newWidth + "*" + newHeight + ". Skip it");
          return;
        }
        width = newWidth;
        height = newHeight;
      }
    }

    // Note:  texture mode ignores colorFormat.  Hence, if the texture helper is non-null, skip
    // color format updates.
    if (surfaceTextureHelper == null && format.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
      colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
      Logging.d(TAG, "Color: 0x" + Integer.toHexString(colorFormat));
      if (!isSupportedColorFormat(colorFormat)) {
        stopOnOutputThread(new IllegalStateException("Unsupported color format: " + colorFormat));
        return;
      }
    }

    // Save stride and sliceHeight under the dimension lock.
    synchronized (dimensionLock) {
      if (format.containsKey(MEDIA_FORMAT_KEY_STRIDE)) {
        stride = format.getInteger(MEDIA_FORMAT_KEY_STRIDE);
      }
      if (format.containsKey(MEDIA_FORMAT_KEY_SLICE_HEIGHT)) {
        sliceHeight = format.getInteger(MEDIA_FORMAT_KEY_SLICE_HEIGHT);
      }
      Logging.d(TAG, "Frame stride and slice height: " + stride + " x " + sliceHeight);
      stride = Math.max(width, stride);
      sliceHeight = Math.max(height, sliceHeight);
    }
  }

  private void releaseCodecOnOutputThread() {
    outputThreadChecker.checkIsOnValidThread();
    Logging.d(TAG, "Releasing MediaCodec on output thread");
    try {
      codec.stop();
    } catch (Exception e) {
      Logging.e(TAG, "Media decoder stop failed", e);
    }
    try {
      codec.release();
    } catch (Exception e) {
      Logging.e(TAG, "Media decoder release failed", e);
      // Propagate exceptions caught during release back to the main thread.
      shutdownException = e;
    }
    Logging.d(TAG, "Release on output thread done");
  }

  private void stopOnOutputThread(Exception e) {
    outputThreadChecker.checkIsOnValidThread();
    running = false;
    shutdownException = e;
  }

  private boolean isSupportedColorFormat(int colorFormat) {
    for (int supported : MediaCodecUtils.DECODER_COLOR_FORMATS) {
      if (supported == colorFormat) {
        return true;
      }
    }
    return false;
  }

  // Visible for testing.
  protected SurfaceTextureHelper createSurfaceTextureHelper() {
    return SurfaceTextureHelper.create("decoder-texture-thread", sharedContext);
  }

  // Visible for testing.
  // TODO(sakal): Remove once Robolectric commit fa991a0 has been rolled to WebRTC.
  protected void releaseSurface() {
    surface.release();
  }

  // Visible for testing.
  protected VideoFrame.I420Buffer allocateI420Buffer(int width, int height) {
    return JavaI420Buffer.allocate(width, height);
  }

  // Visible for testing.
  protected void copyPlane(
      ByteBuffer src, int srcStride, ByteBuffer dst, int dstStride, int width, int height) {
    YuvHelper.copyPlane(src, srcStride, dst, dstStride, width, height);
  }
}
