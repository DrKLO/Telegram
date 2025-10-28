package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarsRating : TlGen_Object {
  public data class TL_starsRating(
    public val level: Int,
    public val current_level_stars: Long,
    public val stars: Long,
    public val next_level_stars: Long?,
  ) : TlGen_StarsRating() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_level_stars != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(level)
      stream.writeInt64(current_level_stars)
      stream.writeInt64(stars)
      next_level_stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x1B0E4F07U
    }
  }
}
