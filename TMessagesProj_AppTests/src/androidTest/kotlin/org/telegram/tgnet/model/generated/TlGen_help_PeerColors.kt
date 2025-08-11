package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_PeerColors : TlGen_Object {
  public data object TL_help_peerColorsNotModified : TlGen_help_PeerColors() {
    public const val MAGIC: UInt = 0x2BA1F5CEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_help_peerColors(
    public val hash: Int,
    public val colors: List<TlGen_help_PeerColorOption>,
  ) : TlGen_help_PeerColors() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(hash)
      TlGen_Vector.serialize(stream, colors)
    }

    public companion object {
      public const val MAGIC: UInt = 0x00F8ED08U
    }
  }
}
