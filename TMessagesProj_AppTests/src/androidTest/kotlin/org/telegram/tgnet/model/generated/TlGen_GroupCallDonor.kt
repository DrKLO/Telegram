package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GroupCallDonor : TlGen_Object {
  public data class TL_groupCallDonor(
    public val top: Boolean,
    public val my: Boolean,
    public val anonymous: Boolean,
    public val peer_id: TlGen_Peer?,
    public val stars: Long,
  ) : TlGen_GroupCallDonor() {
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
      stream.writeInt64(stars)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEE430C85U
    }
  }
}
