package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PeerNotifySettings : TlGen_Object {
  public data class TL_peerNotifySettings(
    public val show_previews: Boolean?,
    public val silent: Boolean?,
    public val mute_until: Int?,
    public val ios_sound: TlGen_NotificationSound?,
    public val android_sound: TlGen_NotificationSound?,
    public val other_sound: TlGen_NotificationSound?,
    public val stories_muted: Boolean?,
    public val stories_hide_sender: Boolean?,
    public val stories_ios_sound: TlGen_NotificationSound?,
    public val stories_android_sound: TlGen_NotificationSound?,
    public val stories_other_sound: TlGen_NotificationSound?,
  ) : TlGen_PeerNotifySettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (show_previews != null) result = result or 1U
        if (silent != null) result = result or 2U
        if (mute_until != null) result = result or 4U
        if (ios_sound != null) result = result or 8U
        if (android_sound != null) result = result or 16U
        if (other_sound != null) result = result or 32U
        if (stories_muted != null) result = result or 64U
        if (stories_hide_sender != null) result = result or 128U
        if (stories_ios_sound != null) result = result or 256U
        if (stories_android_sound != null) result = result or 512U
        if (stories_other_sound != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      show_previews?.let { stream.writeBool(it) }
      silent?.let { stream.writeBool(it) }
      mute_until?.let { stream.writeInt32(it) }
      ios_sound?.serializeToStream(stream)
      android_sound?.serializeToStream(stream)
      other_sound?.serializeToStream(stream)
      stories_muted?.let { stream.writeBool(it) }
      stories_hide_sender?.let { stream.writeBool(it) }
      stories_ios_sound?.serializeToStream(stream)
      stories_android_sound?.serializeToStream(stream)
      stories_other_sound?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x99622C0CU
    }
  }

  public data class TL_peerNotifySettings_layer1(
    public val mute_until: Int,
    public val sound: String,
    public val show_previews: Boolean,
    public val events: TlGen_PeerNotifyEvents,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(mute_until)
      stream.writeString(sound)
      stream.writeBool(show_previews)
      events.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDDBCD4A5U
    }
  }

  public data class TL_peerNotifySettings_layer47(
    public val mute_until: Int,
    public val sound: String,
    public val show_previews: Boolean,
    public val events_mask: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(mute_until)
      stream.writeString(sound)
      stream.writeBool(show_previews)
      stream.writeInt32(events_mask)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8D5E11EEU
    }
  }

  public data class TL_peerNotifySettings_layer78(
    public val show_previews: Boolean,
    public val silent: Boolean,
    public val mute_until: Int,
    public val sound: String,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (show_previews) result = result or 1U
        if (silent) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(mute_until)
      stream.writeString(sound)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9ACDA4C0U
    }
  }

  public data class TL_peerNotifySettings_layer139(
    public val show_previews: Boolean?,
    public val silent: Boolean?,
    public val mute_until: Int?,
    public val sound: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (show_previews != null) result = result or 1U
        if (silent != null) result = result or 2U
        if (mute_until != null) result = result or 4U
        if (sound != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      show_previews?.let { stream.writeBool(it) }
      silent?.let { stream.writeBool(it) }
      mute_until?.let { stream.writeInt32(it) }
      sound?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xAF509D20U
    }
  }

  public data class TL_peerNotifySettings_layer159(
    public val show_previews: Boolean?,
    public val silent: Boolean?,
    public val mute_until: Int?,
    public val ios_sound: TlGen_NotificationSound?,
    public val android_sound: TlGen_NotificationSound?,
    public val other_sound: TlGen_NotificationSound?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (show_previews != null) result = result or 1U
        if (silent != null) result = result or 2U
        if (mute_until != null) result = result or 4U
        if (ios_sound != null) result = result or 8U
        if (android_sound != null) result = result or 16U
        if (other_sound != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      show_previews?.let { stream.writeBool(it) }
      silent?.let { stream.writeBool(it) }
      mute_until?.let { stream.writeInt32(it) }
      ios_sound?.serializeToStream(stream)
      android_sound?.serializeToStream(stream)
      other_sound?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA83B0426U
    }
  }
}
