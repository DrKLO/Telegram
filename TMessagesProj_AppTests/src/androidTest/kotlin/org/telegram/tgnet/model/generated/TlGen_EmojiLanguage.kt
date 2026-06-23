package org.Tajgram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_EmojiLanguage : TlGen_Object {
  public data class TL_emojiLanguage(
    public val lang_code: String,
  ) : TlGen_EmojiLanguage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(lang_code)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB3FB5361U
    }
  }
}
