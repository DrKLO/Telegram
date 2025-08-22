package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AttachMenuPeerType : TlGen_Object {
  public data object TL_attachMenuPeerTypeSameBotPM : TlGen_AttachMenuPeerType() {
    public const val MAGIC: UInt = 0x7D6BE90EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_attachMenuPeerTypeBotPM : TlGen_AttachMenuPeerType() {
    public const val MAGIC: UInt = 0xC32BFA1AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_attachMenuPeerTypePM : TlGen_AttachMenuPeerType() {
    public const val MAGIC: UInt = 0xF146D31FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_attachMenuPeerTypeChat : TlGen_AttachMenuPeerType() {
    public const val MAGIC: UInt = 0x0509113FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_attachMenuPeerTypeBroadcast : TlGen_AttachMenuPeerType() {
    public const val MAGIC: UInt = 0x7BFBDEFCU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
