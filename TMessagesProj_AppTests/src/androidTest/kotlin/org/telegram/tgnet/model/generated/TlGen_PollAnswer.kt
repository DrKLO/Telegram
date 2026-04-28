package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
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
    public val media: TlGen_MessageMedia?,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_PollAnswer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (media != null) result = result or 1U
        if (multiflags_1 != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      text.serializeToStream(stream)
      stream.writeByteArray(option.toByteArray())
      media?.serializeToStream(stream)
      multiflags_1?.let { it.added_by.serializeToStream(stream) }
      multiflags_1?.let { stream.writeInt32(it.date) }
    }

    public data class Multiflags_1(
      public val added_by: TlGen_Peer,
      public val date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x4B7D786AU
    }
  }

  public data class TL_inputPollAnswer(
    public val text: TlGen_TextWithEntities,
    public val media: TlGen_InputMedia?,
  ) : TlGen_PollAnswer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (media != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      text.serializeToStream(stream)
      media?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x199FED96U
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

  public data class TL_pollAnswer_layer223(
    public val text: TlGen_TextWithEntities,
    public val option: List<Byte>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      stream.writeByteArray(option.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xFF16E2CAU
    }
  }
}
