package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputSecureValue : TlGen_Object {
  public data class TL_inputSecureValue(
    public val type: TlGen_SecureValueType,
    public val `data`: TlGen_SecureData?,
    public val front_side: TlGen_InputSecureFile?,
    public val reverse_side: TlGen_InputSecureFile?,
    public val selfie: TlGen_InputSecureFile?,
    public val translation: List<TlGen_InputSecureFile>?,
    public val files: List<TlGen_InputSecureFile>?,
    public val plain_data: TlGen_SecurePlainData?,
  ) : TlGen_InputSecureValue() {
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
    }

    public companion object {
      public const val MAGIC: UInt = 0xDB21D0A7U
    }
  }
}
