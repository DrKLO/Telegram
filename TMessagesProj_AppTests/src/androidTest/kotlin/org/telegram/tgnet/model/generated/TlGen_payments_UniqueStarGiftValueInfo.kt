package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_UniqueStarGiftValueInfo : TlGen_Object {
  public data class TL_payments_uniqueStarGiftValueInfo(
    public val last_sale_on_fragment: Boolean,
    public val value_is_average: Boolean,
    public val currency: String,
    public val `value`: Long,
    public val initial_sale_date: Int,
    public val initial_sale_stars: Long,
    public val initial_sale_price: Long,
    public val floor_price: Long?,
    public val average_price: Long?,
    public val listed_count: Int?,
    public val multiflags_0: Multiflags_0?,
    public val multiflags_5: Multiflags_5?,
  ) : TlGen_payments_UniqueStarGiftValueInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (last_sale_on_fragment) result = result or 2U
        if (floor_price != null) result = result or 4U
        if (average_price != null) result = result or 8U
        if (listed_count != null) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (value_is_average) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(value)
      stream.writeInt32(initial_sale_date)
      stream.writeInt64(initial_sale_stars)
      stream.writeInt64(initial_sale_price)
      multiflags_0?.let { stream.writeInt32(it.last_sale_date) }
      multiflags_0?.let { stream.writeInt64(it.last_sale_price) }
      floor_price?.let { stream.writeInt64(it) }
      average_price?.let { stream.writeInt64(it) }
      listed_count?.let { stream.writeInt32(it) }
      multiflags_5?.let { stream.writeInt32(it.fragment_listed_count) }
      multiflags_5?.let { stream.writeString(it.fragment_listed_url) }
    }

    public data class Multiflags_0(
      public val last_sale_date: Int,
      public val last_sale_price: Long,
    )

    public data class Multiflags_5(
      public val fragment_listed_count: Int,
      public val fragment_listed_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0x512FE446U
    }
  }
}
