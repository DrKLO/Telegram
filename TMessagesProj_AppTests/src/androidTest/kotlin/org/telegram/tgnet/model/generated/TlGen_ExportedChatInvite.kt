package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ExportedChatInvite : TlGen_Object {
  public data object TL_chatInvitePublicJoinRequests : TlGen_ExportedChatInvite() {
    public const val MAGIC: UInt = 0xED107AB7U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_chatInviteExported(
    public val revoked: Boolean,
    public val permanent: Boolean,
    public val request_needed: Boolean,
    public val link: String,
    public val admin_id: Long,
    public val date: Int,
    public val start_date: Int?,
    public val expire_date: Int?,
    public val usage_limit: Int?,
    public val usage: Int?,
    public val requested: Int?,
    public val subscription_expired: Int?,
    public val title: String?,
    public val subscription_pricing: TlGen_StarsSubscriptionPricing?,
  ) : TlGen_ExportedChatInvite() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (revoked) result = result or 1U
        if (expire_date != null) result = result or 2U
        if (usage_limit != null) result = result or 4U
        if (usage != null) result = result or 8U
        if (start_date != null) result = result or 16U
        if (permanent) result = result or 32U
        if (request_needed) result = result or 64U
        if (requested != null) result = result or 128U
        if (title != null) result = result or 256U
        if (subscription_pricing != null) result = result or 512U
        if (subscription_expired != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(link)
      stream.writeInt64(admin_id)
      stream.writeInt32(date)
      start_date?.let { stream.writeInt32(it) }
      expire_date?.let { stream.writeInt32(it) }
      usage_limit?.let { stream.writeInt32(it) }
      usage?.let { stream.writeInt32(it) }
      requested?.let { stream.writeInt32(it) }
      subscription_expired?.let { stream.writeInt32(it) }
      title?.let { stream.writeString(it) }
      subscription_pricing?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA22CBD96U
    }
  }

  public data object TL_chatInviteEmpty_layer122 : TlGen_Object {
    public const val MAGIC: UInt = 0x69DF3769U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_chatInviteExported_layer122(
    public val link: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(link)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFC2E05BCU
    }
  }

  public data class TL_chatInviteExported_layer132(
    public val revoked: Boolean,
    public val permanent: Boolean,
    public val link: String,
    public val admin_id: Int,
    public val date: Int,
    public val start_date: Int?,
    public val expire_date: Int?,
    public val usage_limit: Int?,
    public val usage: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (revoked) result = result or 1U
        if (expire_date != null) result = result or 2U
        if (usage_limit != null) result = result or 4U
        if (usage != null) result = result or 8U
        if (start_date != null) result = result or 16U
        if (permanent) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(link)
      stream.writeInt32(admin_id)
      stream.writeInt32(date)
      start_date?.let { stream.writeInt32(it) }
      expire_date?.let { stream.writeInt32(it) }
      usage_limit?.let { stream.writeInt32(it) }
      usage?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x6E24FC9DU
    }
  }

  public data class TL_chatInviteExported_layer133(
    public val revoked: Boolean,
    public val permanent: Boolean,
    public val link: String,
    public val admin_id: Long,
    public val date: Int,
    public val start_date: Int?,
    public val expire_date: Int?,
    public val usage_limit: Int?,
    public val usage: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (revoked) result = result or 1U
        if (expire_date != null) result = result or 2U
        if (usage_limit != null) result = result or 4U
        if (usage != null) result = result or 8U
        if (start_date != null) result = result or 16U
        if (permanent) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(link)
      stream.writeInt64(admin_id)
      stream.writeInt32(date)
      start_date?.let { stream.writeInt32(it) }
      expire_date?.let { stream.writeInt32(it) }
      usage_limit?.let { stream.writeInt32(it) }
      usage?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB18105E8U
    }
  }

  public data class TL_chatInviteExported_layer185(
    public val revoked: Boolean,
    public val permanent: Boolean,
    public val request_needed: Boolean,
    public val link: String,
    public val admin_id: Long,
    public val date: Int,
    public val start_date: Int?,
    public val expire_date: Int?,
    public val usage_limit: Int?,
    public val usage: Int?,
    public val requested: Int?,
    public val title: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (revoked) result = result or 1U
        if (expire_date != null) result = result or 2U
        if (usage_limit != null) result = result or 4U
        if (usage != null) result = result or 8U
        if (start_date != null) result = result or 16U
        if (permanent) result = result or 32U
        if (request_needed) result = result or 64U
        if (requested != null) result = result or 128U
        if (title != null) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(link)
      stream.writeInt64(admin_id)
      stream.writeInt32(date)
      start_date?.let { stream.writeInt32(it) }
      expire_date?.let { stream.writeInt32(it) }
      usage_limit?.let { stream.writeInt32(it) }
      usage?.let { stream.writeInt32(it) }
      requested?.let { stream.writeInt32(it) }
      title?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x0AB4A819U
    }
  }
}
