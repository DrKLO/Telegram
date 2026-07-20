package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_JoinChatBotResult : TlGen_Object {
  public data object TL_joinChatBotResultApproved : TlGen_JoinChatBotResult() {
    public const val MAGIC: UInt = 0xAE152A69U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_joinChatBotResultDeclined : TlGen_JoinChatBotResult() {
    public const val MAGIC: UInt = 0x0EFA0194U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_joinChatBotResultQueued : TlGen_JoinChatBotResult() {
    public const val MAGIC: UInt = 0x98A3A840U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_joinChatBotResultWebView(
    public val url: String,
  ) : TlGen_JoinChatBotResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD6E3B813U
    }
  }
}
