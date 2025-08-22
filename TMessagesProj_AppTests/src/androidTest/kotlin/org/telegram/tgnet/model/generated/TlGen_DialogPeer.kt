package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DialogPeer : TlGen_Object {
  public data class TL_dialogPeer(
    public val peer: TlGen_Peer,
  ) : TlGen_DialogPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE56DBF05U
    }
  }

  public data class TL_dialogPeerFolder(
    public val folder_id: Int,
  ) : TlGen_DialogPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(folder_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x514519E2U
    }
  }
}
