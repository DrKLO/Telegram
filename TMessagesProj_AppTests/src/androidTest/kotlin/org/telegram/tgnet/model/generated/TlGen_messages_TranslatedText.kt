package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_TranslatedText : TlGen_Object {
  public data class TL_messages_translateResult(
    public val result: List<TlGen_TextWithEntities>,
  ) : TlGen_messages_TranslatedText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, result)
    }

    public companion object {
      public const val MAGIC: UInt = 0x33DB32F8U
    }
  }
}
