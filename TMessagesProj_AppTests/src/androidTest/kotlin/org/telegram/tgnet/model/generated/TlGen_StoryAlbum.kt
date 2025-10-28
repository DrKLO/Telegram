package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StoryAlbum : TlGen_Object {
  public data class TL_storyAlbum(
    public val album_id: Int,
    public val title: String,
    public val icon_photo: TlGen_Photo?,
    public val icon_video: TlGen_Document?,
  ) : TlGen_StoryAlbum() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (icon_photo != null) result = result or 1U
        if (icon_video != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(album_id)
      stream.writeString(title)
      icon_photo?.serializeToStream(stream)
      icon_video?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9325705AU
    }
  }
}
