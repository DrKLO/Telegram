package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ReactionsNotifySettings : TlGen_Object {
  public data class TL_reactionsNotifySettings(
    public val messages_notify_from: TlGen_ReactionNotificationsFrom?,
    public val stories_notify_from: TlGen_ReactionNotificationsFrom?,
    public val sound: TlGen_NotificationSound,
    public val show_previews: Boolean,
  ) : TlGen_ReactionsNotifySettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (messages_notify_from != null) result = result or 1U
        if (stories_notify_from != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      messages_notify_from?.serializeToStream(stream)
      stories_notify_from?.serializeToStream(stream)
      sound.serializeToStream(stream)
      stream.writeBool(show_previews)
    }

    public companion object {
      public const val MAGIC: UInt = 0x56E34970U
    }
  }
}
