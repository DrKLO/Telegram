package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_PeerColorSet : TlGen_Object {
  public data class TL_help_peerColorSet(
    public val colors: List<Int>,
  ) : TlGen_help_PeerColorSet() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeInt(stream, colors)
    }

    public companion object {
      public const val MAGIC: UInt = 0x26219A58U
    }
  }

  public data class TL_help_peerColorProfileSet(
    public val palette_colors: List<Int>,
    public val bg_colors: List<Int>,
    public val story_colors: List<Int>,
  ) : TlGen_help_PeerColorSet() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeInt(stream, palette_colors)
      TlGen_Vector.serializeInt(stream, bg_colors)
      TlGen_Vector.serializeInt(stream, story_colors)
    }

    public companion object {
      public const val MAGIC: UInt = 0x767D61EBU
    }
  }
}
