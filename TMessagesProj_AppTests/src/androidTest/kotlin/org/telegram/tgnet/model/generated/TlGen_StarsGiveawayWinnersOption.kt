package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarsGiveawayWinnersOption : TlGen_Object {
  public data class TL_starsGiveawayWinnersOption(
    public val default: Boolean,
    public val users: Int,
    public val per_user_stars: Long,
  ) : TlGen_StarsGiveawayWinnersOption() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (default) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(users)
      stream.writeInt64(per_user_stars)
    }

    public companion object {
      public const val MAGIC: UInt = 0x54236209U
    }
  }
}
