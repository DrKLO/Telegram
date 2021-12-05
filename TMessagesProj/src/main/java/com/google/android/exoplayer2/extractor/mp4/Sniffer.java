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
package com.google.android.exoplayer2.extractor.mp4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

/**
 * Provides methods that peek data from an {@link ExtractorInput} and return whether the input
 * appears to be in MP4 format.
 */
/* package */ final class Sniffer {

  /** The maximum number of bytes to peek when sniffing. */
  private static final int SEARCH_LENGTH = 4 * 1024;

  private static final int[] COMPATIBLE_BRANDS =
      new int[] {
        0x69736f6d, // isom
        0x69736f32, // iso2
        0x69736f33, // iso3
        0x69736f34, // iso4
        0x69736f35, // iso5
        0x69736f36, // iso6
        0x61766331, // avc1
        0x68766331, // hvc1
        0x68657631, // hev1
        0x61763031, // av01
        0x6d703431, // mp41
        0x6d703432, // mp42
        0x33673261, // 3g2a
        0x33673262, // 3g2b
        0x33677236, // 3gr6
        0x33677336, // 3gs6
        0x33676536, // 3ge6
        0x33676736, // 3gg6
        0x4d345620, // M4V[space]
        0x4d344120, // M4A[space]
        0x66347620, // f4v[space]
        0x6b646469, // kddi
        0x4d345650, // M4VP
        0x71742020, // qt[space][space], Apple QuickTime
        0x4d534e56, // MSNV, Sony PSP
        0x64627931, // dby1, Dolby Vision
      };

  /**
   * Returns whether data peeked from the current position in {@code input} is consistent with the
   * input being a fragmented MP4 file.
   *
   * @param input The extractor input from which to peek data. The peek position will be modified.
   * @return Whether the input appears to be in the fragmented MP4 format.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  public static boolean sniffFragmented(ExtractorInput input)
      throws IOException, InterruptedException {
    return sniffInternal(input, true);
  }

  /**
   * Returns whether data peeked from the current position in {@code input} is consistent with the
   * input being an unfragmented MP4 file.
   *
   * @param input The extractor input from which to peek data. The peek position will be modified.
   * @return Whether the input appears to be in the unfragmented MP4 format.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  public static boolean sniffUnfragmented(ExtractorInput input)
      throws IOException, InterruptedException {
    return sniffInternal(input, false);
  }

  private static boolean sniffInternal(ExtractorInput input, boolean fragmented)
      throws IOException, InterruptedException {
    long inputLength = input.getLength();
    int bytesToSearch = (int) (inputLength == C.LENGTH_UNSET || inputLength > SEARCH_LENGTH
        ? SEARCH_LENGTH : inputLength);

    ParsableByteArray buffer = new ParsableByteArray(64);
    int bytesSearched = 0;
    boolean foundGoodFileType = false;
    boolean isFragmented = false;
    while (bytesSearched < bytesToSearch) {
      // Read an atom header.
      int headerSize = Atom.HEADER_SIZE;
      buffer.reset(headerSize);
      input.peekFully(buffer.data, 0, headerSize);
      long atomSize = buffer.readUnsignedInt();
      int atomType = buffer.readInt();
      if (atomSize == Atom.DEFINES_LARGE_SIZE) {
        // Read the large atom size.
        headerSize = Atom.LONG_HEADER_SIZE;
        input.peekFully(buffer.data, Atom.HEADER_SIZE, Atom.LONG_HEADER_SIZE - Atom.HEADER_SIZE);
        buffer.setLimit(Atom.LONG_HEADER_SIZE);
        atomSize = buffer.readLong();
      } else if (atomSize == Atom.EXTENDS_TO_END_SIZE) {
        // The atom extends to the end of the file.
        long fileEndPosition = input.getLength();
        if (fileEndPosition != C.LENGTH_UNSET) {
          atomSize = fileEndPosition - input.getPeekPosition() + headerSize;
        }
      }

      if (atomSize < headerSize) {
        // The file is invalid because the atom size is too small for its header.
        return false;
      }
      bytesSearched += headerSize;

      if (atomType == Atom.TYPE_moov) {
        // We have seen the moov atom. We increase the search size to make sure we don't miss an
        // mvex atom because the moov's size exceeds the search length.
        bytesToSearch += (int) atomSize;
        if (inputLength != C.LENGTH_UNSET && bytesToSearch > inputLength) {
          // Make sure we don't exceed the file size.
          bytesToSearch = (int) inputLength;
        }
        // Check for an mvex atom inside the moov atom to identify whether the file is fragmented.
        continue;
      }

      if (atomType == Atom.TYPE_moof || atomType == Atom.TYPE_mvex) {
        // The movie is fragmented. Stop searching as we must have read any ftyp atom already.
        isFragmented = true;
        break;
      }

      if (bytesSearched + atomSize - headerSize >= bytesToSearch) {
        // Stop searching as peeking this atom would exceed the search limit.
        break;
      }

      int atomDataSize = (int) (atomSize - headerSize);
      bytesSearched += atomDataSize;
      if (atomType == Atom.TYPE_ftyp) {
        // Parse the atom and check the file type/brand is compatible with the extractors.
        if (atomDataSize < 8) {
          return false;
        }
        buffer.reset(atomDataSize);
        input.peekFully(buffer.data, 0, atomDataSize);
        int brandsCount = atomDataSize / 4;
        for (int i = 0; i < brandsCount; i++) {
          if (i == 1) {
            // This index refers to the minorVersion, not a brand, so skip it.
            buffer.skipBytes(4);
          } else if (isCompatibleBrand(buffer.readInt())) {
            foundGoodFileType = true;
            break;
          }
        }
        if (!foundGoodFileType) {
          // The types were not compatible and there is only one ftyp atom, so reject the file.
          return false;
        }
      } else if (atomDataSize != 0) {
        // Skip the atom.
        input.advancePeekPosition(atomDataSize);
      }
    }
    return foundGoodFileType && fragmented == isFragmented;
  }

  /**
   * Returns whether {@code brand} is an ftyp atom brand that is compatible with the MP4 extractors.
   */
  private static boolean isCompatibleBrand(int brand) {
    // Accept all brands starting '3gp'.
    if (brand >>> 8 == 0x00336770) {
      return true;
    }
    for (int compatibleBrand : COMPATIBLE_BRANDS) {
      if (compatibleBrand == brand) {
        return true;
      }
    }
    return false;
  }

  private Sniffer() {
    // Prevent instantiation.
  }

}
