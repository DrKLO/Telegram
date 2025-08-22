package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatAdminRights : TlGen_Object {
  public data class TL_chatAdminRights(
    public val change_info: Boolean,
    public val post_messages: Boolean,
    public val edit_messages: Boolean,
    public val delete_messages: Boolean,
    public val ban_users: Boolean,
    public val invite_users: Boolean,
    public val pin_messages: Boolean,
    public val add_admins: Boolean,
    public val anonymous: Boolean,
    public val manage_call: Boolean,
    public val other: Boolean,
    public val manage_topics: Boolean,
    public val post_stories: Boolean,
    public val edit_stories: Boolean,
    public val delete_stories: Boolean,
    public val manage_direct_messages: Boolean,
  ) : TlGen_ChatAdminRights() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (change_info) result = result or 1U
        if (post_messages) result = result or 2U
        if (edit_messages) result = result or 4U
        if (delete_messages) result = result or 8U
        if (ban_users) result = result or 16U
        if (invite_users) result = result or 32U
        if (pin_messages) result = result or 128U
        if (add_admins) result = result or 512U
        if (anonymous) result = result or 1024U
        if (manage_call) result = result or 2048U
        if (other) result = result or 4096U
        if (manage_topics) result = result or 8192U
        if (post_stories) result = result or 16384U
        if (edit_stories) result = result or 32768U
        if (delete_stories) result = result or 65536U
        if (manage_direct_messages) result = result or 131072U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x5FB224D5U
    }
  }
}
