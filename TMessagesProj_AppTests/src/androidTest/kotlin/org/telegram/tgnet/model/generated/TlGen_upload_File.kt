package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_upload_File : TlGen_Object {
  public data class TL_upload_file(
    public val type: TlGen_storage_FileType,
    public val mtime: Int,
    public val bytes: List<Byte>,
  ) : TlGen_upload_File() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      stream.writeInt32(mtime)
      stream.writeByteArray(bytes.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x096A18D5U
    }
  }

  public data class TL_upload_fileCdnRedirect(
    public val dc_id: Int,
    public val file_token: List<Byte>,
    public val encryption_key: List<Byte>,
    public val encryption_iv: List<Byte>,
    public val file_hashes: List<TlGen_FileHash>,
  ) : TlGen_upload_File() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(dc_id)
      stream.writeByteArray(file_token.toByteArray())
      stream.writeByteArray(encryption_key.toByteArray())
      stream.writeByteArray(encryption_iv.toByteArray())
      TlGen_Vector.serialize(stream, file_hashes)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF18CDA44U
    }
  }
}
