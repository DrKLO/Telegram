package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_CheckedGiftCode : TlGen_Object {
  public data class TL_payments_checkedGiftCode(
    public val via_giveaway: Boolean,
    public val from_id: TlGen_Peer?,
    public val giveaway_msg_id: Int?,
    public val to_id: Long?,
    public val date: Int,
    public val days: Int,
    public val used_date: Int?,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_payments_CheckedGiftCode() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (to_id != null) result = result or 1U
        if (used_date != null) result = result or 2U
        if (via_giveaway) result = result or 4U
        if (giveaway_msg_id != null) result = result or 8U
        if (from_id != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      from_id?.serializeToStream(stream)
      giveaway_msg_id?.let { stream.writeInt32(it) }
      to_id?.let { stream.writeInt64(it) }
      stream.writeInt32(date)
      stream.writeInt32(days)
      used_date?.let { stream.writeInt32(it) }
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEB983F8FU
    }
  }
}
