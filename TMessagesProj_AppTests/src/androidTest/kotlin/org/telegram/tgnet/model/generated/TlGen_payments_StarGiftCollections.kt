package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarGiftCollections : TlGen_Object {
  public data object TL_payments_starGiftCollectionsNotModified :
      TlGen_payments_StarGiftCollections() {
    public const val MAGIC: UInt = 0xA0BA4F17U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_payments_starGiftCollections(
    public val collections: List<TlGen_StarGiftCollection>,
  ) : TlGen_payments_StarGiftCollections() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, collections)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8A2932F3U
    }
  }
}
