package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputStickeredMedia : TlGen_Object {
  public data class TL_inputStickeredMediaPhoto(
    public val id: TlGen_InputPhoto,
  ) : TlGen_InputStickeredMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4A992157U
    }
  }

  public data class TL_inputStickeredMediaDocument(
    public val id: TlGen_InputDocument,
  ) : TlGen_InputStickeredMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0438865BU
    }
  }
}
