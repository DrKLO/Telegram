package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_bots_ExportedBotToken : TlGen_Object {
  public data class TL_bots_exportedBotToken(
    public val token: String,
  ) : TlGen_bots_ExportedBotToken() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(token)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3C60B621U
    }
  }
}
