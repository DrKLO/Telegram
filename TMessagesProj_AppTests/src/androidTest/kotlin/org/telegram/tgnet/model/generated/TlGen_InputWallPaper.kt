package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputWallPaper : TlGen_Object {
  public data class TL_inputWallPaper(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputWallPaper() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE630B979U
    }
  }

  public data class TL_inputWallPaperSlug(
    public val slug: String,
  ) : TlGen_InputWallPaper() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0x72091C80U
    }
  }

  public data class TL_inputWallPaperNoFile(
    public val id: Long,
  ) : TlGen_InputWallPaper() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x967A462EU
    }
  }
}
