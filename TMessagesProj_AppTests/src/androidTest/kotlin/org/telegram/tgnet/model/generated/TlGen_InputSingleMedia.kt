package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputSingleMedia : TlGen_Object {
  public data class TL_inputSingleMedia(
    public val media: TlGen_InputMedia,
    public val random_id: Long,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
  ) : TlGen_InputSingleMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (entities != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      media.serializeToStream(stream)
      stream.writeInt64(random_id)
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x1CC6E91FU
    }
  }
}
