package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_bots_AccessSettings : TlGen_Object {
  public data class TL_bots_accessSettings(
    public val restricted: Boolean,
    public val add_users: List<TlGen_User>?,
  ) : TlGen_bots_AccessSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (restricted) result = result or 1U
        if (add_users != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      add_users?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xDD1FBF93U
    }
  }
}
