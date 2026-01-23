package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_RecentStory : TlGen_Object {
  public data class TL_recentStory(
    public val live: Boolean,
    public val max_id: Int?,
  ) : TlGen_RecentStory() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (live) result = result or 1U
        if (max_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      max_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x711D692DU
    }
  }
}
