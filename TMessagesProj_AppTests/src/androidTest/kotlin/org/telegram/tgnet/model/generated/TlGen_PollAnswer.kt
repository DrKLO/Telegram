package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PollAnswer : TlGen_Object {
  public data class TL_pollAnswer(
    public val text: TlGen_TextWithEntities,
    public val option: List<Byte>,
  ) : TlGen_PollAnswer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      stream.writeByteArray(option.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xFF16E2CAU
    }
  }

  public data class TL_pollAnswer_layer178(
    public val text: String,
    public val option: List<Byte>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeByteArray(option.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x6CA9C2E9U
    }
  }
}
