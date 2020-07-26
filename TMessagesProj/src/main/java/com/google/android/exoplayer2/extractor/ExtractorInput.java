/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor;

import com.google.android.exoplayer2.C;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Provides data to be consumed by an {@link Extractor}.
 *
 * <p>This interface provides two modes of accessing the underlying input. See the subheadings below
 * for more info about each mode.
 *
 * <ul>
 *   <li>The {@code read()/peek()} and {@code skip()} methods provide {@link InputStream}-like
 *       byte-level access operations.
 *   <li>The {@code read/skip/peekFully()} and {@code advancePeekPosition()} methods assume the user
 *       wants to read an entire block/frame/header of known length.
 * </ul>
 *
 * <h3>{@link InputStream}-like methods</h3>
 *
 * <p>The {@code read()/peek()} and {@code skip()} methods provide {@link InputStream}-like
 * byte-level access operations. The {@code length} parameter is a maximum, and each method returns
 * the number of bytes actually processed. This may be less than {@code length} because the end of
 * the input was reached, or the method was interrupted, or the operation was aborted early for
 * another reason.
 *
 * <h3>Block-based methods</h3>
 *
 * <p>The {@code read/skip/peekFully()} and {@code advancePeekPosition()} methods assume the user
 * wants to read an entire block/frame/header of known length.
 *
 * <p>These methods all have a variant that takes a boolean {@code allowEndOfInput} parameter. This
 * parameter is intended to be set to true when the caller believes the input might be fully
 * exhausted before the call is made (i.e. they've previously read/skipped/peeked the final
 * block/frame/header). It's <b>not</b> intended to allow a partial read (i.e. greater than 0 bytes,
 * but less than {@code length}) to succeed - this will always throw an {@link EOFException} from
 * these methods (a partial read is assumed to indicate a malformed block/frame/header - and
 * therefore a malformed file).
 *
 * <p>The expected behaviour of the block-based methods is therefore:
 *
 * <ul>
 *   <li>Already at end-of-input and {@code allowEndOfInput=false}: Throw {@link EOFException}.
 *   <li>Already at end-of-input and {@code allowEndOfInput=true}: Return {@code false}.
 *   <li>Encounter end-of-input during read/skip/peek/advance: Throw {@link EOFException}
 *       (regardless of {@code allowEndOfInput}).
 * </ul>
 */
public interface ExtractorInput {

