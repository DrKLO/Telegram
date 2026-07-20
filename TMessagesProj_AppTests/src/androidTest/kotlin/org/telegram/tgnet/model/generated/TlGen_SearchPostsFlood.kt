package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SearchPostsFlood : TlGen_Object {
  public data class TL_searchPostsFlood(
    public val query_is_free: Boolean,
    public val total_daily: Int,
    public val remains: Int,
    public val wait_till: Int?,
    public val stars_amount: Long,
  ) : TlGen_SearchPostsFlood() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (query_is_free) result = result or 1U
        if (wait_till != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(total_daily)
      stream.writeInt32(remains)
      wait_till?.let { stream.writeInt32(it) }
      stream.writeInt64(stars_amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3E0B5B6AU
    }
  }
}
