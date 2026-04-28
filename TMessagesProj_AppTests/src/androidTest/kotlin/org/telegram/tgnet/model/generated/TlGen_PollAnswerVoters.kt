package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PollAnswerVoters : TlGen_Object {
  public data class TL_pollAnswerVoters(
    public val chosen: Boolean,
    public val correct: Boolean,
    public val option: List<Byte>,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_PollAnswerVoters() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chosen) result = result or 1U
        if (correct) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeByteArray(option.toByteArray())
      multiflags_2?.let { stream.writeInt32(it.voters) }
      multiflags_2?.let { TlGen_Vector.serialize(stream, it.recent_voters) }
    }

    public data class Multiflags_2(
      public val voters: Int,
      public val recent_voters: List<TlGen_Peer>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x3645230AU
    }
  }

  public data class TL_pollAnswerVoters_layer223(
    public val chosen: Boolean,
    public val correct: Boolean,
    public val option: List<Byte>,
    public val voters: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chosen) result = result or 1U
        if (correct) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeByteArray(option.toByteArray())
      stream.writeInt32(voters)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3B6DDAD2U
    }
  }
}
