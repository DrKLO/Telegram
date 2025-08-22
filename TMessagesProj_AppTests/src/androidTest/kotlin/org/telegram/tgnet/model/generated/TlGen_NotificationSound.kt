package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_NotificationSound : TlGen_Object {
  public data object TL_notificationSoundDefault : TlGen_NotificationSound() {
    public const val MAGIC: UInt = 0x97E8BEBEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_notificationSoundNone : TlGen_NotificationSound() {
    public const val MAGIC: UInt = 0x6F0C34DFU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_notificationSoundLocal(
    public val title: String,
    public val `data`: String,
  ) : TlGen_NotificationSound() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      stream.writeString(data)
    }

    public companion object {
      public const val MAGIC: UInt = 0x830B9AE4U
    }
  }

  public data class TL_notificationSoundRingtone(
    public val id: Long,
  ) : TlGen_NotificationSound() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFF6C8049U
    }
  }
}
