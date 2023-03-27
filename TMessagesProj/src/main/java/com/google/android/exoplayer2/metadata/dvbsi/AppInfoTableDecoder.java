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
package com.google.android.exoplayer2.metadata.dvbsi;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.metadata.SimpleMetadataDecoder;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Decoder for the DVB Application Information Table (AIT).
 *
 * <p>For more info on the AIT see section 5.3.4 of the <a
 * href="https://www.etsi.org/deliver/etsi_ts/102800_102899/102809/01.01.01_60/ts_102809v010101p.pdf">
 * DVB ETSI TS 102 809 v1.1.1 spec</a>.
 */
public final class AppInfoTableDecoder extends SimpleMetadataDecoder {

  /** See section 5.3.6. */
  private static final int DESCRIPTOR_TRANSPORT_PROTOCOL = 0x02;
  /** See section 5.3.7. */
  private static final int DESCRIPTOR_SIMPLE_APPLICATION_LOCATION = 0x15;

  /** See table 29 in section 5.3.6. */
  private static final int TRANSPORT_PROTOCOL_HTTP = 3;

  /** See table 16 in section 5.3.4.6. */
  public static final int APPLICATION_INFORMATION_TABLE_ID = 0x74;

  @Override
  @Nullable
  @SuppressWarnings("ByteBufferBackingArray") // Buffer validated by SimpleMetadataDecoder.decode
  protected Metadata decode(MetadataInputBuffer inputBuffer, ByteBuffer buffer) {
    int tableId = buffer.get();
    return tableId == APPLICATION_INFORMATION_TABLE_ID
        ? parseAit(new ParsableBitArray(buffer.array(), buffer.limit()))
        : null;
  }

  @Nullable
  private static Metadata parseAit(ParsableBitArray sectionData) {
    // tableId, section_syntax_indication, reserved_future_use, reserved
    sectionData.skipBits(12);
    int sectionLength = sectionData.readBits(12);
    int endOfSection = sectionData.getBytePosition() + sectionLength - 4 /* Ignore leading CRC */;

    // test_application_flag, application_type, reserved, version_number, current_next_indicator,
    // section_number, last_section_number, reserved_future_use
    sectionData.skipBits(44);

    int commonDescriptorsLength = sectionData.readBits(12);

    // Since we currently only keep URL and control code, which are unique per application,
    // there is no useful information in common descriptor.
    sectionData.skipBytes(commonDescriptorsLength);

    // reserved_future_use, application_loop_length
    sectionData.skipBits(16);

    ArrayList<AppInfoTable> appInfoTables = new ArrayList<>();
    while (sectionData.getBytePosition() < endOfSection) {
      @Nullable String urlBase = null;
      @Nullable String urlExtension = null;

      // application_identifier
      sectionData.skipBits(48);

      int controlCode = sectionData.readBits(8);

      // reserved_future_use
      sectionData.skipBits(4);

      int applicationDescriptorsLoopLength = sectionData.readBits(12);
      int positionOfNextApplication =
          sectionData.getBytePosition() + applicationDescriptorsLoopLength;
      while (sectionData.getBytePosition() < positionOfNextApplication) {
        int descriptorTag = sectionData.readBits(8);
        int descriptorLength = sectionData.readBits(8);
        int positionOfNextDescriptor = sectionData.getBytePosition() + descriptorLength;

        if (descriptorTag == DESCRIPTOR_TRANSPORT_PROTOCOL) {
          // See section 5.3.6.
          int protocolId = sectionData.readBits(16);
          // label
          sectionData.skipBits(8);

          if (protocolId == TRANSPORT_PROTOCOL_HTTP) {
            // See section 5.3.6.2.
            while (sectionData.getBytePosition() < positionOfNextDescriptor) {
              int urlBaseLength = sectionData.readBits(8);
              urlBase = sectionData.readBytesAsString(urlBaseLength, Charsets.US_ASCII);

              int extensionCount = sectionData.readBits(8);
              for (int urlExtensionIndex = 0;
                  urlExtensionIndex < extensionCount;
                  urlExtensionIndex++) {
                int urlExtensionLength = sectionData.readBits(8);
                sectionData.skipBytes(urlExtensionLength);
              }
            }
          }
        } else if (descriptorTag == DESCRIPTOR_SIMPLE_APPLICATION_LOCATION) {
          // See section 5.3.7.
          urlExtension = sectionData.readBytesAsString(descriptorLength, Charsets.US_ASCII);
        }

        sectionData.setPosition(positionOfNextDescriptor * 8);
      }

      sectionData.setPosition(positionOfNextApplication * 8);

      if (urlBase != null && urlExtension != null) {
        appInfoTables.add(new AppInfoTable(controlCode, urlBase + urlExtension));
      }
    }

    return appInfoTables.isEmpty() ? null : new Metadata(appInfoTables);
  }
}
