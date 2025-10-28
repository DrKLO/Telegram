package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftAttributeId : TlGen_Object {
  public data class TL_starGiftAttributeIdModel(
    public val document_id: Long,
  ) : TlGen_StarGiftAttributeId() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(document_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x48AAAE3CU
    }
  }

  public data class TL_starGiftAttributeIdPattern(
    public val document_id: Long,
  ) : TlGen_StarGiftAttributeId() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(document_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4A162433U
    }
  }

  public data class TL_starGiftAttributeIdBackdrop(
    public val backdrop_id: Int,
  ) : TlGen_StarGiftAttributeId() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(backdrop_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1F01C757U
    }
  }
}
