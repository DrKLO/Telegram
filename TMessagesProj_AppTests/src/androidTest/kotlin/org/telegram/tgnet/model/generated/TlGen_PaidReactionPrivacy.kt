package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PaidReactionPrivacy : TlGen_Object {
  public data object TL_paidReactionPrivacyDefault : TlGen_PaidReactionPrivacy() {
    public const val MAGIC: UInt = 0x206AD49EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_paidReactionPrivacyAnonymous : TlGen_PaidReactionPrivacy() {
    public const val MAGIC: UInt = 0x1F0C1AD9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_paidReactionPrivacyPeer(
    public val peer: TlGen_InputPeer,
  ) : TlGen_PaidReactionPrivacy() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDC6CFCF0U
    }
  }
}
