package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DocumentAttribute : TlGen_Object {
  public data class TL_documentAttributeImageSize(
    public val w: Int,
    public val h: Int,
  ) : TlGen_DocumentAttribute() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(w)
      stream.writeInt32(h)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6C37C15CU
    }
  }

  public data object TL_documentAttributeAnimated : TlGen_DocumentAttribute() {
    public const val MAGIC: UInt = 0x11B58939U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_documentAttributeFilename(
    public val file_name: String,
  ) : TlGen_DocumentAttribute() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(file_name)
    }

    public companion object {
      public const val MAGIC: UInt = 0x15590068U
    }
  }

  public data class TL_documentAttributeAudio(
    public val voice: Boolean,
    public val duration: Int,
    public val title: String?,
    public val performer: String?,
    public val waveform: List<Byte>?,
  ) : TlGen_DocumentAttribute() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (performer != null) result = result or 2U
        if (waveform != null) result = result or 4U
        if (voice) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(duration)
      title?.let { stream.writeString(it) }
      performer?.let { stream.writeString(it) }
      waveform?.let { stream.writeByteArray(it.toByteArray()) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x9852F9C6U
    }
  }

  public data class TL_documentAttributeSticker(
    public val mask: Boolean,
    public val alt: String,
    public val stickerset: TlGen_InputStickerSet,
    public val mask_coords: TlGen_MaskCoords?,
  ) : TlGen_DocumentAttribute() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (mask_coords != null) result = result or 1U
        if (mask) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(alt)
      stickerset.serializeToStream(stream)
      mask_coords?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6319D612U
    }
  }

  public data object TL_documentAttributeHasStickers : TlGen_DocumentAttribute() {
    public const val MAGIC: UInt = 0x9801D2F7U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_documentAttributeCustomEmoji(
    public val free: Boolean,
    public val text_color: Boolean,
    public val alt: String,
    public val stickerset: TlGen_InputStickerSet,
  ) : TlGen_DocumentAttribute() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (free) result = result or 1U
        if (text_color) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(alt)
      stickerset.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFD149899U
    }
  }

  public data class TL_documentAttributeVideo(
    public val round_message: Boolean,
    public val supports_streaming: Boolean,
    public val nosound: Boolean,
    public val duration: Double,
    public val w: Int,
    public val h: Int,
    public val preload_prefix_size: Int?,
    public val video_start_ts: Double?,
    public val video_codec: String?,
  ) : TlGen_DocumentAttribute() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (round_message) result = result or 1U
        if (supports_streaming) result = result or 2U
        if (preload_prefix_size != null) result = result or 4U
        if (nosound) result = result or 8U
        if (video_start_ts != null) result = result or 16U
        if (video_codec != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeDouble(duration)
      stream.writeInt32(w)
      stream.writeInt32(h)
      preload_prefix_size?.let { stream.writeInt32(it) }
      video_start_ts?.let { stream.writeDouble(it) }
      video_codec?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x43C57C48U
    }
  }

  public data object TL_documentAttributeSticker_layer24 : TlGen_Object {
    public const val MAGIC: UInt = 0xFB0A5727U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_documentAttributeVideo_layer65(
    public val duration: Int,
    public val w: Int,
    public val h: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(duration)
      stream.writeInt32(w)
      stream.writeInt32(h)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5910CCCBU
    }
  }

  public data class TL_documentAttributeAudio_layer31(
    public val duration: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(duration)
    }

    public companion object {
      public const val MAGIC: UInt = 0x051448E5U
    }
  }

  public data class TL_documentAttributeSticker_layer28(
    public val alt: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(alt)
    }

    public companion object {
      public const val MAGIC: UInt = 0x994C9882U
    }
  }

  public data class TL_documentAttributeSticker_layer55(
    public val alt: String,
    public val stickerset: TlGen_InputStickerSet,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(alt)
      stickerset.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3A556302U
    }
  }

  public data class TL_documentAttributeAudio_layer45(
    public val duration: Int,
    public val title: String,
    public val performer: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(duration)
      stream.writeString(title)
      stream.writeString(performer)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDED218E0U
    }
  }

  public data class TL_documentAttributeVideo_layer159(
    public val round_message: Boolean,
    public val supports_streaming: Boolean,
    public val duration: Int,
    public val w: Int,
    public val h: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (round_message) result = result or 1U
        if (supports_streaming) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(duration)
      stream.writeInt32(w)
      stream.writeInt32(h)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0EF02CE6U
    }
  }

  public data class TL_documentAttributeVideo_layer184(
    public val round_message: Boolean,
    public val supports_streaming: Boolean,
    public val nosound: Boolean,
    public val duration: Double,
    public val w: Int,
    public val h: Int,
    public val preload_prefix_size: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (round_message) result = result or 1U
        if (supports_streaming) result = result or 2U
        if (preload_prefix_size != null) result = result or 4U
        if (nosound) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeDouble(duration)
      stream.writeInt32(w)
      stream.writeInt32(h)
      preload_prefix_size?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD38FF1C2U
    }
  }

  public data class TL_documentAttributeVideo_layer187(
    public val round_message: Boolean,
    public val supports_streaming: Boolean,
    public val nosound: Boolean,
    public val duration: Double,
    public val w: Int,
    public val h: Int,
    public val preload_prefix_size: Int?,
    public val video_start_ts: Double?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (round_message) result = result or 1U
        if (supports_streaming) result = result or 2U
        if (preload_prefix_size != null) result = result or 4U
        if (nosound) result = result or 8U
        if (video_start_ts != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeDouble(duration)
      stream.writeInt32(w)
      stream.writeInt32(h)
      preload_prefix_size?.let { stream.writeInt32(it) }
      video_start_ts?.let { stream.writeDouble(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x17399FADU
    }
  }
}
