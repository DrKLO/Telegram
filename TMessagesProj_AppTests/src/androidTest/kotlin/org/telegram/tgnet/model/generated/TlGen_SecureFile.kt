package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecureFile : TlGen_Object {
  public data object TL_secureFileEmpty : TlGen_SecureFile() {
    public const val MAGIC: UInt = 0x64199744U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_secureFile(
    public val id: Long,
    public val access_hash: Long,
    public val size: Long,
    public val dc_id: Int,
    public val date: Int,
    public val file_hash: List<Byte>,
    public val secret: List<Byte>,
  ) : TlGen_SecureFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt64(size)
      stream.writeInt32(dc_id)
      stream.writeInt32(date)
      stream.writeByteArray(file_hash.toByteArray())
      stream.writeByteArray(secret.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x7D09C27EU
    }
  }

  public data class TL_secureFile_layer142(
    public val id: Long,
    public val access_hash: Long,
    public val size: Int,
    public val dc_id: Int,
    public val date: Int,
    public val file_hash: List<Byte>,
    public val secret: List<Byte>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(size)
      stream.writeInt32(dc_id)
      stream.writeInt32(date)
      stream.writeByteArray(file_hash.toByteArray())
      stream.writeByteArray(secret.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xE0277A62U
    }
  }
}
