package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_BotApp : TlGen_Object {
  public data class TL_messages_botApp(
    public val inactive: Boolean,
    public val request_write_access: Boolean,
    public val has_settings: Boolean,
    public val app: TlGen_BotApp,
  ) : TlGen_messages_BotApp() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (inactive) result = result or 1U
        if (request_write_access) result = result or 2U
        if (has_settings) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      app.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEB50ADF5U
    }
  }
}
