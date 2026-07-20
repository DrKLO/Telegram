package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AiComposeToneExample : TlGen_Object {
  public data class TL_aiComposeToneExample(
    public val from: TlGen_TextWithEntities,
    public val to: TlGen_TextWithEntities,
  ) : TlGen_AiComposeToneExample() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      from.serializeToStream(stream)
      to.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF1D628ECU
    }
  }
}
