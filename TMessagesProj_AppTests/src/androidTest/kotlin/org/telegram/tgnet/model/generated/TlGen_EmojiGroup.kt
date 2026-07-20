package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_EmojiGroup : TlGen_Object {
  public data class TL_emojiGroup(
    public val title: String,
    public val icon_emoji_id: Long,
    public val emoticons: List<String>,
  ) : TlGen_EmojiGroup() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      stream.writeInt64(icon_emoji_id)
      TlGen_Vector.serializeString(stream, emoticons)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7A9ABDA9U
    }
  }

  public data class TL_emojiGroupGreeting(
    public val title: String,
    public val icon_emoji_id: Long,
    public val emoticons: List<String>,
  ) : TlGen_EmojiGroup() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      stream.writeInt64(icon_emoji_id)
      TlGen_Vector.serializeString(stream, emoticons)
    }

    public companion object {
      public const val MAGIC: UInt = 0x80D26CC7U
    }
  }

  public data class TL_emojiGroupPremium(
    public val title: String,
    public val icon_emoji_id: Long,
  ) : TlGen_EmojiGroup() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      stream.writeInt64(icon_emoji_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x093BCF34U
    }
  }
}
