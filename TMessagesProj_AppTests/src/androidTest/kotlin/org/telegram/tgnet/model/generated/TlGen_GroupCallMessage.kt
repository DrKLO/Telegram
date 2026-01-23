package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GroupCallMessage : TlGen_Object {
  public data class TL_groupCallMessage(
    public val from_admin: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer,
    public val date: Int,
    public val message: TlGen_TextWithEntities,
    public val paid_message_stars: Long?,
  ) : TlGen_GroupCallMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (paid_message_stars != null) result = result or 1U
        if (from_admin) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id.serializeToStream(stream)
      stream.writeInt32(date)
      message.serializeToStream(stream)
      paid_message_stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x1A8AFC7EU
    }
  }
}
