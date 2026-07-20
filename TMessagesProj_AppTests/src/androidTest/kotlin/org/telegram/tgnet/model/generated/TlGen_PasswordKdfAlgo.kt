package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PasswordKdfAlgo : TlGen_Object {
  public data object TL_passwordKdfAlgoUnknown : TlGen_PasswordKdfAlgo() {
    public const val MAGIC: UInt = 0xD45AB096U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(
    public val salt1: List<Byte>,
    public val salt2: List<Byte>,
    public val g: Int,
    public val p: List<Byte>,
  ) : TlGen_PasswordKdfAlgo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(salt1.toByteArray())
      stream.writeByteArray(salt2.toByteArray())
      stream.writeInt32(g)
      stream.writeByteArray(p.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x3A912D4AU
    }
  }
}
