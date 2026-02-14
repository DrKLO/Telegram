package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_EmojiGameOutcome : TlGen_Object {
  public data class TL_messages_emojiGameOutcome(
    public val seed: List<Byte>,
    public val stake_ton_amount: Long,
    public val ton_amount: Long,
  ) : TlGen_messages_EmojiGameOutcome() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(seed.toByteArray())
      stream.writeInt64(stake_ton_amount)
      stream.writeInt64(ton_amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDA2AD647U
    }
  }
}
