package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AttachMenuBot : TlGen_Object {
  public data class TL_attachMenuBot(
    public val inactive: Boolean,
    public val has_settings: Boolean,
    public val request_write_access: Boolean,
    public val show_in_side_menu: Boolean,
    public val side_menu_disclaimer_needed: Boolean,
    public val bot_id: Long,
    public val short_name: String,
    public val peer_types: List<TlGen_AttachMenuPeerType>?,
    public val icons: List<TlGen_AttachMenuBotIcon>,
  ) : TlGen_AttachMenuBot() {
    public val show_in_attach_menu: Boolean = peer_types != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (inactive) result = result or 1U
        if (has_settings) result = result or 2U
        if (request_write_access) result = result or 4U
        if (show_in_attach_menu) result = result or 8U
        if (show_in_side_menu) result = result or 16U
        if (side_menu_disclaimer_needed) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(bot_id)
      stream.writeString(short_name)
      peer_types?.let { TlGen_Vector.serialize(stream, it) }
      TlGen_Vector.serialize(stream, icons)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD90D8DFEU
    }
  }
}
