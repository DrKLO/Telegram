/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.PlaybackParameters;
import java.nio.ByteBuffer;

/** An overridable {@link AudioSink} implementation forwarding all methods to another sink. */
public class ForwardingAudioSink implements AudioSink {

  private final AudioSink sink;

  public ForwardingAudioSink(AudioSink sink) {
    this.sink = sink;
  }

  @Override
  public void setListener(Listener listener) {
    sink.setListener(listener);
  }

  @Override
  public boolean supportsOutput(int channelCount, int encoding) {
    return sink.supportsOutput(channelCount, encoding);
  }

  @Override
  public long getCurrentPositionUs(boolean sourceEnded) {
    return sink.getCurrentPositionUs(sourceEnded);
  }

  @Override
  public void configure(
      int inputEncoding,
      int inputChannelCount,
      int inputSampleRate,
      int specifiedBufferSize,
      @Nullable int[] outputChannels,
      int trimStartFrames,
      int trimEndFrames)
      throws ConfigurationException {
    sink.configure(
        inputEncoding,
        inputChannelCount,
        inputSampleRate,
        specifiedBufferSize,
        outputChannels,
        trimStartFrames,
        trimEndFrames);
  }

  @Override
  public void play() {
    sink.play();
  }

  @Override
  public void handleDiscontinuity() {
    sink.handleDiscontinuity();
  }

  @Override
  public boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs)
      throws InitializationException, WriteException {
    return sink.handleBuffer(buffer, presentationTimeUs);
  }

  @Override
  public void playToEndOfStream() throws WriteException {
    sink.playToEndOfStream();
  }

  @Override
  public boolean isEnded() {
    return sink.isEnded();
  }

  @Override
  public boolean hasPendingData() {
    return sink.hasPendingData();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    sink.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return sink.getPlaybackParameters();
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    sink.setAudioAttributes(audioAttributes);
  }

  @Override
  public void setAudioSessionId(int audioSessionId) {
    sink.setAudioSessionId(audioSessionId);
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    sink.setAuxEffectInfo(auxEffectInfo);
  }

  @Override
  public void enableTunnelingV21(int tunnelingAudioSessionId) {
    sink.enableTunnelingV21(tunnelingAudioSessionId);
  }

  @Override
  public void disableTunneling() {
    sink.disableTunneling();
  }

  @Override
  public void setVolume(float volume) {
    sink.setVolume(volume);
  }

  @Override
  public void pause() {
    sink.pause();
  }

  @Override
  public void flush() {
    sink.flush();
  }

  @Override
  public void reset() {
    sink.reset();
  }
}
