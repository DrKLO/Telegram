package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_GiveawayInfo : TlGen_Object {
  public data class TL_payments_giveawayInfo(
    public val participating: Boolean,
    public val preparing_results: Boolean,
    public val start_date: Int,
    public val joined_too_early_date: Int?,
    public val admin_disallowed_chat_id: Long?,
    public val disallowed_country: String?,
  ) : TlGen_payments_GiveawayInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participating) result = result or 1U
        if (joined_too_early_date != null) result = result or 2U
        if (admin_disallowed_chat_id != null) result = result or 4U
        if (preparing_results) result = result or 8U
        if (disallowed_country != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(start_date)
      joined_too_early_date?.let { stream.writeInt32(it) }
      admin_disallowed_chat_id?.let { stream.writeInt64(it) }
      disallowed_country?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4367DAA0U
    }
  }

  public data class TL_payments_giveawayInfoResults(
    public val winner: Boolean,
    public val refunded: Boolean,
    public val start_date: Int,
    public val gift_code_slug: String?,
    public val stars_prize: Long?,
    public val finish_date: Int,
    public val winners_count: Int,
    public val activated_count: Int?,
  ) : TlGen_payments_GiveawayInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (winner) result = result or 1U
        if (refunded) result = result or 2U
        if (activated_count != null) result = result or 4U
        if (gift_code_slug != null) result = result or 8U
        if (stars_prize != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(start_date)
      gift_code_slug?.let { stream.writeString(it) }
      stars_prize?.let { stream.writeInt64(it) }
      stream.writeInt32(finish_date)
      stream.writeInt32(winners_count)
      activated_count?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xE175E66FU
    }
  }
}
