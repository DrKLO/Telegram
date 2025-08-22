package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DialogFilterSuggested : TlGen_Object {
  public data class TL_dialogFilterSuggested(
    public val filter: TlGen_DialogFilter,
    public val description: String,
  ) : TlGen_DialogFilterSuggested() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      filter.serializeToStream(stream)
      stream.writeString(description)
    }

    public companion object {
      public const val MAGIC: UInt = 0x77744D4AU
    }
  }
}
