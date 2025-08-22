package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputCheckPasswordSRP : TlGen_Object {
  public data object TL_inputCheckPasswordEmpty : TlGen_InputCheckPasswordSRP() {
    public const val MAGIC: UInt = 0x9880F658U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputCheckPasswordSRP(
    public val srp_id: Long,
    public val A: List<Byte>,
    public val M1: List<Byte>,
  ) : TlGen_InputCheckPasswordSRP() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(srp_id)
      stream.writeByteArray(A.toByteArray())
      stream.writeByteArray(M1.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xD27FF082U
    }
  }
}
