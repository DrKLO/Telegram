package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AttachMenuBotsBot : TlGen_Object {
  public data class TL_attachMenuBotsBot(
    public val bot: TlGen_AttachMenuBot,
    public val users: List<TlGen_User>,
  ) : TlGen_AttachMenuBotsBot() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      bot.serializeToStream(stream)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x93BF667FU
    }
  }
}
