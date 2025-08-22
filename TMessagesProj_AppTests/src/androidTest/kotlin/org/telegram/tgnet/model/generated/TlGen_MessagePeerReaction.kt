package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessagePeerReaction : TlGen_Object {
  public data class TL_messagePeerReaction(
    public val big: Boolean,
    public val unread: Boolean,
    public val my: Boolean,
    public val peer_id: TlGen_Peer,
    public val date: Int,
    public val reaction: TlGen_Reaction,
  ) : TlGen_MessagePeerReaction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (big) result = result or 1U
        if (unread) result = result or 2U
        if (my) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer_id.serializeToStream(stream)
      stream.writeInt32(date)
      reaction.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8C79B63CU
    }
  }

  public data class TL_messagePeerReaction_layer144(
    public val big: Boolean,
    public val unread: Boolean,
    public val peer_id: TlGen_Peer,
    public val reaction: String,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (big) result = result or 1U
        if (unread) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer_id.serializeToStream(stream)
      stream.writeString(reaction)
    }

    public companion object {
      public const val MAGIC: UInt = 0x51B67EFFU
    }
  }

  public data class TL_messagePeerReaction_layer154(
    public val big: Boolean,
    public val unread: Boolean,
    public val peer_id: TlGen_Peer,
    public val reaction: TlGen_Reaction,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (big) result = result or 1U
        if (unread) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer_id.serializeToStream(stream)
      reaction.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB156FE9CU
    }
  }
}
