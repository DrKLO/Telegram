package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PendingSuggestion : TlGen_Object {
  public data class TL_pendingSuggestion(
    public val suggestion: String,
    public val title: TlGen_TextWithEntities,
    public val description: TlGen_TextWithEntities,
    public val url: String,
  ) : TlGen_PendingSuggestion() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(suggestion)
      title.serializeToStream(stream)
      description.serializeToStream(stream)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE7E82E12U
    }
  }
}
