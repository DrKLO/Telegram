package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecureRequiredType : TlGen_Object {
  public data class TL_secureRequiredType(
    public val native_names: Boolean,
    public val selfie_required: Boolean,
    public val translation_required: Boolean,
    public val type: TlGen_SecureValueType,
  ) : TlGen_SecureRequiredType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (native_names) result = result or 1U
        if (selfie_required) result = result or 2U
        if (translation_required) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      type.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x829D99DAU
    }
  }

  public data class TL_secureRequiredTypeOneOf(
    public val types: List<TlGen_SecureRequiredType>,
  ) : TlGen_SecureRequiredType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, types)
    }

    public companion object {
      public const val MAGIC: UInt = 0x027477B4U
    }
  }
}
