package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ConnectedBotStarRef : TlGen_Object {
  public data class TL_connectedBotStarRef(
    public val revoked: Boolean,
    public val url: String,
    public val date: Int,
    public val bot_id: Long,
    public val commission_permille: Int,
    public val duration_months: Int?,
    public val participants: Long,
    public val revenue: Long,
  ) : TlGen_ConnectedBotStarRef() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (duration_months != null) result = result or 1U
        if (revoked) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
      stream.writeInt32(date)
      stream.writeInt64(bot_id)
      stream.writeInt32(commission_permille)
      duration_months?.let { stream.writeInt32(it) }
      stream.writeInt64(participants)
      stream.writeInt64(revenue)
    }

    public companion object {
      public const val MAGIC: UInt = 0x19A13F71U
    }
  }
}
