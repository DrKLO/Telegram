package org.Tajgram.tgnet.model.generated

import kotlin.Byte
import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecureData : TlGen_Object {
  public data class TL_secureData(
    public val `data`: List<Byte>,
    public val data_hash: List<Byte>,
    public val secret: List<Byte>,
  ) : TlGen_SecureData() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(data.toByteArray())
      stream.writeByteArray(data_hash.toByteArray())
      stream.writeByteArray(secret.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x8AEABEC3U
    }
  }
}
