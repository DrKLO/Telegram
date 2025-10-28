package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_SavedGifs : TlGen_Object {
  public data object TL_messages_savedGifsNotModified : TlGen_messages_SavedGifs() {
    public const val MAGIC: UInt = 0xE8025CA2U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_savedGifs(
    public val hash: Long,
    public val gifs: List<TlGen_Document>,
  ) : TlGen_messages_SavedGifs() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, gifs)
    }

    public companion object {
      public const val MAGIC: UInt = 0x84A02A0DU
    }
  }
}
