package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessBotRecipients : TlGen_Object {
  public data class TL_businessBotRecipients(
    public val existing_chats: Boolean,
    public val new_chats: Boolean,
    public val contacts: Boolean,
    public val non_contacts: Boolean,
    public val exclude_selected: Boolean,
    public val users: List<Long>?,
    public val exclude_users: List<Long>?,
  ) : TlGen_BusinessBotRecipients() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (existing_chats) result = result or 1U
        if (new_chats) result = result or 2U
        if (contacts) result = result or 4U
        if (non_contacts) result = result or 8U
        if (users != null) result = result or 16U
        if (exclude_selected) result = result or 32U
        if (exclude_users != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      users?.let { TlGen_Vector.serializeLong(stream, it) }
      exclude_users?.let { TlGen_Vector.serializeLong(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB88CF373U
    }
  }
}
