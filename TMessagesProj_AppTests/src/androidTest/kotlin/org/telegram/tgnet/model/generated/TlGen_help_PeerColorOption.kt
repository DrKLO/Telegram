package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_PeerColorOption : TlGen_Object {
  public data class TL_help_peerColorOption(
    public val hidden: Boolean,
    public val color_id: Int,
    public val colors: TlGen_help_PeerColorSet?,
    public val dark_colors: TlGen_help_PeerColorSet?,
    public val channel_min_level: Int?,
    public val group_min_level: Int?,
  ) : TlGen_help_PeerColorOption() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (hidden) result = result or 1U
        if (colors != null) result = result or 2U
        if (dark_colors != null) result = result or 4U
        if (channel_min_level != null) result = result or 8U
        if (group_min_level != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(color_id)
      colors?.serializeToStream(stream)
      dark_colors?.serializeToStream(stream)
      channel_min_level?.let { stream.writeInt32(it) }
      group_min_level?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xADEC6EBEU
    }
  }
}
