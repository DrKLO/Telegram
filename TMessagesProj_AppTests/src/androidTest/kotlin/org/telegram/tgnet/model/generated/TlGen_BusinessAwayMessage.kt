package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessAwayMessage : TlGen_Object {
  public data class TL_businessAwayMessage(
    public val offline_only: Boolean,
    public val shortcut_id: Int,
    public val schedule: TlGen_BusinessAwayMessageSchedule,
    public val recipients: TlGen_BusinessRecipients,
  ) : TlGen_BusinessAwayMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (offline_only) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(shortcut_id)
      schedule.serializeToStream(stream)
      recipients.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEF156A5CU
    }
  }
}
