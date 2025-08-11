package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarsSubscription : TlGen_Object {
  public data class TL_starsSubscription(
    public val canceled: Boolean,
    public val can_refulfill: Boolean,
    public val missing_balance: Boolean,
    public val bot_canceled: Boolean,
    public val id: String,
    public val peer: TlGen_Peer,
    public val until_date: Int,
    public val pricing: TlGen_StarsSubscriptionPricing,
    public val chat_invite_hash: String?,
    public val title: String?,
    public val photo: TlGen_WebDocument?,
    public val invoice_slug: String?,
  ) : TlGen_StarsSubscription() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (canceled) result = result or 1U
        if (can_refulfill) result = result or 2U
        if (missing_balance) result = result or 4U
        if (chat_invite_hash != null) result = result or 8U
        if (title != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (invoice_slug != null) result = result or 64U
        if (bot_canceled) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      peer.serializeToStream(stream)
      stream.writeInt32(until_date)
      pricing.serializeToStream(stream)
      chat_invite_hash?.let { stream.writeString(it) }
      title?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      invoice_slug?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x2E6EAB1AU
    }
  }
}
