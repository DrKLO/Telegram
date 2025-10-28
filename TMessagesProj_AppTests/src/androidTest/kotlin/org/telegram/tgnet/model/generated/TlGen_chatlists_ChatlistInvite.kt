package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_chatlists_ChatlistInvite : TlGen_Object {
  public data class TL_chatlists_chatlistInviteAlready(
    public val filter_id: Int,
    public val missing_peers: List<TlGen_Peer>,
    public val already_peers: List<TlGen_Peer>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_chatlists_ChatlistInvite() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(filter_id)
      TlGen_Vector.serialize(stream, missing_peers)
      TlGen_Vector.serialize(stream, already_peers)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFA87F659U
    }
  }

  public data class TL_chatlists_chatlistInvite(
    public val title_noanimate: Boolean,
    public val title: TlGen_TextWithEntities,
    public val emoticon: String?,
    public val peers: List<TlGen_Peer>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_chatlists_ChatlistInvite() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (emoticon != null) result = result or 1U
        if (title_noanimate) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      title.serializeToStream(stream)
      emoticon?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, peers)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF10ECE2FU
    }
  }
}
