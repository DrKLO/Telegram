package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputDialogPeer : TlGen_Object {
  public data class TL_inputDialogPeer(
    public val peer: TlGen_InputPeer,
  ) : TlGen_InputDialogPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFCAAFEB7U
    }
  }

  public data class TL_inputDialogPeerFolder(
    public val folder_id: Int,
  ) : TlGen_InputDialogPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(folder_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x64600527U
    }
  }
}
