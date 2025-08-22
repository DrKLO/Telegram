package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPeerNotifySettings : TlGen_Object {
  public data class TL_inputPeerNotifySettings(
    public val show_previews: Boolean?,
    public val silent: Boolean?,
    public val mute_until: Int?,
    public val sound: TlGen_NotificationSound?,
    public val stories_muted: Boolean?,
    public val stories_hide_sender: Boolean?,
    public val stories_sound: TlGen_NotificationSound?,
  ) : TlGen_InputPeerNotifySettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (show_previews != null) result = result or 1U
        if (silent != null) result = result or 2U
        if (mute_until != null) result = result or 4U
        if (sound != null) result = result or 8U
        if (stories_muted != null) result = result or 64U
        if (stories_hide_sender != null) result = result or 128U
        if (stories_sound != null) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      show_previews?.let { stream.writeBool(it) }
      silent?.let { stream.writeBool(it) }
      mute_until?.let { stream.writeInt32(it) }
      sound?.serializeToStream(stream)
      stories_muted?.let { stream.writeBool(it) }
      stories_hide_sender?.let { stream.writeBool(it) }
      stories_sound?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCACB6AE2U
    }
  }
}
