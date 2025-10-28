package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelAdminLogEventsFilter : TlGen_Object {
  public data class TL_channelAdminLogEventsFilter(
    public val join: Boolean,
    public val leave: Boolean,
    public val invite: Boolean,
    public val ban: Boolean,
    public val unban: Boolean,
    public val kick: Boolean,
    public val unkick: Boolean,
    public val promote: Boolean,
    public val demote: Boolean,
    public val info: Boolean,
    public val settings: Boolean,
    public val pinned: Boolean,
    public val edit: Boolean,
    public val delete: Boolean,
    public val group_call: Boolean,
    public val invites: Boolean,
    public val send: Boolean,
    public val forums: Boolean,
    public val sub_extend: Boolean,
  ) : TlGen_ChannelAdminLogEventsFilter() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (join) result = result or 1U
        if (leave) result = result or 2U
        if (invite) result = result or 4U
        if (ban) result = result or 8U
        if (unban) result = result or 16U
        if (kick) result = result or 32U
        if (unkick) result = result or 64U
        if (promote) result = result or 128U
        if (demote) result = result or 256U
        if (info) result = result or 512U
        if (settings) result = result or 1024U
        if (pinned) result = result or 2048U
        if (edit) result = result or 4096U
        if (delete) result = result or 8192U
        if (group_call) result = result or 16384U
        if (invites) result = result or 32768U
        if (send) result = result or 65536U
        if (forums) result = result or 131072U
        if (sub_extend) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0xEA107AE4U
    }
  }
}
