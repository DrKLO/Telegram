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

public sealed class TlGen_StarGift : TlGen_Object {
  public data class TL_starGiftUnique(
    public val require_premium: Boolean,
    public val resale_ton_only: Boolean,
    public val theme_available: Boolean,
    public val id: Long,
    public val gift_id: Long,
    public val title: String,
    public val slug: String,
    public val num: Int,
    public val owner_id: TlGen_Peer?,
    public val owner_name: String?,
    public val owner_address: String?,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
    public val gift_address: String?,
    public val resell_amount: List<TlGen_StarsAmount>?,
    public val released_by: TlGen_Peer?,
    public val theme_peer: TlGen_Peer?,
    public val peer_color: TlGen_PeerColor?,
    public val host_id: TlGen_Peer?,
    public val multiflags_8: Multiflags_8?,
  ) : TlGen_StarGift() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (owner_id != null) result = result or 1U
        if (owner_name != null) result = result or 2U
        if (owner_address != null) result = result or 4U
        if (gift_address != null) result = result or 8U
        if (resell_amount != null) result = result or 16U
        if (released_by != null) result = result or 32U
        if (require_premium) result = result or 64U
        if (resale_ton_only) result = result or 128U
        if (multiflags_8 != null) result = result or 256U
        if (theme_available) result = result or 512U
        if (theme_peer != null) result = result or 1024U
        if (peer_color != null) result = result or 2048U
        if (host_id != null) result = result or 4096U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(gift_id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt32(num)
      owner_id?.serializeToStream(stream)
      owner_name?.let { stream.writeString(it) }
      owner_address?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
      gift_address?.let { stream.writeString(it) }
      resell_amount?.let { TlGen_Vector.serialize(stream, it) }
      released_by?.serializeToStream(stream)
      multiflags_8?.let { stream.writeInt64(it.value_amount) }
      multiflags_8?.let { stream.writeString(it.value_currency) }
      theme_peer?.serializeToStream(stream)
      peer_color?.serializeToStream(stream)
      host_id?.serializeToStream(stream)
    }

