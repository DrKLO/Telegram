package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputThemeSettings : TlGen_Object {
  public data class TL_inputThemeSettings(
    public val message_colors_animated: Boolean,
    public val base_theme: TlGen_BaseTheme,
    public val accent_color: Int,
    public val outbox_accent_color: Int?,
    public val message_colors: List<Int>?,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_InputThemeSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (message_colors != null) result = result or 1U
        if (multiflags_1 != null) result = result or 2U
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
      multiflags_1?.let { it.wallpaper.serializeToStream(stream) }
      multiflags_1?.let { it.wallpaper_settings.serializeToStream(stream) }
    }

    public data class Multiflags_1(
      public val wallpaper: TlGen_InputWallPaper,
      public val wallpaper_settings: TlGen_WallPaperSettings,
    )

    public companion object {
      public const val MAGIC: UInt = 0x8FDE504FU
    }
  }
}
