package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputEncryptedFile : TlGen_Object {
  public data object TL_inputEncryptedFileEmpty : TlGen_InputEncryptedFile() {
    public const val MAGIC: UInt = 0x1837C364U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputEncryptedFileUploaded(
    public val id: Long,
    public val parts: Int,
    public val md5_checksum: String,
    public val key_fingerprint: Int,
  ) : TlGen_InputEncryptedFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(parts)
      stream.writeString(md5_checksum)
      stream.writeInt32(key_fingerprint)
    }

    public companion object {
      public const val MAGIC: UInt = 0x64BD0306U
    }
  }

  public data class TL_inputEncryptedFile(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputEncryptedFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5A17B5E5U
    }
  }

  public data class TL_inputEncryptedFileBigUploaded(
    public val id: Long,
    public val parts: Int,
    public val key_fingerprint: Int,
  ) : TlGen_InputEncryptedFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(parts)
      stream.writeInt32(key_fingerprint)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2DC173C8U
    }
  }
}
