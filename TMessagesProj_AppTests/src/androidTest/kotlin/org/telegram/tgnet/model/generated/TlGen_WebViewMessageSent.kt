package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WebViewMessageSent : TlGen_Object {
  public data class TL_webViewMessageSent(
    public val msg_id: TlGen_InputBotInlineMessageID?,
  ) : TlGen_WebViewMessageSent() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (msg_id != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      msg_id?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0C94511CU
    }
  }
}
