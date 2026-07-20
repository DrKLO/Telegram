package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessageReportOption : TlGen_Object {
  public data class TL_messageReportOption(
    public val text: String,
    public val option: List<Byte>,
  ) : TlGen_MessageReportOption() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeByteArray(option.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x7903E3D9U
    }
  }
}
