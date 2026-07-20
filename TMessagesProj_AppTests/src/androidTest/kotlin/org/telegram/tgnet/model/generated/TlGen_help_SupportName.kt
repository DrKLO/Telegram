package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_SupportName : TlGen_Object {
  public data class TL_help_supportName(
    public val name: String,
  ) : TlGen_help_SupportName() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(name)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8C05F1C9U
    }
  }
}
