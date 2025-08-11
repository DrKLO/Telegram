package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SponsoredPeer : TlGen_Object {
  public data class TL_sponsoredPeer(
    public val random_id: List<Byte>,
    public val peer: TlGen_Peer,
    public val sponsor_info: String?,
    public val additional_info: String?,
  ) : TlGen_SponsoredPeer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (sponsor_info != null) result = result or 1U
        if (additional_info != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeByteArray(random_id.toByteArray())
      peer.serializeToStream(stream)
      sponsor_info?.let { stream.writeString(it) }
      additional_info?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC69708D3U
    }
  }
}
