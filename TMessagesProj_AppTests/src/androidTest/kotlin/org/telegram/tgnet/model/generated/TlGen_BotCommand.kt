package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BotCommand : TlGen_Object {
  public data class TL_botCommand(
    public val command: String,
    public val description: String,
  ) : TlGen_BotCommand() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(command)
      stream.writeString(description)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC27AC8C7U
    }
  }
}
