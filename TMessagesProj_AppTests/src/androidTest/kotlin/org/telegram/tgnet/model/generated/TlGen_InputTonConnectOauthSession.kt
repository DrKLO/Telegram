package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputTonConnectOauthSession : TlGen_Object {
  public data class TL_inputTonConnectOauthSession(
    public val session_id: Long,
    public val challenge_answer: List<Byte>,
    public val body: List<Byte>,
    public val proof: TlGen_WalletOwnershipProof,
  ) : TlGen_InputTonConnectOauthSession() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(session_id)
      stream.writeByteArray(challenge_answer.toByteArray())
      stream.writeByteArray(body.toByteArray())
      proof.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x629F66CEU
    }
  }
}
