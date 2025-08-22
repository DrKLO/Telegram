package org.telegram.tgnet.model

import org.telegram.messenger.Utilities
import org.telegram.tgnet.OutputSerializedData


public object TlGen_Vector {
    public const val MAGIC: UInt = 0x1CB5C415U

    fun <T> serialize(stream: OutputSerializedData, write: Utilities.Callback<T>, objects: List<T>) {
        stream.writeInt32(MAGIC.toInt())
        stream.writeInt32(objects.size)
        for (obj in objects) {
            write.run(obj)
        }
    }

    fun serializeInt(stream: OutputSerializedData, objects: List<Int>) {
        serialize(stream, stream::writeInt32, objects)
    }

    fun serializeLong(stream: OutputSerializedData, objects: List<Long>) {
        serialize(stream, stream::writeInt64, objects)
    }

    fun serializeString(stream: OutputSerializedData, objects: List<String>) {
        serialize(stream, stream::writeString, objects)
    }

    fun serializeDouble(stream: OutputSerializedData, objects: List<Double>) {
        serialize(stream, stream::writeDouble, objects)
    }

    fun serializeBytes(stream: OutputSerializedData, objects: List<List<Byte>>) {
        serialize(stream, stream::writeByteArray, objects.map { it.toByteArray() })
    }

    fun <T : TlGen_Object> serialize(stream: OutputSerializedData, objects: List<T>) {
        stream.writeInt32(MAGIC.toInt())
        stream.writeInt32(objects.size)
        for (obj in objects) {
            obj.serializeToStream(stream)
        }
    }
}