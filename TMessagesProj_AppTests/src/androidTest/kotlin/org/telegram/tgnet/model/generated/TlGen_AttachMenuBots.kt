package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AttachMenuBots : TlGen_Object {
  public data object TL_attachMenuBotsNotModified : TlGen_AttachMenuBots() {
    public const val MAGIC: UInt = 0xF1D88A5CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_attachMenuBots(
    public val hash: Long,
    public val bots: List<TlGen_AttachMenuBot>,
    public val users: List<TlGen_User>,
  ) : TlGen_AttachMenuBots() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, bots)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3C4301C0U
    }
  }
}
