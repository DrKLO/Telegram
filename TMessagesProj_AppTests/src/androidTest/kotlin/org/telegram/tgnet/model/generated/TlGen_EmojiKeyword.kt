package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_EmojiKeyword : TlGen_Object {
  public data class TL_emojiKeyword(
    public val keyword: String,
    public val emoticons: List<String>,
  ) : TlGen_EmojiKeyword() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(keyword)
      TlGen_Vector.serializeString(stream, emoticons)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD5B3B9F9U
    }
  }

  public data class TL_emojiKeywordDeleted(
    public val keyword: String,
    public val emoticons: List<String>,
  ) : TlGen_EmojiKeyword() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(keyword)
      TlGen_Vector.serializeString(stream, emoticons)
    }

    public companion object {
      public const val MAGIC: UInt = 0x236DF622U
    }
  }
}
