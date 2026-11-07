package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_EncryptedSecretPhrasePart : TlGen_Object {
  public data class TL_wallet_encryptedSecretPhrasePart(
    public val `data`: List<Byte>,
  ) : TlGen_wallet_EncryptedSecretPhrasePart() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(data.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x18E82537U
    }
  }
}
