package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PollResults : TlGen_Object {
  public data class TL_pollResults(
    public val min: Boolean,
    public val results: List<TlGen_PollAnswerVoters>?,
    public val total_voters: Int?,
    public val recent_voters: List<TlGen_Peer>?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_PollResults() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (min) result = result or 1U
        if (results != null) result = result or 2U
        if (total_voters != null) result = result or 4U
        if (recent_voters != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      results?.let { TlGen_Vector.serialize(stream, it) }
      total_voters?.let { stream.writeInt32(it) }
      recent_voters?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_4?.let { stream.writeString(it.solution) }
      multiflags_4?.let { TlGen_Vector.serialize(stream, it.solution_entities) }
    }

    public data class Multiflags_4(
      public val solution: String,
      public val solution_entities: List<TlGen_MessageEntity>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x7ADF2420U
    }
  }

  public data class TL_pollResults_layer108(
    public val min: Boolean,
    public val results: List<TlGen_PollAnswerVoters>?,
    public val total_voters: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (min) result = result or 1U
        if (results != null) result = result or 2U
        if (total_voters != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      results?.let { TlGen_Vector.serialize(stream, it) }
      total_voters?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x5755785AU
    }
  }

  public data class TL_pollResults_layer111(
    public val min: Boolean,
    public val results: List<TlGen_PollAnswerVoters>?,
    public val total_voters: Int?,
    public val recent_voters: List<Int>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (min) result = result or 1U
        if (results != null) result = result or 2U
        if (total_voters != null) result = result or 4U
        if (recent_voters != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      results?.let { TlGen_Vector.serialize(stream, it) }
      total_voters?.let { stream.writeInt32(it) }
      recent_voters?.let { TlGen_Vector.serializeInt(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC87024A2U
    }
  }

  public data class TL_pollResults_layer132(
    public val min: Boolean,
    public val results: List<TlGen_PollAnswerVoters>?,
    public val total_voters: Int?,
    public val recent_voters: List<Int>?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (min) result = result or 1U
        if (results != null) result = result or 2U
        if (total_voters != null) result = result or 4U
        if (recent_voters != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      results?.let { TlGen_Vector.serialize(stream, it) }
      total_voters?.let { stream.writeInt32(it) }
      recent_voters?.let { TlGen_Vector.serializeInt(stream, it) }
      multiflags_4?.let { stream.writeString(it.solution) }
      multiflags_4?.let { TlGen_Vector.serialize(stream, it.solution_entities) }
    }

    public data class Multiflags_4(
      public val solution: String,
      public val solution_entities: List<TlGen_MessageEntity>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xBADCC1A3U
    }
  }

  public data class TL_pollResults_layer158(
    public val min: Boolean,
    public val results: List<TlGen_PollAnswerVoters>?,
    public val total_voters: Int?,
    public val recent_voters: List<Long>?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (min) result = result or 1U
        if (results != null) result = result or 2U
        if (total_voters != null) result = result or 4U
        if (recent_voters != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      results?.let { TlGen_Vector.serialize(stream, it) }
      total_voters?.let { stream.writeInt32(it) }
      recent_voters?.let { TlGen_Vector.serializeLong(stream, it) }
      multiflags_4?.let { stream.writeString(it.solution) }
      multiflags_4?.let { TlGen_Vector.serialize(stream, it.solution_entities) }
    }

    public data class Multiflags_4(
      public val solution: String,
      public val solution_entities: List<TlGen_MessageEntity>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xDCB82EA3U
    }
  }
}
