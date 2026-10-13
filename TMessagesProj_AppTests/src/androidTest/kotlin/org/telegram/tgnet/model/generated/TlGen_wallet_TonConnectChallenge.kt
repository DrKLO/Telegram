package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_TonConnectChallenge : TlGen_Object {
  public data class TL_wallet_tonConnectChallenge(
    public val challenge: List<Byte>,
    public val event_id: Long,
  ) : TlGen_wallet_TonConnectChallenge() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(challenge.toByteArray())
      stream.writeInt64(event_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4BC89693U
    }
  }
}
