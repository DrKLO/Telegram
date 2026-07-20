package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_PassportConfig : TlGen_Object {
  public data object TL_help_passportConfigNotModified : TlGen_help_PassportConfig() {
    public const val MAGIC: UInt = 0xBFB9F457U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_help_passportConfig(
    public val hash: Int,
    public val countries_langs: TlGen_DataJSON,
  ) : TlGen_help_PassportConfig() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(hash)
      countries_langs.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA098D6AFU
    }
  }
}
