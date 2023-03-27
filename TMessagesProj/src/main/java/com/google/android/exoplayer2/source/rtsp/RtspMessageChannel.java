/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.source.rtsp.RtspMessageUtil.isRtspStartLine;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.common.base.Ascii;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Sends and receives RTSP messages. */
/* package */ final class RtspMessageChannel implements Closeable {

  /** RTSP uses UTF-8 (RFC2326 Section 1.1). */
  public static final Charset CHARSET = Charsets.UTF_8;

  private static final String TAG = "RtspMessageChannel";

  /** A listener for received RTSP messages and possible failures. */
  public interface MessageListener {

    /**
     * Called when an RTSP message is received.
     *
     * @param message The non-empty list of received lines, with line terminators removed.
     */
    void onRtspMessageReceived(List<String> message);

    /**
     * Called when failed to send an RTSP message.
     *
     * @param message The list of lines making up the RTSP message that is failed to send.
     * @param e The thrown {@link Exception}.
     */
    default void onSendingFailed(List<String> message, Exception e) {}

    /**
     * Called when failed to receive an RTSP message.
     *
     * @param e The thrown {@link Exception}.
     */
    default void onReceivingFailed(Exception e) {}
  }

  /** A listener for received interleaved binary data from RTSP. */
  public interface InterleavedBinaryDataListener {

    /**
     * Called when interleaved binary data is received on RTSP.
     *
     * @param data The received binary data. The byte array will not be reused by {@link
     *     RtspMessageChannel}, and will always be full.
     */
    void onInterleavedBinaryDataReceived(byte[] data);
  }

  /**
   * The IANA-registered default port for RTSP. See <a
   * href="https://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xhtml">here</a>
   */
  public static final int DEFAULT_RTSP_PORT = 554;

  private final MessageListener messageListener;
  private final Loader receiverLoader;
  private final Map<Integer, InterleavedBinaryDataListener> interleavedBinaryDataListeners;
  private @MonotonicNonNull Sender sender;
  private @MonotonicNonNull Socket socket;

  private volatile boolean closed;

  /**
   * Constructs a new instance.
   *
   * <p>A connected {@link Socket} must be provided in {@link #open} in order to send and receive
   * RTSP messages. {@link #close} must be called when done, which would also close the socket.
   *
   * <p>{@link MessageListener} and {@link InterleavedBinaryDataListener} implementations must not
   * make assumptions about which thread called their listener methods; and must be thread-safe.
   *
   * <p>Note: all method invocations must be made from the thread on which this class is created.
   *
   * @param messageListener The {@link MessageListener} to receive events.
   */
  public RtspMessageChannel(MessageListener messageListener) {
    this.messageListener = messageListener;
    this.receiverLoader = new Loader("ExoPlayer:RtspMessageChannel:ReceiverLoader");
    this.interleavedBinaryDataListeners = Collections.synchronizedMap(new HashMap<>());
  }

  /**
   * Opens the message channel to send and receive RTSP messages.
   *
   * <p>Note: If an {@link IOException} is thrown, callers must still call {@link #close()} to
   * ensure that any partial effects of the invocation are cleaned up.
   *
   * @param socket A connected {@link Socket}.
   */
  public void open(Socket socket) throws IOException {
    this.socket = socket;
    sender = new Sender(socket.getOutputStream());

    receiverLoader.startLoading(
        new Receiver(socket.getInputStream()),
        new LoaderCallbackImpl(),
        /* defaultMinRetryCount= */ 0);
  }

  /**
   * Closes the RTSP message channel.
   *
   * <p>The closed instance must not be re-opened again. The {@link MessageListener} will not
   * receive further messages after closing.
   *
   * @throws IOException If an error occurs closing the message channel.
   */
  @Override
  public void close() throws IOException {
    // TODO(internal b/172331505) Make sure most resources are closed before throwing, and close()
    // can be called again to close the resources that are still open.
    if (closed) {
      return;
    }
    try {
      if (sender != null) {
        sender.close();
      }
      receiverLoader.release();

      if (socket != null) {
        socket.close();
      }
    } finally {
      closed = true;
    }
  }

  /**
   * Sends a serialized RTSP message.
   *
   * @param message The list of strings representing the serialized RTSP message.
   */
  public void send(List<String> message) {
    checkStateNotNull(sender);
    sender.send(message);
  }

  /**
   * Registers an {@link InterleavedBinaryDataListener} to receive RTSP interleaved data.
   *
   * <p>The listener method {@link InterleavedBinaryDataListener#onInterleavedBinaryDataReceived} is
   * called on {@link RtspMessageChannel}'s internal thread for receiving RTSP messages.
   */
  public void registerInterleavedBinaryDataListener(
      int channel, InterleavedBinaryDataListener listener) {
    interleavedBinaryDataListeners.put(channel, listener);
  }

  private final class Sender implements Closeable {

    private final OutputStream outputStream;
    private final HandlerThread senderThread;
    private final Handler senderThreadHandler;

    /**
     * Creates a new instance.
     *
     * @param outputStream The {@link OutputStream} of the opened RTSP {@link Socket}, to which the
     *     request is sent. The caller needs to close the {@link OutputStream}.
     */
    public Sender(OutputStream outputStream) {
      this.outputStream = outputStream;
      this.senderThread = new HandlerThread("ExoPlayer:RtspMessageChannel:Sender");
      this.senderThread.start();
      this.senderThreadHandler = new Handler(this.senderThread.getLooper());
    }

    /**
     * Sends out RTSP messages that are in the forms of lists of strings.
     *
     * <p>If {@link Exception} is thrown while sending, the message {@link
     * MessageListener#onSendingFailed} is dispatched to the thread that created the {@link
     * RtspMessageChannel}.
     *
     * @param message The must of strings representing the serialized RTSP message.
     */
    public void send(List<String> message) {
      byte[] data = RtspMessageUtil.convertMessageToByteArray(message);
      senderThreadHandler.post(
          () -> {
            try {
              outputStream.write(data);
            } catch (Exception e) {
              if (!closed) {
                messageListener.onSendingFailed(message, e);
              }
            }
          });
    }

    @Override
    public void close() {
      senderThreadHandler.post(senderThread::quit);
      try {
        // Waits until all the messages posted to the sender thread are handled.
        senderThread.join();
      } catch (InterruptedException e) {
        senderThread.interrupt();
      }
    }
  }

  /** A {@link Loadable} for receiving RTSP responses. */
  private final class Receiver implements Loadable {

    /** ASCII dollar encapsulates the RTP packets in interleaved mode (RFC2326 Section 10.12). */
    private static final byte INTERLEAVED_MESSAGE_MARKER = '$';

    private final DataInputStream dataInputStream;
    private final MessageParser messageParser;
    private volatile boolean loadCanceled;

    /**
     * Creates a new instance.
     *
     * @param inputStream The {@link InputStream} of the opened RTSP {@link Socket}, from which the
     *     {@link RtspResponse RtspResponses} are received. The caller needs to close the {@link
     *     InputStream}.
     */
    public Receiver(InputStream inputStream) {
      dataInputStream = new DataInputStream(inputStream);
      messageParser = new MessageParser();
    }

    @Override
    public void cancelLoad() {
      loadCanceled = true;
    }

    @Override
    public void load() throws IOException {
      while (!loadCanceled) {
        byte firstByte = dataInputStream.readByte();
        if (firstByte == INTERLEAVED_MESSAGE_MARKER) {
          handleInterleavedBinaryData();
        } else {
          handleRtspMessage(firstByte);
        }
      }
    }

    /** Handles an entire RTSP message. */
    private void handleRtspMessage(byte firstByte) throws IOException {
      if (!closed) {
        messageListener.onRtspMessageReceived(messageParser.parseNext(firstByte, dataInputStream));
      }
    }

    private void handleInterleavedBinaryData() throws IOException {
      int channel = dataInputStream.readUnsignedByte();
      int size = dataInputStream.readUnsignedShort();
      byte[] data = new byte[size];
      dataInputStream.readFully(data, /* off= */ 0, size);

      @Nullable
      InterleavedBinaryDataListener listener = interleavedBinaryDataListeners.get(channel);
      if (listener != null && !closed) {
        listener.onInterleavedBinaryDataReceived(data);
      }
    }
  }

  private final class LoaderCallbackImpl implements Loader.Callback<Receiver> {
    @Override
    public void onLoadCompleted(Receiver loadable, long elapsedRealtimeMs, long loadDurationMs) {}

    @Override
    public void onLoadCanceled(
        Receiver loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {}

    @Override
    public LoadErrorAction onLoadError(
        Receiver loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      if (!closed) {
        messageListener.onReceivingFailed(error);
      }
      return Loader.DONT_RETRY;
    }
  }

  /** Processes RTSP messages line-by-line. */
  private static final class MessageParser {

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({STATE_READING_FIRST_LINE, STATE_READING_HEADER, STATE_READING_BODY})
    @interface ReadingState {}

    private static final int STATE_READING_FIRST_LINE = 1;
    private static final int STATE_READING_HEADER = 2;
    private static final int STATE_READING_BODY = 3;

    private final List<String> messageLines;

    private @ReadingState int state;
    private long messageBodyLength;

    /** Creates a new instance. */
    public MessageParser() {
      messageLines = new ArrayList<>();
      state = STATE_READING_FIRST_LINE;
    }

    /**
     * Receives and parses an entire RTSP message.
     *
     * @param firstByte The first byte received for the RTSP message.
     * @param dataInputStream The {@link DataInputStream} on which RTSP messages are received.
     * @return An {@link ImmutableList} of the lines that make up an RTSP message.
     */
    public ImmutableList<String> parseNext(byte firstByte, DataInputStream dataInputStream)
        throws IOException {
      @Nullable
      ImmutableList<String> parsedMessageLines =
          addMessageLine(parseNextLine(firstByte, dataInputStream));

      while (parsedMessageLines == null) {
        if (state == STATE_READING_BODY) {
          if (messageBodyLength > 0) {
            // Message body's format is not regulated under RTSP, so it could use LF (instead of
            // RTSP's CRLF) as line ending. The length of the message body is included in the RTSP
            // Content-Length header.
            // Assume the message body length is within a 32-bit integer.
            int messageBodyLengthInt = Ints.checkedCast(messageBodyLength);
            checkState(messageBodyLengthInt != C.LENGTH_UNSET);
            byte[] messageBodyBytes = new byte[messageBodyLengthInt];
            dataInputStream.readFully(messageBodyBytes, /* off= */ 0, messageBodyLengthInt);
            parsedMessageLines = addMessageBody(messageBodyBytes);
          } else {
            throw new IllegalStateException("Expects a greater than zero Content-Length.");
          }
        } else {
          parsedMessageLines =
              addMessageLine(parseNextLine(dataInputStream.readByte(), dataInputStream));
        }
      }
      return parsedMessageLines;
    }

    /** Returns the byte representation of a complete RTSP line, with CRLF line terminator. */
    private static byte[] parseNextLine(byte firstByte, DataInputStream dataInputStream)
        throws IOException {
      ByteArrayOutputStream messageByteStream = new ByteArrayOutputStream();

      byte[] peekedBytes = new byte[2];
      peekedBytes[0] = firstByte;
      peekedBytes[1] = dataInputStream.readByte();
      messageByteStream.write(peekedBytes);

      while (peekedBytes[0] != Ascii.CR || peekedBytes[1] != Ascii.LF) {
        // Shift the CRLF buffer.
        peekedBytes[0] = peekedBytes[1];
        peekedBytes[1] = dataInputStream.readByte();
        messageByteStream.write(peekedBytes[1]);
      }

      return messageByteStream.toByteArray();
    }

    /**
     * Returns a list of completed RTSP message lines, without the CRLF line terminators; or {@code
     * null} if the message is not yet complete.
     */
    @Nullable
    private ImmutableList<String> addMessageLine(byte[] lineBytes) throws ParserException {
      // Trim CRLF. RTSP lists are terminated by a CRLF.
      checkArgument(
          lineBytes.length >= 2
              && lineBytes[lineBytes.length - 2] == Ascii.CR
              && lineBytes[lineBytes.length - 1] == Ascii.LF);
      String line =
          new String(lineBytes, /* offset= */ 0, /* length= */ lineBytes.length - 2, CHARSET);
      messageLines.add(line);

      switch (state) {
        case STATE_READING_FIRST_LINE:
          if (isRtspStartLine(line)) {
            state = STATE_READING_HEADER;
          }
          break;

        case STATE_READING_HEADER:
          // Check if the line contains RTSP Content-Length header.
          long contentLength = RtspMessageUtil.parseContentLengthHeader(line);
          if (contentLength != C.LENGTH_UNSET) {
            messageBodyLength = contentLength;
          }

          if (line.isEmpty()) {
            // An empty line signals the end of the header section.
            if (messageBodyLength > 0) {
              state = STATE_READING_BODY;
            } else {
              ImmutableList<String> linesToReturn = ImmutableList.copyOf(messageLines);
              reset();
              return linesToReturn;
            }
          }
          break;

        case STATE_READING_BODY:
          // Message body must be handled by addMessageBody().

        default:
          throw new IllegalStateException();
      }
      return null;
    }

    /** Returns a list of completed RTSP message lines, without the line terminators. */
    private ImmutableList<String> addMessageBody(byte[] messageBodyBytes) {
      checkState(state == STATE_READING_BODY);

      String messageBody;
      if (messageBodyBytes.length > 0
          && messageBodyBytes[messageBodyBytes.length - 1] == Ascii.LF) {
        if (messageBodyBytes.length > 1
            && messageBodyBytes[messageBodyBytes.length - 2] == Ascii.CR) {
          // Line ends with CRLF.
          messageBody =
              new String(
                  messageBodyBytes,
                  /* offset= */ 0,
                  /* length= */ messageBodyBytes.length - 2,
                  CHARSET);
        } else {
          // Line ends with LF.
          messageBody =
              new String(
                  messageBodyBytes,
                  /* offset= */ 0,
                  /* length= */ messageBodyBytes.length - 1,
                  CHARSET);
        }
      } else {
        throw new IllegalArgumentException("Message body is empty or does not end with a LF.");
      }

      messageLines.add(messageBody);
      ImmutableList<String> linesToReturn = ImmutableList.copyOf(messageLines);
      reset();
      return linesToReturn;
    }

    private void reset() {
      messageLines.clear();
      state = STATE_READING_FIRST_LINE;
      messageBodyLength = 0;
    }
  }
}
