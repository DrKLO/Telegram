package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_communities_PeerLinkRequests : TlGen_Object {
  public data class TL_communities_peerLinkRequests(
    public val total_count: Int,
    public val requests: List<TlGen_CommunityPeerRequest>,
    public val next_offset: String?,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_communities_PeerLinkRequests() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(total_count)
      TlGen_Vector.serialize(stream, requests)
      next_offset?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2244AFADU
    }
  }
}
