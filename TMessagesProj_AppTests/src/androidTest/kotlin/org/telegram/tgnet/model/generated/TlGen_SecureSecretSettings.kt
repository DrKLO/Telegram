package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecureSecretSettings : TlGen_Object {
  public data class TL_secureSecretSettings(
    public val secure_algo: TlGen_SecurePasswordKdfAlgo,
    public val secure_secret: List<Byte>,
    public val secure_secret_id: Long,
  ) : TlGen_SecureSecretSettings() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      secure_algo.serializeToStream(stream)
      stream.writeByteArray(secure_secret.toByteArray())
      stream.writeInt64(secure_secret_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1527BCACU
    }
  }
}
