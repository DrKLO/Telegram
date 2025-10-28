package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessageExtendedMedia : TlGen_Object {
  public data class TL_messageExtendedMediaPreview(
    public val thumb: TlGen_PhotoSize?,
    public val video_duration: Int?,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_MessageExtendedMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (thumb != null) result = result or 2U
        if (video_duration != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      multiflags_0?.let { stream.writeInt32(it.w) }
      multiflags_0?.let { stream.writeInt32(it.h) }
      thumb?.serializeToStream(stream)
      video_duration?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_0(
      public val w: Int,
      public val h: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xAD628CC8U
    }
  }

  public data class TL_messageExtendedMedia(
    public val media: TlGen_MessageMedia,
  ) : TlGen_MessageExtendedMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      media.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEE479C64U
    }
  }
}
