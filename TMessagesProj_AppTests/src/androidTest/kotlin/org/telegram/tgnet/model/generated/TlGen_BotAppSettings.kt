package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BotAppSettings : TlGen_Object {
  public data class TL_botAppSettings(
    public val placeholder_path: List<Byte>?,
    public val background_color: Int?,
    public val background_dark_color: Int?,
    public val header_color: Int?,
    public val header_dark_color: Int?,
  ) : TlGen_BotAppSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (placeholder_path != null) result = result or 1U
        if (background_color != null) result = result or 2U
        if (background_dark_color != null) result = result or 4U
        if (header_color != null) result = result or 8U
        if (header_dark_color != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      placeholder_path?.let { stream.writeByteArray(it.toByteArray()) }
      background_color?.let { stream.writeInt32(it) }
      background_dark_color?.let { stream.writeInt32(it) }
      header_color?.let { stream.writeInt32(it) }
      header_dark_color?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC99B1950U
    }
  }
}
