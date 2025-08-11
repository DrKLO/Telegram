package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_RestrictionReason : TlGen_Object {
  public data class TL_restrictionReason(
    public val platform: String,
    public val reason: String,
    public val text: String,
  ) : TlGen_RestrictionReason() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(platform)
      stream.writeString(reason)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD072ACB4U
    }
  }
}
