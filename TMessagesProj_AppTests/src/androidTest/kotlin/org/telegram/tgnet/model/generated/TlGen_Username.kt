package org.Tajgram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_Username : TlGen_Object {
  public data class TL_username(
    public val editable: Boolean,
    public val active: Boolean,
    public val username: String,
  ) : TlGen_Username() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (editable) result = result or 1U
        if (active) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(username)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB4073647U
    }
  }
}
