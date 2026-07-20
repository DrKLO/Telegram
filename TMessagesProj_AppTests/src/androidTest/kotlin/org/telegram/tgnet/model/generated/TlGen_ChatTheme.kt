package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatTheme : TlGen_Object {
  public data class TL_chatTheme(
    public val emoticon: String,
  ) : TlGen_ChatTheme() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(emoticon)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC3DFFC04U
    }
  }

  public data class TL_chatThemeUniqueGift(
    public val gift: TlGen_StarGift,
    public val theme_settings: List<TlGen_ThemeSettings>,
  ) : TlGen_ChatTheme() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      gift.serializeToStream(stream)
      TlGen_Vector.serialize(stream, theme_settings)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3458F9C8U
    }
  }
}
