package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_FolderPeer : TlGen_Object {
  public data class TL_folderPeer(
    public val peer: TlGen_Peer,
    public val folder_id: Int,
  ) : TlGen_FolderPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(folder_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE9BAA668U
    }
  }
}
