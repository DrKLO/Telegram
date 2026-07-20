package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WallPaperSettings : TlGen_Object {
  public data class TL_wallPaperSettings(
    public val blur: Boolean,
    public val motion: Boolean,
    public val background_color: Int?,
    public val third_background_color: Int?,
    public val fourth_background_color: Int?,
    public val intensity: Int?,
    public val emoticon: String?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_WallPaperSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (background_color != null) result = result or 1U
        if (blur) result = result or 2U
        if (motion) result = result or 4U
        if (intensity != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (third_background_color != null) result = result or 32U
        if (fourth_background_color != null) result = result or 64U
        if (emoticon != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      background_color?.let { stream.writeInt32(it) }
      multiflags_4?.let { stream.writeInt32(it.second_background_color) }
      third_background_color?.let { stream.writeInt32(it) }
      fourth_background_color?.let { stream.writeInt32(it) }
      intensity?.let { stream.writeInt32(it) }
      multiflags_4?.let { stream.writeInt32(it.rotation) }
      emoticon?.let { stream.writeString(it) }
    }

    public data class Multiflags_4(
      public val second_background_color: Int,
      public val rotation: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x372EFCD0U
    }
  }

  public data class TL_wallPaperSettings_layer128(
    public val blur: Boolean,
    public val motion: Boolean,
    public val background_color: Int?,
    public val intensity: Int?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (background_color != null) result = result or 1U
        if (blur) result = result or 2U
        if (motion) result = result or 4U
        if (intensity != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      background_color?.let { stream.writeInt32(it) }
      multiflags_4?.let { stream.writeInt32(it.second_background_color) }
      intensity?.let { stream.writeInt32(it) }
      multiflags_4?.let { stream.writeInt32(it.rotation) }
    }

    public data class Multiflags_4(
      public val second_background_color: Int,
      public val rotation: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x05086CF8U
    }
  }

  public data class TL_wallPaperSettings_layer167(
    public val blur: Boolean,
    public val motion: Boolean,
    public val background_color: Int?,
    public val third_background_color: Int?,
    public val fourth_background_color: Int?,
    public val intensity: Int?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (background_color != null) result = result or 1U
        if (blur) result = result or 2U
        if (motion) result = result or 4U
        if (intensity != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (third_background_color != null) result = result or 32U
        if (fourth_background_color != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      background_color?.let { stream.writeInt32(it) }
      multiflags_4?.let { stream.writeInt32(it.second_background_color) }
      third_background_color?.let { stream.writeInt32(it) }
      fourth_background_color?.let { stream.writeInt32(it) }
      intensity?.let { stream.writeInt32(it) }
      multiflags_4?.let { stream.writeInt32(it.rotation) }
    }

    public data class Multiflags_4(
      public val second_background_color: Int,
      public val rotation: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x1DC1BCA4U
    }
  }
}
