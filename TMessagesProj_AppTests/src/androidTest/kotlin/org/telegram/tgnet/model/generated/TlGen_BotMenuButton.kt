package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BotMenuButton : TlGen_Object {
  public data object TL_botMenuButtonDefault : TlGen_BotMenuButton() {
    public const val MAGIC: UInt = 0x7533A588U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_botMenuButtonCommands : TlGen_BotMenuButton() {
    public const val MAGIC: UInt = 0x4258C205U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_botMenuButton(
    public val text: String,
    public val url: String,
  ) : TlGen_BotMenuButton() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC7B57CE6U
    }
  }
}
