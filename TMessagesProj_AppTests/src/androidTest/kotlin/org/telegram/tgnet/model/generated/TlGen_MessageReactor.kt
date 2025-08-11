package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessageReactor : TlGen_Object {
  public data class TL_messageReactor(
    public val top: Boolean,
    public val my: Boolean,
    public val anonymous: Boolean,
    public val peer_id: TlGen_Peer?,
    public val count: Int,
  ) : TlGen_MessageReactor() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top) result = result or 1U
        if (my) result = result or 2U
        if (anonymous) result = result or 4U
        if (peer_id != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer_id?.serializeToStream(stream)
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4BA3A95AU
    }
  }
}
