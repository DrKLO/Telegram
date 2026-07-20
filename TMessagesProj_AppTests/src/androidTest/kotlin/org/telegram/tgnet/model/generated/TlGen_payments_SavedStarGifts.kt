package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_SavedStarGifts : TlGen_Object {
  public data class TL_payments_savedStarGifts(
    public val count: Int,
    public val chat_notifications_enabled: Boolean?,
    public val gifts: List<TlGen_SavedStarGift>,
    public val next_offset: String?,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_payments_SavedStarGifts() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        if (chat_notifications_enabled != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(count)
      chat_notifications_enabled?.let { stream.writeBool(it) }
      TlGen_Vector.serialize(stream, gifts)
      next_offset?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x95F389B1U
    }
  }
}
