package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_upload_CdnFile : TlGen_Object {
  public data class TL_upload_cdnFileReuploadNeeded(
    public val request_token: List<Byte>,
  ) : TlGen_upload_CdnFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(request_token.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xEEA8E46EU
    }
  }

  public data class TL_upload_cdnFile(
    public val bytes: List<Byte>,
  ) : TlGen_upload_CdnFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(bytes.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xA99FCA4FU
    }
  }
}
