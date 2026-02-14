package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_EmojiGameInfo : TlGen_Object {
  public data object TL_messages_emojiGameUnavailable : TlGen_messages_EmojiGameInfo() {
    public const val MAGIC: UInt = 0x59E65335U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_emojiGameDiceInfo(
    public val game_hash: String,
    public val prev_stake: Long,
    public val current_streak: Int,
    public val params: List<Int>,
    public val plays_left: Int?,
  ) : TlGen_messages_EmojiGameInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (plays_left != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(game_hash)
      stream.writeInt64(prev_stake)
      stream.writeInt32(current_streak)
      TlGen_Vector.serializeInt(stream, params)
      plays_left?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x44E56023U
    }
  }
}
