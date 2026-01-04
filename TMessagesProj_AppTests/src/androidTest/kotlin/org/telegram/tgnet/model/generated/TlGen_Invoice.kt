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

public sealed class TlGen_Invoice : TlGen_Object {
  public data class TL_invoice(
    public val test: Boolean,
    public val name_requested: Boolean,
    public val phone_requested: Boolean,
    public val email_requested: Boolean,
    public val shipping_address_requested: Boolean,
    public val flexible: Boolean,
    public val phone_to_provider: Boolean,
    public val email_to_provider: Boolean,
    public val recurring: Boolean,
    public val currency: String,
    public val prices: List<TlGen_LabeledPrice>,
    public val terms_url: String?,
    public val subscription_period: Int?,
    public val multiflags_8: Multiflags_8?,
  ) : TlGen_Invoice() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (test) result = result or 1U
        if (name_requested) result = result or 2U
        if (phone_requested) result = result or 4U
        if (email_requested) result = result or 8U
        if (shipping_address_requested) result = result or 16U
        if (flexible) result = result or 32U
        if (phone_to_provider) result = result or 64U
        if (email_to_provider) result = result or 128U
        if (multiflags_8 != null) result = result or 256U
        if (recurring) result = result or 512U
        if (terms_url != null) result = result or 1024U
        if (subscription_period != null) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      TlGen_Vector.serialize(stream, prices)
      multiflags_8?.let { stream.writeInt64(it.max_tip_amount) }
      multiflags_8?.let { TlGen_Vector.serializeLong(stream, it.suggested_tip_amounts) }
      terms_url?.let { stream.writeString(it) }
      subscription_period?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_8(
      public val max_tip_amount: Long,
      public val suggested_tip_amounts: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x049EE584U
    }
  }

  public data class TL_invoice_layer192(
    public val test: Boolean,
    public val name_requested: Boolean,
    public val phone_requested: Boolean,
    public val email_requested: Boolean,
    public val shipping_address_requested: Boolean,
    public val flexible: Boolean,
    public val phone_to_provider: Boolean,
    public val email_to_provider: Boolean,
    public val recurring: Boolean,
    public val currency: String,
    public val prices: List<TlGen_LabeledPrice>,
    public val terms_url: String?,
    public val multiflags_8: Multiflags_8?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (test) result = result or 1U
        if (name_requested) result = result or 2U
        if (phone_requested) result = result or 4U
        if (email_requested) result = result or 8U
        if (shipping_address_requested) result = result or 16U
        if (flexible) result = result or 32U
        if (phone_to_provider) result = result or 64U
        if (email_to_provider) result = result or 128U
        if (multiflags_8 != null) result = result or 256U
        if (recurring) result = result or 512U
        if (terms_url != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      TlGen_Vector.serialize(stream, prices)
      multiflags_8?.let { stream.writeInt64(it.max_tip_amount) }
      multiflags_8?.let { TlGen_Vector.serializeLong(stream, it.suggested_tip_amounts) }
      terms_url?.let { stream.writeString(it) }
    }

    public data class Multiflags_8(
      public val max_tip_amount: Long,
      public val suggested_tip_amounts: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x5DB95A15U
    }
  }
}
