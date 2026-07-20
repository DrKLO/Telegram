package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BotCommand : TlGen_Object {
  public data class TL_botCommand(
    public val ephemeral: Boolean,
    public val command: String,
    public val description: String,
  ) : TlGen_BotCommand() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ephemeral) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(command)
      stream.writeString(description)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9852D6D2U
    }
  }

  public data class TL_botCommand_layer227(
    public val command: String,
    public val description: String,
  ) : TlGen_Object {
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
