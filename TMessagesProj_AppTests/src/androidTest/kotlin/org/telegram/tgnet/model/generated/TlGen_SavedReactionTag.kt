package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SavedReactionTag : TlGen_Object {
  public data class TL_savedReactionTag(
    public val reaction: TlGen_Reaction,
    public val title: String?,
    public val count: Int,
  ) : TlGen_SavedReactionTag() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      reaction.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCB6FF828U
    }
  }
}
