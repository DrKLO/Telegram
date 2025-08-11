package org.telegram.tgnet.model.generated

import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_VideoSize : TlGen_Object {
  public data class TL_videoSize(
    public val type: String,
    public val w: Int,
    public val h: Int,
    public val size: Int,
    public val video_start_ts: Double?,
  ) : TlGen_VideoSize() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (video_start_ts != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(type)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
      video_start_ts?.let { stream.writeDouble(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xDE33B094U
    }
  }

  public data class TL_videoSizeEmojiMarkup(
    public val emoji_id: Long,
    public val background_colors: List<Int>,
  ) : TlGen_VideoSize() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(emoji_id)
      TlGen_Vector.serializeInt(stream, background_colors)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF85C413CU
    }
  }

  public data class TL_videoSizeStickerMarkup(
    public val stickerset: TlGen_InputStickerSet,
    public val sticker_id: Long,
    public val background_colors: List<Int>,
  ) : TlGen_VideoSize() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stickerset.serializeToStream(stream)
      stream.writeInt64(sticker_id)
      TlGen_Vector.serializeInt(stream, background_colors)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0DA082FEU
    }
  }

  public data class TL_videoSize_layer115(
    public val type: String,
    public val location: TlGen_FileLocation,
    public val w: Int,
    public val h: Int,
    public val size: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
      location.serializeToStream(stream)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
    }

    public companion object {
      public const val MAGIC: UInt = 0x435BB987U
    }
  }

  public data class TL_videoSize_layer127(
    public val type: String,
    public val location: TlGen_FileLocation,
    public val w: Int,
    public val h: Int,
    public val size: Int,
    public val video_start_ts: Double?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (video_start_ts != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(type)
      location.serializeToStream(stream)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
      video_start_ts?.let { stream.writeDouble(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xE831C556U
    }
  }
}
