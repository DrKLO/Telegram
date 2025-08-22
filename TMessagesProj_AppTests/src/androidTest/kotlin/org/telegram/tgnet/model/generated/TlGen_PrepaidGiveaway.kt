package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PrepaidGiveaway : TlGen_Object {
  public data class TL_prepaidGiveaway(
    public val id: Long,
    public val months: Int,
    public val quantity: Int,
    public val date: Int,
  ) : TlGen_PrepaidGiveaway() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(months)
      stream.writeInt32(quantity)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB2539D54U
    }
  }

  public data class TL_prepaidStarsGiveaway(
    public val id: Long,
    public val stars: Long,
    public val quantity: Int,
    public val boosts: Int,
    public val date: Int,
  ) : TlGen_PrepaidGiveaway() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(stars)
      stream.writeInt32(quantity)
      stream.writeInt32(boosts)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9A9D77E0U
    }
  }
}
