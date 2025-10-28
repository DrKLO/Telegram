package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_LangPackString : TlGen_Object {
  public data class TL_langPackString(
    public val key: String,
    public val `value`: String,
  ) : TlGen_LangPackString() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(key)
      stream.writeString(value)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCAD181F6U
    }
  }

  public data class TL_langPackStringPluralized(
    public val key: String,
    public val zero_value: String?,
    public val one_value: String?,
    public val two_value: String?,
    public val few_value: String?,
    public val many_value: String?,
    public val other_value: String,
  ) : TlGen_LangPackString() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (zero_value != null) result = result or 1U
        if (one_value != null) result = result or 2U
        if (two_value != null) result = result or 4U
        if (few_value != null) result = result or 8U
        if (many_value != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(key)
      zero_value?.let { stream.writeString(it) }
      one_value?.let { stream.writeString(it) }
      two_value?.let { stream.writeString(it) }
      few_value?.let { stream.writeString(it) }
      many_value?.let { stream.writeString(it) }
      stream.writeString(other_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6C47AC9FU
    }
  }

  public data class TL_langPackStringDeleted(
    public val key: String,
  ) : TlGen_LangPackString() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(key)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2979EEB2U
    }
  }
}
