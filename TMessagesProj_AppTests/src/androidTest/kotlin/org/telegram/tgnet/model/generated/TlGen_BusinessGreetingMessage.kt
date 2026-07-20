package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessGreetingMessage : TlGen_Object {
  public data class TL_businessGreetingMessage(
    public val shortcut_id: Int,
    public val recipients: TlGen_BusinessRecipients,
    public val no_activity_days: Int,
  ) : TlGen_BusinessGreetingMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(shortcut_id)
      recipients.serializeToStream(stream)
      stream.writeInt32(no_activity_days)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE519ABABU
    }
  }
}
