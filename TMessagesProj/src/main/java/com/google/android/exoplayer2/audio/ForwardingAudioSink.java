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

import android.media.AudioDeviceInfo;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.analytics.PlayerId;
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
  public void setPlayerId(@Nullable PlayerId playerId) {
    sink.setPlayerId(playerId);
  }

  @Override
  public boolean supportsFormat(Format format) {
    return sink.supportsFormat(format);
  }

  @Override
  public @SinkFormatSupport int getFormatSupport(Format format) {
    return sink.getFormatSupport(format);
  }

  @Override
  public long getCurrentPositionUs(boolean sourceEnded) {
    return sink.getCurrentPositionUs(sourceEnded);
  }

  @Override
  public void configure(Format inputFormat, int specifiedBufferSize, @Nullable int[] outputChannels)
      throws ConfigurationException {
    sink.configure(inputFormat, specifiedBufferSize, outputChannels);
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
  public boolean handleBuffer(
      ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
      throws InitializationException, WriteException {
    return sink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount);
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
  public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {
    sink.setSkipSilenceEnabled(skipSilenceEnabled);
  }

  @Override
  public boolean getSkipSilenceEnabled() {
    return sink.getSkipSilenceEnabled();
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    sink.setAudioAttributes(audioAttributes);
  }

  @Override
  @Nullable
  public AudioAttributes getAudioAttributes() {
    return sink.getAudioAttributes();
  }

  @Override
  public void setAudioSessionId(int audioSessionId) {
    sink.setAudioSessionId(audioSessionId);
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    sink.setAuxEffectInfo(auxEffectInfo);
  }

  @RequiresApi(23)
  @Override
  public void setPreferredDevice(@Nullable AudioDeviceInfo audioDeviceInfo) {
    sink.setPreferredDevice(audioDeviceInfo);
  }

  @Override
  public void setOutputStreamOffsetUs(long outputStreamOffsetUs) {
    sink.setOutputStreamOffsetUs(outputStreamOffsetUs);
  }

  @Override
  public void enableTunnelingV21() {
    sink.enableTunnelingV21();
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
  public void experimentalFlushWithoutAudioTrackRelease() {
    sink.experimentalFlushWithoutAudioTrackRelease();
  }

  @Override
  public void reset() {
    sink.reset();
  }
}
