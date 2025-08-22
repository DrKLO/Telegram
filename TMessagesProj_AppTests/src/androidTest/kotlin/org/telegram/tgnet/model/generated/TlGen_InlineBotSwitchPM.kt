package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InlineBotSwitchPM : TlGen_Object {
  public data class TL_inlineBotSwitchPM(
    public val text: String,
    public val start_param: String,
  ) : TlGen_InlineBotSwitchPM() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeString(start_param)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3C20629FU
    }
  }
}
