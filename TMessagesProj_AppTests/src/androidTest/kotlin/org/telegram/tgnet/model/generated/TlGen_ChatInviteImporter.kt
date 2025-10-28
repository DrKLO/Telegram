package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatInviteImporter : TlGen_Object {
  public data class TL_chatInviteImporter(
    public val requested: Boolean,
    public val via_chatlist: Boolean,
    public val user_id: Long,
    public val date: Int,
    public val about: String?,
    public val approved_by: Long?,
  ) : TlGen_ChatInviteImporter() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (requested) result = result or 1U
        if (approved_by != null) result = result or 2U
        if (about != null) result = result or 4U
        if (via_chatlist) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      stream.writeInt32(date)
      about?.let { stream.writeString(it) }
      approved_by?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x8C5ADFD9U
    }
  }
}
