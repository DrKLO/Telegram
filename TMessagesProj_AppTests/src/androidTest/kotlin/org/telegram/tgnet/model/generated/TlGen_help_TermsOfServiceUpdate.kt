package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_TermsOfServiceUpdate : TlGen_Object {
  public data class TL_help_termsOfServiceUpdateEmpty(
    public val expires: Int,
  ) : TlGen_help_TermsOfServiceUpdate() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(expires)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE3309F7FU
    }
  }

  public data class TL_help_termsOfServiceUpdate(
    public val expires: Int,
    public val terms_of_service: TlGen_help_TermsOfService,
  ) : TlGen_help_TermsOfServiceUpdate() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(expires)
      terms_of_service.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x28ECF961U
    }
  }
}
