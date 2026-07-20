package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_EncryptedFile : TlGen_Object {
  public data object TL_encryptedFileEmpty : TlGen_EncryptedFile() {
    public const val MAGIC: UInt = 0xC21F497EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_encryptedFile(
    public val id: Long,
    public val access_hash: Long,
    public val size: Long,
    public val dc_id: Int,
    public val key_fingerprint: Int,
  ) : TlGen_EncryptedFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt64(size)
      stream.writeInt32(dc_id)
      stream.writeInt32(key_fingerprint)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA8008CD8U
    }
  }
}
