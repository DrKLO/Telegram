package org.Tajgram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputAiComposeTone : TlGen_Object {
  public data class TL_inputAiComposeToneDefault(
    public val tone: String,
  ) : TlGen_InputAiComposeTone() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(tone)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1FE9A9BFU
    }
  }

  public data class TL_inputAiComposeToneID(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputAiComposeTone() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0773C080U
    }
  }

  public data class TL_inputAiComposeToneSlug(
    public val slug: String,
  ) : TlGen_InputAiComposeTone() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1FA01357U
    }
  }
}
