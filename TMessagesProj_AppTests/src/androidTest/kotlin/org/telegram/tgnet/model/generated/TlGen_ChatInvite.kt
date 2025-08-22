package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatInvite : TlGen_Object {
  public data class TL_chatInviteAlready(
    public val chat: TlGen_Chat,
  ) : TlGen_ChatInvite() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      chat.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5A686D7CU
    }
  }

  public data class TL_chatInvitePeek(
    public val chat: TlGen_Chat,
    public val expires: Int,
  ) : TlGen_ChatInvite() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      chat.serializeToStream(stream)
      stream.writeInt32(expires)
    }

    public companion object {
      public const val MAGIC: UInt = 0x61695CB0U
    }
  }

  public data class TL_chatInvite(
    public val channel: Boolean,
    public val broadcast: Boolean,
    public val `public`: Boolean,
    public val megagroup: Boolean,
    public val request_needed: Boolean,
    public val verified: Boolean,
    public val scam: Boolean,
    public val fake: Boolean,
    public val can_refulfill_subscription: Boolean,
    public val title: String,
    public val about: String?,
    public val photo: TlGen_Photo,
    public val participants_count: Int,
    public val participants: List<TlGen_User>?,
    public val color: Int,
    public val subscription_pricing: TlGen_StarsSubscriptionPricing?,
    public val subscription_form_id: Long?,
    public val bot_verification: TlGen_BotVerification?,
  ) : TlGen_ChatInvite() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (channel) result = result or 1U
        if (broadcast) result = result or 2U
        if (public) result = result or 4U
        if (megagroup) result = result or 8U
        if (participants != null) result = result or 16U
        if (about != null) result = result or 32U
        if (request_needed) result = result or 64U
        if (verified) result = result or 128U
        if (scam) result = result or 256U
        if (fake) result = result or 512U
        if (subscription_pricing != null) result = result or 1024U
        if (can_refulfill_subscription) result = result or 2048U
        if (subscription_form_id != null) result = result or 4096U
        if (bot_verification != null) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(title)
      about?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(participants_count)
      participants?.let { TlGen_Vector.serialize(stream, it) }
      stream.writeInt32(color)
      subscription_pricing?.serializeToStream(stream)
      subscription_form_id?.let { stream.writeInt64(it) }
      bot_verification?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5C9D3702U
    }
  }
}
