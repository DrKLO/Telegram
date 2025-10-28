package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InlineQueryPeerType : TlGen_Object {
  public data object TL_inlineQueryPeerTypeSameBotPM : TlGen_InlineQueryPeerType() {
    public const val MAGIC: UInt = 0x3081ED9DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inlineQueryPeerTypePM : TlGen_InlineQueryPeerType() {
    public const val MAGIC: UInt = 0x833C0FACU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inlineQueryPeerTypeChat : TlGen_InlineQueryPeerType() {
    public const val MAGIC: UInt = 0xD766C50AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inlineQueryPeerTypeMegagroup : TlGen_InlineQueryPeerType() {
    public const val MAGIC: UInt = 0x5EC4BE43U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inlineQueryPeerTypeBroadcast : TlGen_InlineQueryPeerType() {
    public const val MAGIC: UInt = 0x6334EE9AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inlineQueryPeerTypeBotPM : TlGen_InlineQueryPeerType() {
    public const val MAGIC: UInt = 0x0E3B2D0CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
