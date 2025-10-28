package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputFile : TlGen_Object {
  public data class TL_inputFile(
    public val id: Long,
    public val parts: Int,
    public val name: String,
    public val md5_checksum: String,
  ) : TlGen_InputFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(parts)
      stream.writeString(name)
      stream.writeString(md5_checksum)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF52FF27FU
    }
  }

  public data class TL_inputFileBig(
    public val id: Long,
    public val parts: Int,
    public val name: String,
  ) : TlGen_InputFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(parts)
      stream.writeString(name)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFA4F0BB5U
    }
  }

  public data class TL_inputFileStoryDocument(
    public val id: TlGen_InputDocument,
  ) : TlGen_InputFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x62DC8B48U
    }
  }
}
