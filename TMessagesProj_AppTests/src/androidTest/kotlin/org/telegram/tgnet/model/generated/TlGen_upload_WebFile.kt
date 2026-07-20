package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_upload_WebFile : TlGen_Object {
  public data class TL_upload_webFile(
    public val size: Int,
    public val mime_type: String,
    public val file_type: TlGen_storage_FileType,
    public val mtime: Int,
    public val bytes: List<Byte>,
  ) : TlGen_upload_WebFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(size)
      stream.writeString(mime_type)
      file_type.serializeToStream(stream)
      stream.writeInt32(mtime)
      stream.writeByteArray(bytes.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x21E753BCU
    }
  }
}
