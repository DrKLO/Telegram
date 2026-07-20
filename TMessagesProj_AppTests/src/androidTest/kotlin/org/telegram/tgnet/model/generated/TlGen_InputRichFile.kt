package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputRichFile : TlGen_Object {
  public data class TL_inputRichFilePhoto(
    public val id: String,
    public val photo: TlGen_InputPhoto,
  ) : TlGen_InputRichFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(id)
      photo.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9B00622BU
    }
  }

  public data class TL_inputRichFileDocument(
    public val id: String,
    public val document: TlGen_InputDocument,
  ) : TlGen_InputRichFile() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(id)
      document.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x83281DBDU
    }
  }
}
