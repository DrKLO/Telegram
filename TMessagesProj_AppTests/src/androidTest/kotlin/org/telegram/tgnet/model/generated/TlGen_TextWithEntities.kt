package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_TextWithEntities : TlGen_Object {
  public data class TL_textWithEntities(
    public val text: String,
    public val entities: List<TlGen_MessageEntity>,
  ) : TlGen_TextWithEntities() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      TlGen_Vector.serialize(stream, entities)
    }

    public companion object {
      public const val MAGIC: UInt = 0x751F3146U
    }
  }
}
