package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputChatTheme : TlGen_Object {
  public data object TL_inputChatThemeEmpty : TlGen_InputChatTheme() {
    public const val MAGIC: UInt = 0x83268483U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputChatTheme(
    public val emoticon: String,
  ) : TlGen_InputChatTheme() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(emoticon)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC93DE95CU
    }
  }

  public data class TL_inputChatThemeUniqueGift(
    public val slug: String,
  ) : TlGen_InputChatTheme() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0x87E5DFE4U
    }
  }
}
