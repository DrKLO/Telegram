package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_auth_LoggedOut : TlGen_Object {
  public data class TL_auth_loggedOut(
    public val future_auth_token: List<Byte>?,
  ) : TlGen_auth_LoggedOut() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (future_auth_token != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      future_auth_token?.let { stream.writeByteArray(it.toByteArray()) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC3A2835FU
    }
  }
}
