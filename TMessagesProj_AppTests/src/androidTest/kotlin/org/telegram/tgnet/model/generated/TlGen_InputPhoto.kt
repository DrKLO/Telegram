package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPhoto : TlGen_Object {
  public data object TL_inputPhotoEmpty : TlGen_InputPhoto() {
    public const val MAGIC: UInt = 0x1CD7BF0DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputPhoto(
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
  ) : TlGen_InputPhoto() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x3BB3B94AU
    }
  }
}
