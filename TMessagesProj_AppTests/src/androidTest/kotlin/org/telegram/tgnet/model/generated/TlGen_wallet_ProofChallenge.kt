package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_ProofChallenge : TlGen_Object {
  public data class TL_wallet_proofChallenge(
    public val payload: String,
    public val expires: Int,
    public val domain: String,
  ) : TlGen_wallet_ProofChallenge() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(payload)
      stream.writeInt32(expires)
      stream.writeString(domain)
    }

    public companion object {
      public const val MAGIC: UInt = 0x99E41707U
    }
  }
}
