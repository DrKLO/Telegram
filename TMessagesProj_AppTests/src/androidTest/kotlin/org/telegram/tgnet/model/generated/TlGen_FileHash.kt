package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_FileHash : TlGen_Object {
  public data class TL_fileHash(
    public val offset: Long,
    public val limit: Int,
    public val hash: List<Byte>,
  ) : TlGen_FileHash() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(offset)
      stream.writeInt32(limit)
      stream.writeByteArray(hash.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xF39B035CU
    }
  }
}
