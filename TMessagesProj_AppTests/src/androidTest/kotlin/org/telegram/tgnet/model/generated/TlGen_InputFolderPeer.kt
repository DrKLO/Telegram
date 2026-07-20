package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputFolderPeer : TlGen_Object {
  public data class TL_inputFolderPeer(
    public val peer: TlGen_InputPeer,
    public val folder_id: Int,
  ) : TlGen_InputFolderPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(folder_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFBD2C296U
    }
  }
}
