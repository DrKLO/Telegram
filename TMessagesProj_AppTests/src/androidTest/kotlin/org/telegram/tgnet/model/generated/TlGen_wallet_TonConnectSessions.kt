package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_TonConnectSessions : TlGen_Object {
  public data class TL_wallet_tonConnectSessions(
    public val sessions: List<TlGen_TonConnectSession>,
  ) : TlGen_wallet_TonConnectSessions() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, sessions)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0E1F6896U
    }
  }
}
