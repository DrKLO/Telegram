package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_premium_BoostsList : TlGen_Object {
  public data class TL_premium_boostsList(
    public val count: Int,
    public val boosts: List<TlGen_Boost>,
    public val next_offset: String?,
    public val users: List<TlGen_User>,
  ) : TlGen_premium_BoostsList() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, boosts)
      next_offset?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x86F8613CU
    }
  }
}
