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
package com.google.android.exoplayer2.extractor.ogg;

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.OpusUtil;
import com.google.android.exoplayer2.extractor.VorbisUtil;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;

/** {@link StreamReader} to extract Opus data out of Ogg byte stream. */
/* package */ final class OpusReader extends StreamReader {

  private static final byte[] OPUS_ID_HEADER_SIGNATURE = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};
  private static final byte[] OPUS_COMMENT_HEADER_SIGNATURE = {
    'O', 'p', 'u', 's', 'T', 'a', 'g', 's'
  };

  private boolean firstCommentHeaderSeen;

  public static boolean verifyBitstreamType(ParsableByteArray data) {
    return peekPacketStartsWith(data, OPUS_ID_HEADER_SIGNATURE);
  }

  @Override
  protected void reset(boolean headerData) {
    super.reset(headerData);
    if (headerData) {
      firstCommentHeaderSeen = false;
    }
  }

  @Override
  protected long preparePayload(ParsableByteArray packet) {
    return convertTimeToGranule(OpusUtil.getPacketDurationUs(packet.getData()));
  }

  @Override
  @EnsuresNonNullIf(expression = "#3.format", result = false)
  protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData)
      throws ParserException {
    if (peekPacketStartsWith(packet, OPUS_ID_HEADER_SIGNATURE)) {
      byte[] headerBytes = Arrays.copyOf(packet.getData(), packet.limit());
      int channelCount = OpusUtil.getChannelCount(headerBytes);
      List<byte[]> initializationData = OpusUtil.buildInitializationData(headerBytes);

      if (setupData.format != null) {
        // setupData.format being non-null indicates we've already seen an ID header. Multiple ID
        // headers are not permitted by the Opus spec [1], but have been observed in real files [2],
        // so we just ignore all subsequent ones.
        // [1] https://datatracker.ietf.org/doc/html/rfc7845#section-3 and
        //     https://datatracker.ietf.org/doc/html/rfc7845#section-5
        // [2] https://github.com/google/ExoPlayer/issues/10038
        return true;
      }
      setupData.format =
          new Format.Builder()
              .setSampleMimeType(MimeTypes.AUDIO_OPUS)
              .setChannelCount(channelCount)
              .setSampleRate(OpusUtil.SAMPLE_RATE)
              .setInitializationData(initializationData)
              .build();
      return true;
    } else if (peekPacketStartsWith(packet, OPUS_COMMENT_HEADER_SIGNATURE)) {
      // The comment header must come immediately after the ID header, so the format will already
      // be populated: https://datatracker.ietf.org/doc/html/rfc7845#section-3
      checkStateNotNull(setupData.format);
      if (firstCommentHeaderSeen) {
        // Multiple comment headers are not permitted by the Opus spec [1], but have been observed
        // in real files [2], so we just ignore all subsequent ones.
        // [1] https://datatracker.ietf.org/doc/html/rfc7845#section-3 and
        //     https://datatracker.ietf.org/doc/html/rfc7845#section-5
        // [2] https://github.com/google/ExoPlayer/issues/10038
        return true;
      }
      firstCommentHeaderSeen = true;
      packet.skipBytes(OPUS_COMMENT_HEADER_SIGNATURE.length);
      VorbisUtil.CommentHeader commentHeader =
          VorbisUtil.readVorbisCommentHeader(
              packet, /* hasMetadataHeader= */ false, /* hasFramingBit= */ false);
      @Nullable
      Metadata vorbisMetadata =
          VorbisUtil.parseVorbisComments(ImmutableList.copyOf(commentHeader.comments));
      if (vorbisMetadata == null) {
        return true;
      }
      setupData.format =
          setupData
              .format
              .buildUpon()
              .setMetadata(vorbisMetadata.copyWithAppendedEntriesFrom(setupData.format.metadata))
              .build();
      return true;
    } else {
      // The ID header must come at the start of the file, so the format must already be populated:
      // https://datatracker.ietf.org/doc/html/rfc7845#section-3
      checkStateNotNull(setupData.format);
      return false;
    }
  }

  /**
   * Returns true if the given {@link ParsableByteArray} starts with {@code expectedPrefix}. Does
   * not change the {@link ParsableByteArray#getPosition() position} of {@code packet}.
   *
   * @param packet The packet data.
   * @return True if the packet starts with {@code expectedPrefix}, false if not.
   */
  private static boolean peekPacketStartsWith(ParsableByteArray packet, byte[] expectedPrefix) {
    if (packet.bytesLeft() < expectedPrefix.length) {
      return false;
    }
    int startPosition = packet.getPosition();
    byte[] header = new byte[expectedPrefix.length];
    packet.readBytes(header, 0, expectedPrefix.length);
    packet.setPosition(startPosition);
    return Arrays.equals(header, expectedPrefix);
  }
}
