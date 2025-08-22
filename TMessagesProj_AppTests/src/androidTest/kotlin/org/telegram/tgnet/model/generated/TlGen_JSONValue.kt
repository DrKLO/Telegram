package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_JSONValue : TlGen_Object {
  public data object TL_jsonNull : TlGen_JSONValue() {
    public const val MAGIC: UInt = 0x3F6D7B68U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_jsonBool(
    public val `value`: Boolean,
  ) : TlGen_JSONValue() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(value)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC7345E6AU
    }
  }

  public data class TL_jsonNumber(
    public val `value`: Double,
  ) : TlGen_JSONValue() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeDouble(value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2BE0DFA4U
    }
  }

  public data class TL_jsonString(
    public val `value`: String,
  ) : TlGen_JSONValue() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(value)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB71E767AU
    }
  }

  public data class TL_jsonArray(
    public val `value`: List<TlGen_JSONValue>,
  ) : TlGen_JSONValue() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, value)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF7444763U
    }
  }

  public data class TL_jsonObject(
    public val `value`: List<TlGen_JSONObjectValue>,
  ) : TlGen_JSONValue() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x99C1D49DU
    }
  }
}
