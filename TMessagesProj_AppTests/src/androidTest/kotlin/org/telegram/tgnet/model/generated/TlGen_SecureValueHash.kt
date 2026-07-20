package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecureValueHash : TlGen_Object {
  public data class TL_secureValueHash(
    public val type: TlGen_SecureValueType,
    public val hash: List<Byte>,
  ) : TlGen_SecureValueHash() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      stream.writeByteArray(hash.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xED1ECDB0U
    }
  }
}
