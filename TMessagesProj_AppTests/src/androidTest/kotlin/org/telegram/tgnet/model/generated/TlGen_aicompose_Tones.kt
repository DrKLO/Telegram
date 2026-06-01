package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_aicompose_Tones : TlGen_Object {
  public data object TL_aicompose_tonesNotModified : TlGen_aicompose_Tones() {
    public const val MAGIC: UInt = 0xC1F46103U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_aicompose_tones(
    public val hash: Long,
    public val tones: List<TlGen_AiComposeTone>,
    public val users: List<TlGen_User>,
  ) : TlGen_aicompose_Tones() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, tones)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6C9D0EFEU
    }
  }
}
