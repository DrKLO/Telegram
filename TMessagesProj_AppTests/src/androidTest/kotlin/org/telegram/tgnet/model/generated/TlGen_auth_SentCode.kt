package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_auth_SentCode : TlGen_Object {
  public data class TL_auth_sentCode(
    public val type: TlGen_auth_SentCodeType,
    public val phone_code_hash: String,
    public val next_type: TlGen_auth_CodeType?,
    public val timeout: Int?,
  ) : TlGen_auth_SentCode() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_type != null) result = result or 2U
        if (timeout != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      type.serializeToStream(stream)
      stream.writeString(phone_code_hash)
      next_type?.serializeToStream(stream)
      timeout?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x5E002502U
    }
  }

  public data class TL_auth_sentCodeSuccess(
    public val authorization: TlGen_auth_Authorization,
  ) : TlGen_auth_SentCode() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      authorization.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2390FE44U
    }
  }

  public data class TL_auth_sentCodePaymentRequired(
    public val store_product: String,
    public val phone_code_hash: String,
    public val support_email_address: String,
    public val support_email_subject: String,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_auth_SentCode() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(store_product)
      stream.writeString(phone_code_hash)
      stream.writeString(support_email_address)
      stream.writeString(support_email_subject)
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE0955A3CU
    }
  }
}
