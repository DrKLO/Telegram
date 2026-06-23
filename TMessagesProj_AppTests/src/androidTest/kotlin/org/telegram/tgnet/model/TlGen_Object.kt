package org.Tajgram.tgnet.model

import org.Tajgram.tgnet.OutputSerializedData

public interface TlGen_Object {
    fun serializeToStream(stream: OutputSerializedData)
}