package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ReactionNotificationsFrom : TlGen_Object {
  public data object TL_reactionNotificationsFromContacts : TlGen_ReactionNotificationsFrom() {
    public const val MAGIC: UInt = 0xBAC3A61AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_reactionNotificationsFromAll : TlGen_ReactionNotificationsFrom() {
    public const val MAGIC: UInt = 0x4B9E22A0U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
