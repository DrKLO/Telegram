package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BotVerification : TlGen_Object {
  public data class TL_botVerification(
    public val bot_id: Long,
    public val icon: Long,
    public val description: String,
  ) : TlGen_BotVerification() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(bot_id)
      stream.writeInt64(icon)
      stream.writeString(description)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF93CD45CU
    }
  }
}
