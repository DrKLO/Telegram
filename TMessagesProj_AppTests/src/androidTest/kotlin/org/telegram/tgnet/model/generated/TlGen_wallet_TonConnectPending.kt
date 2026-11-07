package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_TonConnectPending : TlGen_Object {
  public data class TL_wallet_tonConnectPending(
    public val session: TlGen_TonConnectSession,
    public val requests: List<TlGen_TonConnectRequest>,
  ) : TlGen_wallet_TonConnectPending() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      session.serializeToStream(stream)
      TlGen_Vector.serialize(stream, requests)
    }

    public companion object {
      public const val MAGIC: UInt = 0x85C0F124U
    }
  }
}
