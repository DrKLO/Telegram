package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PeerStories : TlGen_Object {
  public data class TL_peerStories(
    public val peer: TlGen_Peer,
    public val max_read_id: Int?,
    public val stories: List<TlGen_StoryItem>,
  ) : TlGen_PeerStories() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (max_read_id != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      max_read_id?.let { stream.writeInt32(it) }
      TlGen_Vector.serialize(stream, stories)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9A35E999U
    }
  }
}
