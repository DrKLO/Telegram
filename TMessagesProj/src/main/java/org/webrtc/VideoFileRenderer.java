/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.os.Handler;
import android.os.HandlerThread;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;

/**
 * Can be used to save the video frames to file.
 */
public class VideoFileRenderer implements VideoSink {
  private static final String TAG = "VideoFileRenderer";

  private final HandlerThread renderThread;
  private final Handler renderThreadHandler;
  private final HandlerThread fileThread;
  private final Handler fileThreadHandler;
  private final FileOutputStream videoOutFile;
  private final String outputFileName;
  private final int outputFileWidth;
  private final int outputFileHeight;
  private final int outputFrameSize;
  private final ByteBuffer outputFrameBuffer;
  private EglBase eglBase;
  private YuvConverter yuvConverter;
  private int frameCount;

  public VideoFileRenderer(String outputFile, int outputFileWidth, int outputFileHeight,
      final EglBase.Context sharedContext) throws IOException {
    if ((outputFileWidth % 2) == 1 || (outputFileHeight % 2) == 1) {
      throw new IllegalArgumentException("Does not support uneven width or height");
    }

    this.outputFileName = outputFile;
    this.outputFileWidth = outputFileWidth;
    this.outputFileHeight = outputFileHeight;

    outputFrameSize = outputFileWidth * outputFileHeight * 3 / 2;
    outputFrameBuffer = ByteBuffer.allocateDirect(outputFrameSize);

    videoOutFile = new FileOutputStream(outputFile);
    videoOutFile.write(
        ("YUV4MPEG2 C420 W" + outputFileWidth + " H" + outputFileHeight + " Ip F30:1 A1:1\n")
            .getBytes(Charset.forName("US-ASCII")));

    renderThread = new HandlerThread(TAG + "RenderThread");
    renderThread.start();
    renderThreadHandler = new Handler(renderThread.getLooper());

    fileThread = new HandlerThread(TAG + "FileThread");
    fileThread.start();
    fileThreadHandler = new Handler(fileThread.getLooper());

    ThreadUtils.invokeAtFrontUninterruptibly(renderThreadHandler, new Runnable() {
      @Override
      public void run() {
        eglBase = EglBase.create(sharedContext, EglBase.CONFIG_PIXEL_BUFFER);
        eglBase.createDummyPbufferSurface();
        eglBase.makeCurrent();
        yuvConverter = new YuvConverter();
      }
    });
  }

  @Override
  public void onFrame(VideoFrame frame) {
    frame.retain();
    renderThreadHandler.post(() -> renderFrameOnRenderThread(frame));
  }

  private void renderFrameOnRenderThread(VideoFrame frame) {
    final VideoFrame.Buffer buffer = frame.getBuffer();

    // If the frame is rotated, it will be applied after cropAndScale. Therefore, if the frame is
    // rotated by 90 degrees, swap width and height.
    final int targetWidth = frame.getRotation() % 180 == 0 ? outputFileWidth : outputFileHeight;
    final int targetHeight = frame.getRotation() % 180 == 0 ? outputFileHeight : outputFileWidth;

    final float frameAspectRatio = (float) buffer.getWidth() / (float) buffer.getHeight();
    final float fileAspectRatio = (float) targetWidth / (float) targetHeight;

    // Calculate cropping to equalize the aspect ratio.
    int cropWidth = buffer.getWidth();
    int cropHeight = buffer.getHeight();
    if (fileAspectRatio > frameAspectRatio) {
      cropHeight = (int) (cropHeight * (frameAspectRatio / fileAspectRatio));
    } else {
      cropWidth = (int) (cropWidth * (fileAspectRatio / frameAspectRatio));
    }

    final int cropX = (buffer.getWidth() - cropWidth) / 2;
    final int cropY = (buffer.getHeight() - cropHeight) / 2;

    final VideoFrame.Buffer scaledBuffer =
        buffer.cropAndScale(cropX, cropY, cropWidth, cropHeight, targetWidth, targetHeight);
    frame.release();

    final VideoFrame.I420Buffer i420 = scaledBuffer.toI420();
    scaledBuffer.release();

    fileThreadHandler.post(() -> {
      YuvHelper.I420Rotate(i420.getDataY(), i420.getStrideY(), i420.getDataU(), i420.getStrideU(),
          i420.getDataV(), i420.getStrideV(), outputFrameBuffer, i420.getWidth(), i420.getHeight(),
          frame.getRotation());
      i420.release();

      try {
        videoOutFile.write("FRAME\n".getBytes(Charset.forName("US-ASCII")));
        videoOutFile.write(
            outputFrameBuffer.array(), outputFrameBuffer.arrayOffset(), outputFrameSize);
      } catch (IOException e) {
        throw new RuntimeException("Error writing video to disk", e);
      }
      frameCount++;
    });
  }

  /**
   * Release all resources. All already posted frames will be rendered first.
   */
  public void release() {
    final CountDownLatch cleanupBarrier = new CountDownLatch(1);
    renderThreadHandler.post(() -> {
      yuvConverter.release();
      eglBase.release();
      renderThread.quit();
      cleanupBarrier.countDown();
    });
    ThreadUtils.awaitUninterruptibly(cleanupBarrier);
    fileThreadHandler.post(() -> {
      try {
        videoOutFile.close();
        Logging.d(TAG,
            "Video written to disk as " + outputFileName + ". The number of frames is " + frameCount
                + " and the dimensions of the frames are " + outputFileWidth + "x"
                + outputFileHeight + ".");
      } catch (IOException e) {
        throw new RuntimeException("Error closing output file", e);
      }
      fileThread.quit();
    });
    try {
      fileThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Logging.e(TAG, "Interrupted while waiting for the write to disk to complete.", e);
    }
  }
}
