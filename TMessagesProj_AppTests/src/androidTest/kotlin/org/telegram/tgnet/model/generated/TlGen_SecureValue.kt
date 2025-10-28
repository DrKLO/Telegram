package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecureValue : TlGen_Object {
  public data class TL_secureValue(
    public val type: TlGen_SecureValueType,
    public val `data`: TlGen_SecureData?,
    public val front_side: TlGen_SecureFile?,
    public val reverse_side: TlGen_SecureFile?,
    public val selfie: TlGen_SecureFile?,
    public val translation: List<TlGen_SecureFile>?,
    public val files: List<TlGen_SecureFile>?,
    public val plain_data: TlGen_SecurePlainData?,
    public val hash: List<Byte>,
  ) : TlGen_SecureValue() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (data != null) result = result or 1U
        if (front_side != null) result = result or 2U
        if (reverse_side != null) result = result or 4U
        if (selfie != null) result = result or 8U
        if (files != null) result = result or 16U
        if (plain_data != null) result = result or 32U
        if (translation != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      type.serializeToStream(stream)
      data?.serializeToStream(stream)
      front_side?.serializeToStream(stream)
      reverse_side?.serializeToStream(stream)
      selfie?.serializeToStream(stream)
      translation?.let { TlGen_Vector.serialize(stream, it) }
      files?.let { TlGen_Vector.serialize(stream, it) }
      plain_data?.serializeToStream(stream)
      stream.writeByteArray(hash.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x187FA0CAU
    }
  }

  public data class TL_secureValue_layer84(
    public val type: TlGen_SecureValueType,
    public val `data`: TlGen_SecureData?,
    public val front_side: TlGen_SecureFile?,
    public val reverse_side: TlGen_SecureFile?,
    public val selfie: TlGen_SecureFile?,
    public val files: List<TlGen_SecureFile>?,
    public val plain_data: TlGen_SecurePlainData?,
    public val hash: List<Byte>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (data != null) result = result or 1U
        if (front_side != null) result = result or 2U
        if (reverse_side != null) result = result or 4U
        if (selfie != null) result = result or 8U
        if (files != null) result = result or 16U
        if (plain_data != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      type.serializeToStream(stream)
      data?.serializeToStream(stream)
      front_side?.serializeToStream(stream)
      reverse_side?.serializeToStream(stream)
      selfie?.serializeToStream(stream)
      files?.let { TlGen_Vector.serialize(stream, it) }
      plain_data?.serializeToStream(stream)
      stream.writeByteArray(hash.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xB4B4B699U
    }
  }
}
