package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_stories_Albums : TlGen_Object {
  public data object TL_stories_albumsNotModified : TlGen_stories_Albums() {
    public const val MAGIC: UInt = 0x564EDAEBU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_stories_albums(
    public val hash: Long,
    public val albums: List<TlGen_StoryAlbum>,
  ) : TlGen_stories_Albums() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, albums)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC3987A3AU
    }
  }
}
