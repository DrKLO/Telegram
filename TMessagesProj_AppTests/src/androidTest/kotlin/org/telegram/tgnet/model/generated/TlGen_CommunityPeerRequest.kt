package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_CommunityPeerRequest : TlGen_Object {
  public data class TL_communityPeerRequest(
    public val visible: Boolean,
    public val peer: TlGen_Peer,
    public val requested_by: Long,
    public val date: Int,
  ) : TlGen_CommunityPeerRequest() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (visible) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt64(requested_by)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7BEAFA85U
    }
  }
}
