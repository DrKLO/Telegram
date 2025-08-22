package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_AppConfig : TlGen_Object {
  public data object TL_help_appConfigNotModified : TlGen_help_AppConfig() {
    public const val MAGIC: UInt = 0x7CDE641DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_help_appConfig(
    public val hash: Int,
    public val config: TlGen_JSONValue,
  ) : TlGen_help_AppConfig() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(hash)
      config.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDD18782EU
    }
  }
}
