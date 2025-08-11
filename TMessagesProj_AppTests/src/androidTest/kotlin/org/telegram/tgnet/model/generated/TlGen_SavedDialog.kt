package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SavedDialog : TlGen_Object {
  public data class TL_savedDialog(
    public val pinned: Boolean,
    public val peer: TlGen_Peer,
    public val top_message: Int,
  ) : TlGen_SavedDialog() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pinned) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(top_message)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBD87CB6CU
    }
  }

  public data class TL_monoForumDialog(
    public val unread_mark: Boolean,
    public val nopaid_messages_exception: Boolean,
    public val peer: TlGen_Peer,
    public val top_message: Int,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val unread_reactions_count: Int,
    public val draft: TlGen_DraftMessage?,
  ) : TlGen_SavedDialog() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (draft != null) result = result or 2U
        if (unread_mark) result = result or 8U
        if (nopaid_messages_exception) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(top_message)
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      stream.writeInt32(unread_reactions_count)
      draft?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x64407EA7U
    }
  }
}
