/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.metadata.emsg;

import org.telegram.messenger.exoplayer2.metadata.Metadata;
import org.telegram.messenger.exoplayer2.metadata.MetadataDecoder;
import org.telegram.messenger.exoplayer2.metadata.MetadataInputBuffer;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Decodes Event Message (emsg) atoms, as defined in ISO 23009-1.
 * <p>
 * Atom data should be provided to the decoder without the full atom header (i.e. starting from the
 * first byte of the scheme_id_uri field).
 */
public final class EventMessageDecoder implements MetadataDecoder {

  @Override
  public Metadata decode(MetadataInputBuffer inputBuffer) {
    ByteBuffer buffer = inputBuffer.data;
    byte[] data = buffer.array();
    int size = buffer.limit();
    ParsableByteArray emsgData = new ParsableByteArray(data, size);
    String schemeIdUri = emsgData.readNullTerminatedString();
    String value = emsgData.readNullTerminatedString();
    long timescale = emsgData.readUnsignedInt();
    emsgData.skipBytes(4); // presentation_time_delta
    long durationMs = (emsgData.readUnsignedInt() * 1000) / timescale;
    long id = emsgData.readUnsignedInt();
    byte[] messageData = Arrays.copyOfRange(data, emsgData.getPosition(), size);
    return new Metadata(new EventMessage(schemeIdUri, value, durationMs, id, messageData));
  }

}
