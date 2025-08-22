package org.telegram.tgnet.model.generated

import kotlin.Double
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_TopPeer : TlGen_Object {
  public data class TL_topPeer(
    public val peer: TlGen_Peer,
    public val rating: Double,
  ) : TlGen_TopPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeDouble(rating)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEDCDC05BU
    }
  }
}
