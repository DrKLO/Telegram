package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessBotRights : TlGen_Object {
  public data class TL_businessBotRights(
    public val reply: Boolean,
    public val read_messages: Boolean,
    public val delete_sent_messages: Boolean,
    public val delete_received_messages: Boolean,
    public val edit_name: Boolean,
    public val edit_bio: Boolean,
    public val edit_profile_photo: Boolean,
    public val edit_username: Boolean,
    public val view_gifts: Boolean,
    public val sell_gifts: Boolean,
    public val change_gift_settings: Boolean,
    public val transfer_and_upgrade_gifts: Boolean,
    public val transfer_stars: Boolean,
    public val manage_stories: Boolean,
  ) : TlGen_BusinessBotRights() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply) result = result or 1U
        if (read_messages) result = result or 2U
        if (delete_sent_messages) result = result or 4U
        if (delete_received_messages) result = result or 8U
        if (edit_name) result = result or 16U
        if (edit_bio) result = result or 32U
        if (edit_profile_photo) result = result or 64U
        if (edit_username) result = result or 128U
        if (view_gifts) result = result or 256U
        if (sell_gifts) result = result or 512U
        if (change_gift_settings) result = result or 1024U
        if (transfer_and_upgrade_gifts) result = result or 2048U
        if (transfer_stars) result = result or 4096U
        if (manage_stories) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0xA0624CF7U
    }
  }
}
