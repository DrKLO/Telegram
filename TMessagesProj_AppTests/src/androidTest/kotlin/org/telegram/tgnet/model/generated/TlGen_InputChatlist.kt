package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputChatlist : TlGen_Object {
  public data class TL_inputChatlistDialogFilter(
    public val filter_id: Int,
  ) : TlGen_InputChatlist() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(filter_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF3E0DA33U
    }
  }
}
