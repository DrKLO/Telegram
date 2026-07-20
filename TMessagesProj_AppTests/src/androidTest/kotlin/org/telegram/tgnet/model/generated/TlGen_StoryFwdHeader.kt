package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StoryFwdHeader : TlGen_Object {
  public data class TL_storyFwdHeader(
    public val modified: Boolean,
    public val from: TlGen_Peer?,
    public val from_name: String?,
    public val story_id: Int?,
  ) : TlGen_StoryFwdHeader() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (from != null) result = result or 1U
        if (from_name != null) result = result or 2U
        if (story_id != null) result = result or 4U
        if (modified) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      from?.serializeToStream(stream)
      from_name?.let { stream.writeString(it) }
      story_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB826E150U
    }
  }
}
