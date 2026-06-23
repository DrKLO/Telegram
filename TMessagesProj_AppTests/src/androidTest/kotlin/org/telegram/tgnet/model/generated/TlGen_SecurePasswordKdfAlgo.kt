package org.Tajgram.tgnet.model.generated

import kotlin.Byte
import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecurePasswordKdfAlgo : TlGen_Object {
  public data object TL_securePasswordKdfAlgoUnknown : TlGen_SecurePasswordKdfAlgo() {
    public const val MAGIC: UInt = 0x004A8537U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000(
    public val salt: List<Byte>,
  ) : TlGen_SecurePasswordKdfAlgo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(salt.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xBBF2DDA0U
    }
  }

  public data class TL_securePasswordKdfAlgoSHA512(
    public val salt: List<Byte>,
  ) : TlGen_SecurePasswordKdfAlgo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(salt.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x86471D92U
    }
  }
}