    public data class Multiflags_8(
      public val value_amount: Long,
      public val value_currency: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0xB0BF741BU
    }
  }

  public data class TL_starGift(
    public val birthday: Boolean,
    public val require_premium: Boolean,
    public val peer_color_available: Boolean,
    public val id: Long,
    public val sticker: TlGen_Document,
    public val stars: Long,
    public val convert_stars: Long,
    public val upgrade_stars: Long?,
    public val title: String?,
    public val released_by: TlGen_Peer?,
    public val locked_until_date: Int?,
    public val multiflags_0: Multiflags_0?,
    public val multiflags_1: Multiflags_1?,
    public val multiflags_8: Multiflags_8?,
    public val multiflags_11: Multiflags_11?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_StarGift() {
    public val limited: Boolean = multiflags_0 != null

    public val sold_out: Boolean = multiflags_1 != null

    public val limited_per_user: Boolean = multiflags_8 != null

    public val auction: Boolean = multiflags_11 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (limited) result = result or 1U
        if (sold_out) result = result or 2U
        if (birthday) result = result or 4U
        if (upgrade_stars != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (title != null) result = result or 32U
        if (released_by != null) result = result or 64U
        if (require_premium) result = result or 128U
        if (limited_per_user) result = result or 256U
        if (locked_until_date != null) result = result or 512U
        if (peer_color_available) result = result or 1024U
        if (auction) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      sticker.serializeToStream(stream)
      stream.writeInt64(stars)
      multiflags_0?.let { stream.writeInt32(it.availability_remains) }
      multiflags_0?.let { stream.writeInt32(it.availability_total) }
      multiflags_4?.let { stream.writeInt64(it.availability_resale) }
      stream.writeInt64(convert_stars)
      multiflags_1?.let { stream.writeInt32(it.first_sale_date) }
      multiflags_1?.let { stream.writeInt32(it.last_sale_date) }
      upgrade_stars?.let { stream.writeInt64(it) }
      multiflags_4?.let { stream.writeInt64(it.resell_min_stars) }
      title?.let { stream.writeString(it) }
      released_by?.serializeToStream(stream)
      multiflags_8?.let { stream.writeInt32(it.per_user_total) }
      multiflags_8?.let { stream.writeInt32(it.per_user_remains) }
      locked_until_date?.let { stream.writeInt32(it) }
      multiflags_11?.let { stream.writeString(it.auction_slug) }
      multiflags_11?.let { stream.writeInt32(it.gifts_per_round) }
    }

    public data class Multiflags_0(
      public val availability_remains: Int,
      public val availability_total: Int,
    )

    public data class Multiflags_1(
      public val first_sale_date: Int,
      public val last_sale_date: Int,
    )

    public data class Multiflags_8(
      public val per_user_total: Int,
      public val per_user_remains: Int,
    )

    public data class Multiflags_11(
      public val auction_slug: String,
      public val gifts_per_round: Int,
    )

    public data class Multiflags_4(
      public val availability_resale: Long,
      public val resell_min_stars: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x1B9A4D7FU
    }
  }

  public data class TL_starGift_layer190(
    public val id: Long,
    public val sticker: TlGen_Document,
    public val stars: Long,
    public val convert_stars: Long,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_Object {
    public val limited: Boolean = multiflags_0 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (limited) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      sticker.serializeToStream(stream)
      stream.writeInt64(stars)
      multiflags_0?.let { stream.writeInt32(it.availability_remains) }
      multiflags_0?.let { stream.writeInt32(it.availability_total) }
      stream.writeInt64(convert_stars)
    }

    public data class Multiflags_0(
      public val availability_remains: Int,
      public val availability_total: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xAEA174EEU
    }
  }

  public data class TL_starGift_layer195(
    public val birthday: Boolean,
    public val id: Long,
    public val sticker: TlGen_Document,
    public val stars: Long,
    public val convert_stars: Long,
    public val multiflags_0: Multiflags_0?,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_Object {
    public val limited: Boolean = multiflags_0 != null

    public val sold_out: Boolean = multiflags_1 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (limited) result = result or 1U
        if (sold_out) result = result or 2U
        if (birthday) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      sticker.serializeToStream(stream)
      stream.writeInt64(stars)
      multiflags_0?.let { stream.writeInt32(it.availability_remains) }
      multiflags_0?.let { stream.writeInt32(it.availability_total) }
      stream.writeInt64(convert_stars)
      multiflags_1?.let { stream.writeInt32(it.first_sale_date) }
      multiflags_1?.let { stream.writeInt32(it.last_sale_date) }
    }

    public data class Multiflags_0(
      public val availability_remains: Int,
      public val availability_total: Int,
    )

    public data class Multiflags_1(
      public val first_sale_date: Int,
      public val last_sale_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x49C577CDU
    }
  }

  public data class TL_starGift_layer202(
    public val birthday: Boolean,
    public val id: Long,
    public val sticker: TlGen_Document,
    public val stars: Long,
    public val convert_stars: Long,
    public val upgrade_stars: Long?,
    public val multiflags_0: Multiflags_0?,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_Object {
    public val limited: Boolean = multiflags_0 != null

    public val sold_out: Boolean = multiflags_1 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (limited) result = result or 1U
        if (sold_out) result = result or 2U
        if (birthday) result = result or 4U
        if (upgrade_stars != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      sticker.serializeToStream(stream)
      stream.writeInt64(stars)
      multiflags_0?.let { stream.writeInt32(it.availability_remains) }
      multiflags_0?.let { stream.writeInt32(it.availability_total) }
      stream.writeInt64(convert_stars)
      multiflags_1?.let { stream.writeInt32(it.first_sale_date) }
      multiflags_1?.let { stream.writeInt32(it.last_sale_date) }
      upgrade_stars?.let { stream.writeInt64(it) }
    }

    public data class Multiflags_0(
      public val availability_remains: Int,
      public val availability_total: Int,
    )

    public data class Multiflags_1(
      public val first_sale_date: Int,
      public val last_sale_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x02CC73C8U
    }
  }

  public data class TL_starGiftUnique_layer196(
    public val id: Long,
    public val title: String,
    public val num: Int,
    public val owner_id: Long,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeString(title)
      stream.writeInt32(num)
      stream.writeInt64(owner_id)
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6A1407CDU
    }
  }

  public data class TL_starGiftUnique_layer197(
    public val id: Long,
    public val title: String,
    public val slug: String,
    public val num: Int,
    public val owner_id: Long?,
    public val owner_name: String?,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (owner_id != null) result = result or 1U
        if (owner_name != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt32(num)
      owner_id?.let { stream.writeInt64(it) }
      owner_name?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3482F322U
    }
  }

  public data class TL_starGiftUnique_layer198(
    public val id: Long,
    public val title: String,
    public val slug: String,
    public val num: Int,
    public val owner_id: TlGen_Peer?,
    public val owner_name: String?,
    public val owner_address: String?,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (owner_id != null) result = result or 1U
        if (owner_name != null) result = result or 2U
        if (owner_address != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt32(num)
      owner_id?.serializeToStream(stream)
      owner_name?.let { stream.writeString(it) }
      owner_address?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF2FE7E4AU
    }
  }

  public data class TL_starGiftUnique_layer202(
    public val id: Long,
    public val title: String,
    public val slug: String,
    public val num: Int,
    public val owner_id: TlGen_Peer?,
    public val owner_name: String?,
    public val owner_address: String?,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
    public val gift_address: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (owner_id != null) result = result or 1U
        if (owner_name != null) result = result or 2U
        if (owner_address != null) result = result or 4U
        if (gift_address != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt32(num)
      owner_id?.serializeToStream(stream)
      owner_name?.let { stream.writeString(it) }
      owner_address?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
      gift_address?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x5C62D151U
    }
  }

  public data class TL_starGift_layer206(
    public val birthday: Boolean,
    public val id: Long,
    public val sticker: TlGen_Document,
    public val stars: Long,
    public val convert_stars: Long,
    public val upgrade_stars: Long?,
    public val title: String?,
    public val multiflags_0: Multiflags_0?,
    public val multiflags_1: Multiflags_1?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    public val limited: Boolean = multiflags_0 != null

    public val sold_out: Boolean = multiflags_1 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (limited) result = result or 1U
        if (sold_out) result = result or 2U
        if (birthday) result = result or 4U
        if (upgrade_stars != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (title != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      sticker.serializeToStream(stream)
      stream.writeInt64(stars)
      multiflags_0?.let { stream.writeInt32(it.availability_remains) }
      multiflags_0?.let { stream.writeInt32(it.availability_total) }
      multiflags_4?.let { stream.writeInt64(it.availability_resale) }
      stream.writeInt64(convert_stars)
      multiflags_1?.let { stream.writeInt32(it.first_sale_date) }
      multiflags_1?.let { stream.writeInt32(it.last_sale_date) }
      upgrade_stars?.let { stream.writeInt64(it) }
      multiflags_4?.let { stream.writeInt64(it.resell_min_stars) }
      title?.let { stream.writeString(it) }
    }

    public data class Multiflags_0(
      public val availability_remains: Int,
      public val availability_total: Int,
    )

    public data class Multiflags_1(
      public val first_sale_date: Int,
      public val last_sale_date: Int,
    )

    public data class Multiflags_4(
      public val availability_resale: Long,
      public val resell_min_stars: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0xC62ACA28U
    }
  }

  public data class TL_starGiftUnique_layer206(
    public val id: Long,
    public val title: String,
    public val slug: String,
    public val num: Int,
    public val owner_id: TlGen_Peer?,
    public val owner_name: String?,
    public val owner_address: String?,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
    public val gift_address: String?,
    public val resell_stars: Long?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (owner_id != null) result = result or 1U
        if (owner_name != null) result = result or 2U
        if (owner_address != null) result = result or 4U
        if (gift_address != null) result = result or 8U
        if (resell_stars != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt32(num)
      owner_id?.serializeToStream(stream)
      owner_name?.let { stream.writeString(it) }
      owner_address?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
      gift_address?.let { stream.writeString(it) }
      resell_stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x6411DB89U
    }
  }

  public data class TL_starGift_layer209(
    public val birthday: Boolean,
    public val id: Long,
    public val sticker: TlGen_Document,
    public val stars: Long,
    public val convert_stars: Long,
    public val upgrade_stars: Long?,
    public val title: String?,
    public val released_by: TlGen_Peer?,
    public val multiflags_0: Multiflags_0?,
    public val multiflags_1: Multiflags_1?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    public val limited: Boolean = multiflags_0 != null

    public val sold_out: Boolean = multiflags_1 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (limited) result = result or 1U
        if (sold_out) result = result or 2U
        if (birthday) result = result or 4U
        if (upgrade_stars != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (title != null) result = result or 32U
        if (released_by != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      sticker.serializeToStream(stream)
      stream.writeInt64(stars)
      multiflags_0?.let { stream.writeInt32(it.availability_remains) }
      multiflags_0?.let { stream.writeInt32(it.availability_total) }
      multiflags_4?.let { stream.writeInt64(it.availability_resale) }
      stream.writeInt64(convert_stars)
      multiflags_1?.let { stream.writeInt32(it.first_sale_date) }
      multiflags_1?.let { stream.writeInt32(it.last_sale_date) }
      upgrade_stars?.let { stream.writeInt64(it) }
      multiflags_4?.let { stream.writeInt64(it.resell_min_stars) }
      title?.let { stream.writeString(it) }
      released_by?.serializeToStream(stream)
    }

    public data class Multiflags_0(
      public val availability_remains: Int,
      public val availability_total: Int,
    )

    public data class Multiflags_1(
      public val first_sale_date: Int,
      public val last_sale_date: Int,
    )

    public data class Multiflags_4(
      public val availability_resale: Long,
      public val resell_min_stars: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x7F853C12U
    }
  }

  public data class TL_starGiftUnique_layer210(
    public val require_premium: Boolean,
    public val id: Long,
    public val title: String,
    public val slug: String,
    public val num: Int,
    public val owner_id: TlGen_Peer?,
    public val owner_name: String?,
    public val owner_address: String?,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
    public val gift_address: String?,
    public val resell_stars: Long?,
    public val released_by: TlGen_Peer?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (owner_id != null) result = result or 1U
        if (owner_name != null) result = result or 2U
        if (owner_address != null) result = result or 4U
        if (gift_address != null) result = result or 8U
        if (resell_stars != null) result = result or 16U
        if (released_by != null) result = result or 32U
        if (require_premium) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt32(num)
      owner_id?.serializeToStream(stream)
      owner_name?.let { stream.writeString(it) }
      owner_address?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
      gift_address?.let { stream.writeString(it) }
      resell_stars?.let { stream.writeInt64(it) }
      released_by?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF63778AEU
    }
  }

  public data class TL_starGift_layer212(
    public val birthday: Boolean,
    public val require_premium: Boolean,
    public val id: Long,
    public val sticker: TlGen_Document,
    public val stars: Long,
    public val convert_stars: Long,
    public val upgrade_stars: Long?,
    public val title: String?,
    public val released_by: TlGen_Peer?,
    public val multiflags_0: Multiflags_0?,
    public val multiflags_1: Multiflags_1?,
    public val multiflags_8: Multiflags_8?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    public val limited: Boolean = multiflags_0 != null

    public val sold_out: Boolean = multiflags_1 != null

    public val limited_per_user: Boolean = multiflags_8 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (limited) result = result or 1U
        if (sold_out) result = result or 2U
        if (birthday) result = result or 4U
        if (upgrade_stars != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (title != null) result = result or 32U
        if (released_by != null) result = result or 64U
        if (require_premium) result = result or 128U
        if (limited_per_user) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      sticker.serializeToStream(stream)
      stream.writeInt64(stars)
      multiflags_0?.let { stream.writeInt32(it.availability_remains) }
      multiflags_0?.let { stream.writeInt32(it.availability_total) }
      multiflags_4?.let { stream.writeInt64(it.availability_resale) }
      stream.writeInt64(convert_stars)
      multiflags_1?.let { stream.writeInt32(it.first_sale_date) }
      multiflags_1?.let { stream.writeInt32(it.last_sale_date) }
      upgrade_stars?.let { stream.writeInt64(it) }
      multiflags_4?.let { stream.writeInt64(it.resell_min_stars) }
      title?.let { stream.writeString(it) }
      released_by?.serializeToStream(stream)
      multiflags_8?.let { stream.writeInt32(it.per_user_total) }
      multiflags_8?.let { stream.writeInt32(it.per_user_remains) }
    }

    public data class Multiflags_0(
      public val availability_remains: Int,
      public val availability_total: Int,
    )

    public data class Multiflags_1(
      public val first_sale_date: Int,
      public val last_sale_date: Int,
    )

    public data class Multiflags_8(
      public val per_user_total: Int,
      public val per_user_remains: Int,
    )

    public data class Multiflags_4(
      public val availability_resale: Long,
      public val resell_min_stars: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x00BCFF5BU
    }
  }

  public data class TL_starGiftUnique_layer211(
    public val require_premium: Boolean,
    public val resale_ton_only: Boolean,
    public val id: Long,
    public val title: String,
    public val slug: String,
    public val num: Int,
    public val owner_id: TlGen_Peer?,
    public val owner_name: String?,
    public val owner_address: String?,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
    public val gift_address: String?,
    public val resell_amount: List<TlGen_StarsAmount>?,
    public val released_by: TlGen_Peer?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (owner_id != null) result = result or 1U
        if (owner_name != null) result = result or 2U
        if (owner_address != null) result = result or 4U
        if (gift_address != null) result = result or 8U
        if (resell_amount != null) result = result or 16U
        if (released_by != null) result = result or 32U
        if (require_premium) result = result or 64U
        if (resale_ton_only) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt32(num)
      owner_id?.serializeToStream(stream)
      owner_name?.let { stream.writeString(it) }
      owner_address?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
      gift_address?.let { stream.writeString(it) }
      resell_amount?.let { TlGen_Vector.serialize(stream, it) }
      released_by?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3A274D50U
    }
  }

  public data class TL_starGiftUnique_layer213(
    public val require_premium: Boolean,
    public val resale_ton_only: Boolean,
    public val id: Long,
    public val gift_id: Long,
    public val title: String,
    public val slug: String,
    public val num: Int,
    public val owner_id: TlGen_Peer?,
    public val owner_name: String?,
    public val owner_address: String?,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
    public val gift_address: String?,
    public val resell_amount: List<TlGen_StarsAmount>?,
    public val released_by: TlGen_Peer?,
    public val multiflags_8: Multiflags_8?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (owner_id != null) result = result or 1U
        if (owner_name != null) result = result or 2U
        if (owner_address != null) result = result or 4U
        if (gift_address != null) result = result or 8U
        if (resell_amount != null) result = result or 16U
        if (released_by != null) result = result or 32U
        if (require_premium) result = result or 64U
        if (resale_ton_only) result = result or 128U
        if (multiflags_8 != null) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(gift_id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt32(num)
      owner_id?.serializeToStream(stream)
      owner_name?.let { stream.writeString(it) }
      owner_address?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
      gift_address?.let { stream.writeString(it) }
      resell_amount?.let { TlGen_Vector.serialize(stream, it) }
      released_by?.serializeToStream(stream)
      multiflags_8?.let { stream.writeInt64(it.value_amount) }
      multiflags_8?.let { stream.writeString(it.value_currency) }
    }

    public data class Multiflags_8(
      public val value_amount: Long,
      public val value_currency: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0x26A5553EU
    }
  }

  public data class TL_starGift_layer217(
    public val birthday: Boolean,
    public val require_premium: Boolean,
    public val peer_color_available: Boolean,
    public val id: Long,
    public val sticker: TlGen_Document,
    public val stars: Long,
    public val convert_stars: Long,
    public val upgrade_stars: Long?,
    public val title: String?,
    public val released_by: TlGen_Peer?,
    public val locked_until_date: Int?,
    public val multiflags_0: Multiflags_0?,
    public val multiflags_1: Multiflags_1?,
    public val multiflags_8: Multiflags_8?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    public val limited: Boolean = multiflags_0 != null

    public val sold_out: Boolean = multiflags_1 != null

    public val limited_per_user: Boolean = multiflags_8 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (limited) result = result or 1U
        if (sold_out) result = result or 2U
        if (birthday) result = result or 4U
        if (upgrade_stars != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (title != null) result = result or 32U
        if (released_by != null) result = result or 64U
        if (require_premium) result = result or 128U
        if (limited_per_user) result = result or 256U
        if (locked_until_date != null) result = result or 512U
        if (peer_color_available) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      sticker.serializeToStream(stream)
      stream.writeInt64(stars)
      multiflags_0?.let { stream.writeInt32(it.availability_remains) }
      multiflags_0?.let { stream.writeInt32(it.availability_total) }
      multiflags_4?.let { stream.writeInt64(it.availability_resale) }
      stream.writeInt64(convert_stars)
      multiflags_1?.let { stream.writeInt32(it.first_sale_date) }
      multiflags_1?.let { stream.writeInt32(it.last_sale_date) }
      upgrade_stars?.let { stream.writeInt64(it) }
      multiflags_4?.let { stream.writeInt64(it.resell_min_stars) }
      title?.let { stream.writeString(it) }
      released_by?.serializeToStream(stream)
      multiflags_8?.let { stream.writeInt32(it.per_user_total) }
      multiflags_8?.let { stream.writeInt32(it.per_user_remains) }
      locked_until_date?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_0(
      public val availability_remains: Int,
      public val availability_total: Int,
    )

    public data class Multiflags_1(
      public val first_sale_date: Int,
      public val last_sale_date: Int,
    )

    public data class Multiflags_8(
      public val per_user_total: Int,
      public val per_user_remains: Int,
    )

    public data class Multiflags_4(
      public val availability_resale: Long,
      public val resell_min_stars: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x80AC53C3U
    }
  }

  public data class TL_starGiftUnique_layer215(
    public val require_premium: Boolean,
    public val resale_ton_only: Boolean,
    public val theme_available: Boolean,
    public val id: Long,
    public val gift_id: Long,
    public val title: String,
    public val slug: String,
    public val num: Int,
    public val owner_id: TlGen_Peer?,
    public val owner_name: String?,
    public val owner_address: String?,
    public val attributes: List<TlGen_StarGiftAttribute>,
    public val availability_issued: Int,
    public val availability_total: Int,
    public val gift_address: String?,
    public val resell_amount: List<TlGen_StarsAmount>?,
    public val released_by: TlGen_Peer?,
    public val theme_peer: TlGen_Peer?,
    public val multiflags_8: Multiflags_8?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (owner_id != null) result = result or 1U
        if (owner_name != null) result = result or 2U
        if (owner_address != null) result = result or 4U
        if (gift_address != null) result = result or 8U
        if (resell_amount != null) result = result or 16U
        if (released_by != null) result = result or 32U
        if (require_premium) result = result or 64U
        if (resale_ton_only) result = result or 128U
        if (multiflags_8 != null) result = result or 256U
        if (theme_available) result = result or 512U
        if (theme_peer != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(gift_id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt32(num)
      owner_id?.serializeToStream(stream)
      owner_name?.let { stream.writeString(it) }
      owner_address?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(availability_issued)
      stream.writeInt32(availability_total)
      gift_address?.let { stream.writeString(it) }
      resell_amount?.let { TlGen_Vector.serialize(stream, it) }
      released_by?.serializeToStream(stream)
      multiflags_8?.let { stream.writeInt64(it.value_amount) }
      multiflags_8?.let { stream.writeString(it.value_currency) }
      theme_peer?.serializeToStream(stream)
    }

    public data class Multiflags_8(
      public val value_amount: Long,
      public val value_currency: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0x1BEFE865U
    }
  }
}
