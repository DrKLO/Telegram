package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelAdminRights : TlGen_Object {
  public data class TL_channelAdminRights_layer92(
    public val change_info: Boolean,
    public val post_messages: Boolean,
    public val edit_messages: Boolean,
    public val delete_messages: Boolean,
    public val ban_users: Boolean,
    public val invite_users: Boolean,
    public val invite_link: Boolean,
    public val pin_messages: Boolean,
    public val add_admins: Boolean,
  ) : TlGen_ChannelAdminRights() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (change_info) result = result or 1U
        if (post_messages) result = result or 2U
        if (edit_messages) result = result or 4U
        if (delete_messages) result = result or 8U
        if (ban_users) result = result or 16U
        if (invite_users) result = result or 32U
        if (invite_link) result = result or 64U
        if (pin_messages) result = result or 128U
        if (add_admins) result = result or 512U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x5D7CEBA5U
    }
  }
}
