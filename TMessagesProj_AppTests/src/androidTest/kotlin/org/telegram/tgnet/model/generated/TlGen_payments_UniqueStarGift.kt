package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_UniqueStarGift : TlGen_Object {
  public data class TL_payments_uniqueStarGift(
    public val gift: TlGen_StarGift,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_payments_UniqueStarGift() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      gift.serializeToStream(stream)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x416C56E8U
    }
  }
}
