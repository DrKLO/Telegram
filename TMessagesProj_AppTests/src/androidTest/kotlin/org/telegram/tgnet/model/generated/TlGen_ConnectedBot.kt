package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ConnectedBot : TlGen_Object {
  public data class TL_connectedBot(
    public val bot_id: Long,
    public val recipients: TlGen_BusinessBotRecipients,
    public val rights: TlGen_BusinessBotRights,
    public val device: String?,
    public val date: Int?,
    public val location: String?,
  ) : TlGen_ConnectedBot() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (device != null) result = result or 1U
        if (date != null) result = result or 2U
        if (location != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(bot_id)
      recipients.serializeToStream(stream)
      rights.serializeToStream(stream)
      device?.let { stream.writeString(it) }
      date?.let { stream.writeInt32(it) }
      location?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x033ED001U
    }
  }
}
