package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PeerLocated : TlGen_Object {
  public data class TL_peerLocated(
    public val peer: TlGen_Peer,
    public val expires: Int,
    public val distance: Int,
  ) : TlGen_PeerLocated() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(expires)
      stream.writeInt32(distance)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCA461B5DU
    }
  }

  public data class TL_peerSelfLocated(
    public val expires: Int,
  ) : TlGen_PeerLocated() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(expires)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF8EC284BU
    }
  }
}
