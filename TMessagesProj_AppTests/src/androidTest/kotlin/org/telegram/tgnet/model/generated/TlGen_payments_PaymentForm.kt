package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_PaymentForm : TlGen_Object {
  public data class TL_payments_paymentForm(
    public val can_save_credentials: Boolean,
    public val password_missing: Boolean,
    public val form_id: Long,
    public val bot_id: Long,
    public val title: String,
    public val description: String,
    public val photo: TlGen_WebDocument?,
    public val invoice: TlGen_Invoice,
    public val provider_id: Long,
    public val url: String,
    public val additional_methods: List<TlGen_PaymentFormMethod>?,
    public val saved_info: TlGen_PaymentRequestedInfo?,
    public val saved_credentials: List<TlGen_PaymentSavedCredentials>?,
    public val users: List<TlGen_User>,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_payments_PaymentForm() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (saved_info != null) result = result or 1U
        if (saved_credentials != null) result = result or 2U
        if (can_save_credentials) result = result or 4U
        if (password_missing) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (additional_methods != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(form_id)
      stream.writeInt64(bot_id)
      stream.writeString(title)
      stream.writeString(description)
      photo?.serializeToStream(stream)
      invoice.serializeToStream(stream)
      stream.writeInt64(provider_id)
      stream.writeString(url)
      multiflags_4?.let { stream.writeString(it.native_provider) }
      multiflags_4?.let { it.native_params.serializeToStream(stream) }
      additional_methods?.let { TlGen_Vector.serialize(stream, it) }
      saved_info?.serializeToStream(stream)
      saved_credentials?.let { TlGen_Vector.serialize(stream, it) }
      TlGen_Vector.serialize(stream, users)
    }

    public data class Multiflags_4(
      public val native_provider: String,
      public val native_params: TlGen_DataJSON,
    )

    public companion object {
      public const val MAGIC: UInt = 0xA0058751U
    }
  }

  public data class TL_payments_paymentFormStars(
    public val form_id: Long,
    public val bot_id: Long,
    public val title: String,
    public val description: String,
    public val photo: TlGen_WebDocument?,
    public val invoice: TlGen_Invoice,
    public val users: List<TlGen_User>,
  ) : TlGen_payments_PaymentForm() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(form_id)
      stream.writeInt64(bot_id)
      stream.writeString(title)
      stream.writeString(description)
      photo?.serializeToStream(stream)
      invoice.serializeToStream(stream)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7BF6B15CU
    }
  }

  public data class TL_payments_paymentFormStarGift(
    public val form_id: Long,
    public val invoice: TlGen_Invoice,
  ) : TlGen_payments_PaymentForm() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(form_id)
      invoice.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB425CFE1U
    }
  }
}
