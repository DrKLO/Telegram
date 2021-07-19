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

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class FileVideoCapturer implements VideoCapturer {
  private interface VideoReader {
    VideoFrame getNextFrame();
    void close();
  }

  /**
   * Read video data from file for the .y4m container.
   */
  @SuppressWarnings("StringSplitter")
  private static class VideoReaderY4M implements VideoReader {
    private static final String TAG = "VideoReaderY4M";
    private static final String Y4M_FRAME_DELIMETER = "FRAME";
    private static final int FRAME_DELIMETER_LENGTH = Y4M_FRAME_DELIMETER.length() + 1;

    private final int frameWidth;
    private final int frameHeight;
    // First char after header
    private final long videoStart;
    private final RandomAccessFile mediaFile;
    private final FileChannel mediaFileChannel;

    public VideoReaderY4M(String file) throws IOException {
      mediaFile = new RandomAccessFile(file, "r");
      mediaFileChannel = mediaFile.getChannel();
      StringBuilder builder = new StringBuilder();
      for (;;) {
        int c = mediaFile.read();
        if (c == -1) {
          // End of file reached.
          throw new RuntimeException("Found end of file before end of header for file: " + file);
        }
        if (c == '\n') {
          // End of header found.
          break;
        }
        builder.append((char) c);
      }
      videoStart = mediaFileChannel.position();
      String header = builder.toString();
      String[] headerTokens = header.split("[ ]");
      int w = 0;
      int h = 0;
      String colorSpace = "";
      for (String tok : headerTokens) {
        char c = tok.charAt(0);
        switch (c) {
          case 'W':
            w = Integer.parseInt(tok.substring(1));
            break;
          case 'H':
            h = Integer.parseInt(tok.substring(1));
            break;
          case 'C':
            colorSpace = tok.substring(1);
            break;
        }
      }
      Logging.d(TAG, "Color space: " + colorSpace);
      if (!colorSpace.equals("420") && !colorSpace.equals("420mpeg2")) {
        throw new IllegalArgumentException(
            "Does not support any other color space than I420 or I420mpeg2");
      }
      if ((w % 2) == 1 || (h % 2) == 1) {
        throw new IllegalArgumentException("Does not support odd width or height");
      }
      frameWidth = w;
      frameHeight = h;
      Logging.d(TAG, "frame dim: (" + w + ", " + h + ")");
    }

    @Override
    public VideoFrame getNextFrame() {
      final long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
      final JavaI420Buffer buffer = JavaI420Buffer.allocate(frameWidth, frameHeight);
      final ByteBuffer dataY = buffer.getDataY();
      final ByteBuffer dataU = buffer.getDataU();
      final ByteBuffer dataV = buffer.getDataV();
      final int chromaHeight = (frameHeight + 1) / 2;
      final int sizeY = frameHeight * buffer.getStrideY();
      final int sizeU = chromaHeight * buffer.getStrideU();
      final int sizeV = chromaHeight * buffer.getStrideV();

      try {
        ByteBuffer frameDelim = ByteBuffer.allocate(FRAME_DELIMETER_LENGTH);
        if (mediaFileChannel.read(frameDelim) < FRAME_DELIMETER_LENGTH) {
          // We reach end of file, loop
          mediaFileChannel.position(videoStart);
          if (mediaFileChannel.read(frameDelim) < FRAME_DELIMETER_LENGTH) {
            throw new RuntimeException("Error looping video");
          }
        }
        String frameDelimStr = new String(frameDelim.array(), Charset.forName("US-ASCII"));
        if (!frameDelimStr.equals(Y4M_FRAME_DELIMETER + "\n")) {
          throw new RuntimeException(
              "Frames should be delimited by FRAME plus newline, found delimter was: '"
              + frameDelimStr + "'");
        }

        mediaFileChannel.read(dataY);
        mediaFileChannel.read(dataU);
        mediaFileChannel.read(dataV);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      return new VideoFrame(buffer, 0 /* rotation */, captureTimeNs);
    }

    @Override
    public void close() {
      try {
        // Closing a file also closes the channel.
        mediaFile.close();
      } catch (IOException e) {
        Logging.e(TAG, "Problem closing file", e);
      }
    }
  }

  private final static String TAG = "FileVideoCapturer";
  private final VideoReader videoReader;
  private CapturerObserver capturerObserver;
  private final Timer timer = new Timer();

  private final TimerTask tickTask = new TimerTask() {
    @Override
    public void run() {
      tick();
    }
  };

  public FileVideoCapturer(String inputFile) throws IOException {
    try {
      videoReader = new VideoReaderY4M(inputFile);
    } catch (IOException e) {
      Logging.d(TAG, "Could not open video file: " + inputFile);
      throw e;
    }
  }

  public void tick() {
    VideoFrame videoFrame = videoReader.getNextFrame();
    capturerObserver.onFrameCaptured(videoFrame);
    videoFrame.release();
  }

  @Override
  public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext,
      CapturerObserver capturerObserver) {
    this.capturerObserver = capturerObserver;
  }

  @Override
  public void startCapture(int width, int height, int framerate) {
    timer.schedule(tickTask, 0, 1000 / framerate);
  }

  @Override
  public void stopCapture() throws InterruptedException {
    timer.cancel();
  }

  @Override
  public void changeCaptureFormat(int width, int height, int framerate) {
    // Empty on purpose
  }

  @Override
  public void dispose() {
    videoReader.close();
  }

  @Override
  public boolean isScreencast() {
    return false;
  }
}
