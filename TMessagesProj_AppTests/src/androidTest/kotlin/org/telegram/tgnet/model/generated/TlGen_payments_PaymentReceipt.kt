package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_PaymentReceipt : TlGen_Object {
  public data class TL_payments_paymentReceipt(
    public val date: Int,
    public val bot_id: Long,
    public val provider_id: Long,
    public val title: String,
    public val description: String,
    public val photo: TlGen_WebDocument?,
    public val invoice: TlGen_Invoice,
    public val info: TlGen_PaymentRequestedInfo?,
    public val shipping: TlGen_ShippingOption?,
    public val tip_amount: Long?,
    public val currency: String,
    public val total_amount: Long,
    public val credentials_title: String,
    public val users: List<TlGen_User>,
  ) : TlGen_payments_PaymentReceipt() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (info != null) result = result or 1U
        if (shipping != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (tip_amount != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(date)
      stream.writeInt64(bot_id)
      stream.writeInt64(provider_id)
      stream.writeString(title)
      stream.writeString(description)
      photo?.serializeToStream(stream)
      invoice.serializeToStream(stream)
      info?.serializeToStream(stream)
      shipping?.serializeToStream(stream)
      tip_amount?.let { stream.writeInt64(it) }
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      stream.writeString(credentials_title)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x70C4FE03U
    }
  }

  public data class TL_payments_paymentReceiptStars(
    public val date: Int,
    public val bot_id: Long,
    public val title: String,
    public val description: String,
    public val photo: TlGen_WebDocument?,
    public val invoice: TlGen_Invoice,
    public val currency: String,
    public val total_amount: Long,
    public val transaction_id: String,
    public val users: List<TlGen_User>,
  ) : TlGen_payments_PaymentReceipt() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(date)
      stream.writeInt64(bot_id)
      stream.writeString(title)
      stream.writeString(description)
      photo?.serializeToStream(stream)
      invoice.serializeToStream(stream)
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      stream.writeString(transaction_id)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDABBF83AU
    }
  }
}
