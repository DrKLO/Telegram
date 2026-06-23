package org.Tajgram.tgnet.model.generated

import kotlin.Byte
import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecureCredentialsEncrypted : TlGen_Object {
  public data class TL_secureCredentialsEncrypted(
    public val `data`: List<Byte>,
    public val hash: List<Byte>,
    public val secret: List<Byte>,
  ) : TlGen_SecureCredentialsEncrypted() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(data.toByteArray())
      stream.writeByteArray(hash.toByteArray())
      stream.writeByteArray(secret.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x33F0EA47U
    }
  }
}
