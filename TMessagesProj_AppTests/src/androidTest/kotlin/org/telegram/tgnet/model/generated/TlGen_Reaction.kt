package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Reaction : TlGen_Object {
  public data object TL_reactionEmpty : TlGen_Reaction() {
    public const val MAGIC: UInt = 0x79F5D419U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_reactionEmoji(
    public val emoticon: String,
  ) : TlGen_Reaction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(emoticon)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1B2286B8U
    }
  }

  public data class TL_reactionCustomEmoji(
    public val document_id: Long,
  ) : TlGen_Reaction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(document_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8935FC73U
    }
  }

  public data object TL_reactionPaid : TlGen_Reaction() {
    public const val MAGIC: UInt = 0x523DA4EBU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
