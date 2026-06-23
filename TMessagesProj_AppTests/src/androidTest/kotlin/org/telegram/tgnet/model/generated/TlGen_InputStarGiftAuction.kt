package org.Tajgram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputStarGiftAuction : TlGen_Object {
  public data class TL_inputStarGiftAuction(
    public val gift_id: Long,
  ) : TlGen_InputStarGiftAuction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(gift_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x02E16C98U
    }
  }

  public data class TL_inputStarGiftAuctionSlug(
    public val slug: String,
  ) : TlGen_InputStarGiftAuction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7AB58308U
    }
  }
}
