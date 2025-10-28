package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputSecureFile : TlGen_Object {
  public data class TL_inputSecureFileUploaded(
    public val id: Long,
    public val parts: Int,
    public val md5_checksum: String,
    public val file_hash: List<Byte>,
    public val secret: List<Byte>,
  ) : TlGen_InputSecureFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(parts)
      stream.writeString(md5_checksum)
      stream.writeByteArray(file_hash.toByteArray())
      stream.writeByteArray(secret.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x3334B0F0U
    }
  }

  public data class TL_inputSecureFile(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputSecureFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5367E5BEU
    }
  }
}
