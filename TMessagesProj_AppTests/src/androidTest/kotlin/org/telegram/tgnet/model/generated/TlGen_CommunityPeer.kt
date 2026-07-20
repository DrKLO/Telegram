package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_CommunityPeer : TlGen_Object {
  public data class TL_communityPeer(
    public val can_view_history: Boolean,
    public val visible: Boolean?,
    public val peer: TlGen_Peer,
  ) : TlGen_CommunityPeer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (visible != null) result = result or 1U
        if (can_view_history) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      visible?.let { stream.writeBool(it) }
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x76141EBDU
    }
  }
}
