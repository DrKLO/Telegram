package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_TopPeerCategoryPeers : TlGen_Object {
  public data class TL_topPeerCategoryPeers(
    public val category: TlGen_TopPeerCategory,
    public val count: Int,
    public val peers: List<TlGen_TopPeer>,
  ) : TlGen_TopPeerCategoryPeers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      category.serializeToStream(stream)
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, peers)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFB834291U
    }
  }
}
