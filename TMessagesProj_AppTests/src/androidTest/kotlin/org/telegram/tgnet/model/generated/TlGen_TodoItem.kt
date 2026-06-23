package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_TodoItem : TlGen_Object {
  public data class TL_todoItem(
    public val id: Int,
    public val title: TlGen_TextWithEntities,
  ) : TlGen_TodoItem() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      title.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCBA9A52FU
    }
  }
}
