package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Passkey : TlGen_Object {
  public data class TL_passkey(
    public val id: String,
    public val name: String,
    public val date: Int,
    public val software_emoji_id: Long?,
    public val last_usage_date: Int?,
  ) : TlGen_Passkey() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (software_emoji_id != null) result = result or 1U
        if (last_usage_date != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeString(name)
      stream.writeInt32(date)
      software_emoji_id?.let { stream.writeInt64(it) }
      last_usage_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x98613EBFU
    }
  }
}
