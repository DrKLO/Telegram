package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarsStatus : TlGen_Object {
  public data class TL_payments_starsStatus(
    public val balance: TlGen_StarsAmount,
    public val subscriptions: List<TlGen_StarsSubscription>?,
    public val subscriptions_next_offset: String?,
    public val subscriptions_missing_balance: Long?,
    public val history: List<TlGen_StarsTransaction>?,
    public val next_offset: String?,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_payments_StarsStatus() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        if (subscriptions != null) result = result or 2U
        if (subscriptions_next_offset != null) result = result or 4U
        if (history != null) result = result or 8U
        if (subscriptions_missing_balance != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      balance.serializeToStream(stream)
      subscriptions?.let { TlGen_Vector.serialize(stream, it) }
      subscriptions_next_offset?.let { stream.writeString(it) }
      subscriptions_missing_balance?.let { stream.writeInt64(it) }
      history?.let { TlGen_Vector.serialize(stream, it) }
      next_offset?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6C9CE8EDU
    }
  }
}
