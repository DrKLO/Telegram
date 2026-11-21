package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_UserWallet : TlGen_Object {
  public data class TL_userWallet(
    public val address: String,
    public val balance: Long,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_UserWallet() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(address)
      stream.writeInt64(balance)
      multiflags_0?.let { stream.writeByteArray(it.public_key.toByteArray()) }
      multiflags_0?.let { stream.writeByteArray(it.state_init.toByteArray()) }
    }

    public data class Multiflags_0(
      public val public_key: List<Byte>,
      public val state_init: List<Byte>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x454D2737U
    }
  }
}
