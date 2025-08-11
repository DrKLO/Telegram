package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_photos_Photos : TlGen_Object {
  public data class TL_photos_photos(
    public val photos: List<TlGen_Photo>,
    public val users: List<TlGen_User>,
  ) : TlGen_photos_Photos() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8DCA6AA5U
    }
  }

  public data class TL_photos_photosSlice(
    public val count: Int,
    public val photos: List<TlGen_Photo>,
    public val users: List<TlGen_User>,
  ) : TlGen_photos_Photos() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x15051F54U
    }
  }
}
