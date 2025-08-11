package org.telegram.tgnet.model

import org.telegram.tgnet.OutputSerializedData

public interface TlGen_Object {
    fun serializeToStream(stream: OutputSerializedData)
}