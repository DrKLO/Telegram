package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_FeedPosition : TlGen_Object {
  public data class TL_feedPosition(
    public val date: Int,
    public val peer: TlGen_Peer,
    public val id: Int,
  ) : TlGen_FeedPosition() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      stream.writeInt32(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5059DC73U
    }
  }
}
