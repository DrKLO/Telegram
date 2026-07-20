package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ThemeSettings : TlGen_Object {
  public data class TL_themeSettings(
    public val message_colors_animated: Boolean,
    public val base_theme: TlGen_BaseTheme,
    public val accent_color: Int,
    public val outbox_accent_color: Int?,
    public val message_colors: List<Int>?,
    public val wallpaper: TlGen_WallPaper?,
  ) : TlGen_ThemeSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (message_colors != null) result = result or 1U
        if (wallpaper != null) result = result or 2U
        if (message_colors_animated) result = result or 4U
        if (outbox_accent_color != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      base_theme.serializeToStream(stream)
      stream.writeInt32(accent_color)
      outbox_accent_color?.let { stream.writeInt32(it) }
      message_colors?.let { TlGen_Vector.serializeInt(stream, it) }
      wallpaper?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFA58B6D4U
    }
  }

  public data class TL_themeSettings_layer131(
    public val base_theme: TlGen_BaseTheme,
    public val accent_color: Int,
    public val wallpaper: TlGen_WallPaper?,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (wallpaper != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      base_theme.serializeToStream(stream)
      stream.writeInt32(accent_color)
      multiflags_0?.let { stream.writeInt32(it.message_top_color) }
      multiflags_0?.let { stream.writeInt32(it.message_bottom_color) }
      wallpaper?.serializeToStream(stream)
    }

    public data class Multiflags_0(
      public val message_top_color: Int,
      public val message_bottom_color: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x9C14984AU
    }
  }

  public data class TL_themeSettings_layer132(
    public val message_colors_animated: Boolean,
    public val base_theme: TlGen_BaseTheme,
    public val accent_color: Int,
    public val message_colors: List<Int>?,
    public val wallpaper: TlGen_WallPaper?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (message_colors != null) result = result or 1U
        if (wallpaper != null) result = result or 2U
        if (message_colors_animated) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      base_theme.serializeToStream(stream)
      stream.writeInt32(accent_color)
      message_colors?.let { TlGen_Vector.serializeInt(stream, it) }
      wallpaper?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8DB4E76CU
    }
  }
}
