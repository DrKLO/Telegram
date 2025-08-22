package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPaymentCredentials : TlGen_Object {
  public data class TL_inputPaymentCredentialsSaved(
    public val id: String,
    public val tmp_password: List<Byte>,
  ) : TlGen_InputPaymentCredentials() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(id)
      stream.writeByteArray(tmp_password.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xC10EB2CFU
    }
  }

  public data class TL_inputPaymentCredentials(
    public val save: Boolean,
    public val `data`: TlGen_DataJSON,
  ) : TlGen_InputPaymentCredentials() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (save) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      data.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3417D728U
    }
  }

  public data class TL_inputPaymentCredentialsGooglePay(
    public val payment_token: TlGen_DataJSON,
  ) : TlGen_InputPaymentCredentials() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      payment_token.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8AC32801U
    }
  }
}
