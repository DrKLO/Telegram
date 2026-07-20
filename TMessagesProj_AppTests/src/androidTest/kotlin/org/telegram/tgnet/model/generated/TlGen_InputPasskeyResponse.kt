package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPasskeyResponse : TlGen_Object {
  public data class TL_inputPasskeyResponseRegister(
    public val client_data: TlGen_DataJSON,
    public val attestation_data: List<Byte>,
  ) : TlGen_InputPasskeyResponse() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      client_data.serializeToStream(stream)
      stream.writeByteArray(attestation_data.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x3E63935CU
    }
  }

  public data class TL_inputPasskeyResponseLogin(
    public val client_data: TlGen_DataJSON,
    public val authenticator_data: List<Byte>,
    public val signature: List<Byte>,
    public val user_handle: String,
  ) : TlGen_InputPasskeyResponse() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      client_data.serializeToStream(stream)
      stream.writeByteArray(authenticator_data.toByteArray())
      stream.writeByteArray(signature.toByteArray())
      stream.writeString(user_handle)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC31FC14AU
    }
  }
}
