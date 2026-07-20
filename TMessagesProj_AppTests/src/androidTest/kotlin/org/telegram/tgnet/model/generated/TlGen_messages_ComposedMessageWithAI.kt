package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_ComposedMessageWithAI : TlGen_Object {
  public data class TL_messages_composedMessageWithAI(
    public val result_text: TlGen_TextWithEntities,
    public val diff_text: TlGen_TextWithEntities?,
  ) : TlGen_messages_ComposedMessageWithAI() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (diff_text != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      result_text.serializeToStream(stream)
      diff_text?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x90D7ADFAU
    }
  }
}
