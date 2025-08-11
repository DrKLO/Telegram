package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ExportedChatlistInvite : TlGen_Object {
  public data class TL_exportedChatlistInvite(
    public val title: String,
    public val url: String,
    public val peers: List<TlGen_Peer>,
  ) : TlGen_ExportedChatlistInvite() {
    internal val flags: UInt
      get() {
        var result = 0U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(title)
      stream.writeString(url)
      TlGen_Vector.serialize(stream, peers)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0C5181ACU
    }
  }
}