  /**
   * Reads up to {@code length} bytes from the input and resets the peek position.
   * <p>
   * This method blocks until at least one byte of data can be read, the end of the input is
   * detected, or an exception is thrown.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes to read from the input.
   * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the input has ended.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  int read(byte[] target, int offset, int length) throws IOException, InterruptedException;

  /**
   * Like {@link #read(byte[], int, int)}, but reads the requested {@code length} in full.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The number of bytes to read from the input.
   * @param allowEndOfInput True if encountering the end of the input having read no data is
   *     allowed, and should result in {@code false} being returned. False if it should be
   *     considered an error, causing an {@link EOFException} to be thrown. See note in class
   *     Javadoc.
   * @return True if the read was successful. False if {@code allowEndOfInput=true} and the end of
   *     the input was encountered having read no data.
   * @throws EOFException If the end of input was encountered having partially satisfied the read
   *     (i.e. having read at least one byte, but fewer than {@code length}), or if no bytes were
   *     read and {@code allowEndOfInput} is false.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException;

  /**
   * Equivalent to {@link #readFully(byte[], int, int, boolean) readFully(target, offset, length,
   * false)}.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The number of bytes to read from the input.
   * @throws EOFException If the end of input was encountered.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  void readFully(byte[] target, int offset, int length) throws IOException, InterruptedException;

  /**
   * Like {@link #read(byte[], int, int)}, except the data is skipped instead of read.
   *
   * @param length The maximum number of bytes to skip from the input.
   * @return The number of bytes skipped, or {@link C#RESULT_END_OF_INPUT} if the input has ended.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  int skip(int length) throws IOException, InterruptedException;

  /**
   * Like {@link #readFully(byte[], int, int, boolean)}, except the data is skipped instead of read.
   *
   * @param length The number of bytes to skip from the input.
   * @param allowEndOfInput True if encountering the end of the input having skipped no data is
   *     allowed, and should result in {@code false} being returned. False if it should be
   *     considered an error, causing an {@link EOFException} to be thrown. See note in class
   *     Javadoc.
   * @return True if the skip was successful. False if {@code allowEndOfInput=true} and the end of
   *     the input was encountered having skipped no data.
   * @throws EOFException If the end of input was encountered having partially satisfied the skip
   *     (i.e. having skipped at least one byte, but fewer than {@code length}), or if no bytes were
   *     skipped and {@code allowEndOfInput} is false.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  boolean skipFully(int length, boolean allowEndOfInput) throws IOException, InterruptedException;

  /**
   * Like {@link #readFully(byte[], int, int)}, except the data is skipped instead of read.
   * <p>
   * Encountering the end of input is always considered an error, and will result in an
   * {@link EOFException} being thrown.
   *
   * @param length The number of bytes to skip from the input.
   * @throws EOFException If the end of input was encountered.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  void skipFully(int length) throws IOException, InterruptedException;

  /**
   * Peeks up to {@code length} bytes from the peek position. The current read position is left
   * unchanged.
   *
   * <p>This method blocks until at least one byte of data can be peeked, the end of the input is
   * detected, or an exception is thrown.
   *
   * <p>Calling {@link #resetPeekPosition()} resets the peek position to equal the current read
   * position, so the caller can peek the same data again. Reading or skipping also resets the peek
   * position.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes to peek from the input.
   * @return The number of bytes peeked, or {@link C#RESULT_END_OF_INPUT} if the input has ended.
   * @throws IOException If an error occurs peeking from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  int peek(byte[] target, int offset, int length) throws IOException, InterruptedException;

  /**
   * Like {@link #peek(byte[], int, int)}, but peeks the requested {@code length} in full.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The number of bytes to peek from the input.
   * @param allowEndOfInput True if encountering the end of the input having peeked no data is
   *     allowed, and should result in {@code false} being returned. False if it should be
   *     considered an error, causing an {@link EOFException} to be thrown. See note in class
   *     Javadoc.
   * @return True if the peek was successful. False if {@code allowEndOfInput=true} and the end of
   *     the input was encountered having peeked no data.
   * @throws EOFException If the end of input was encountered having partially satisfied the peek
   *     (i.e. having peeked at least one byte, but fewer than {@code length}), or if no bytes were
   *     peeked and {@code allowEndOfInput} is false.
   * @throws IOException If an error occurs peeking from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException;

  /**
   * Equivalent to {@link #peekFully(byte[], int, int, boolean) peekFully(target, offset, length,
   * false)}.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The number of bytes to peek from the input.
   * @throws EOFException If the end of input was encountered.
   * @throws IOException If an error occurs peeking from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  void peekFully(byte[] target, int offset, int length) throws IOException, InterruptedException;

  /**
   * Advances the peek position by {@code length} bytes. Like {@link #peekFully(byte[], int, int,
   * boolean)} except the data is skipped instead of read.
   *
   * @param length The number of bytes by which to advance the peek position.
   * @param allowEndOfInput True if encountering the end of the input before advancing is allowed,
   *     and should result in {@code false} being returned. False if it should be considered an
   *     error, causing an {@link EOFException} to be thrown. See note in class Javadoc.
   * @return True if advancing the peek position was successful. False if {@code
   *     allowEndOfInput=true} and the end of the input was encountered before advancing over any
   *     data.
   * @throws EOFException If the end of input was encountered having partially advanced (i.e. having
   *     advanced by at least one byte, but fewer than {@code length}), or if the end of input was
   *     encountered before advancing and {@code allowEndOfInput} is false.
   * @throws IOException If an error occurs advancing the peek position.
   * @throws InterruptedException If the thread is interrupted.
   */
  boolean advancePeekPosition(int length, boolean allowEndOfInput)
      throws IOException, InterruptedException;

  /**
   * Advances the peek position by {@code length} bytes. Like {@link #peekFully(byte[], int, int)}
   * except the data is skipped instead of read.
   *
   * @param length The number of bytes to peek from the input.
   * @throws EOFException If the end of input was encountered.
   * @throws IOException If an error occurs peeking from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  void advancePeekPosition(int length) throws IOException, InterruptedException;

  /**
   * Resets the peek position to equal the current read position.
   */
  void resetPeekPosition();

  /**
   * Returns the current peek position (byte offset) in the stream.
   *
   * @return The peek position (byte offset) in the stream.
   */
  long getPeekPosition();

  /**
   * Returns the current read position (byte offset) in the stream.
   *
   * @return The read position (byte offset) in the stream.
   */
  long getPosition();

  /**
   * Returns the length of the source stream, or {@link C#LENGTH_UNSET} if it is unknown.
   *
   * @return The length of the source stream, or {@link C#LENGTH_UNSET}.
   */
  long getLength();

  /**
   * Called when reading fails and the required retry position is different from the last position.
   * After setting the retry position it throws the given {@link Throwable}.
   *
   * @param <E> Type of {@link Throwable} to be thrown.
   * @param position The required retry position.
   * @param e {@link Throwable} to be thrown.
   * @throws E The given {@link Throwable} object.
   */
  <E extends Throwable> void setRetryPosition(long position, E e) throws E;

}
