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

public sealed class TlGen_Poll : TlGen_Object {
  public data class TL_poll(
    public val id: Long,
    public val closed: Boolean,
    public val public_voters: Boolean,
    public val multiple_choice: Boolean,
    public val quiz: Boolean,
    public val question: TlGen_TextWithEntities,
    public val answers: List<TlGen_PollAnswer>,
    public val close_period: Int?,
    public val close_date: Int?,
  ) : TlGen_Poll() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (closed) result = result or 1U
        if (public_voters) result = result or 2U
        if (multiple_choice) result = result or 4U
        if (quiz) result = result or 8U
        if (close_period != null) result = result or 16U
        if (close_date != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(flags.toInt())
      question.serializeToStream(stream)
      TlGen_Vector.serialize(stream, answers)
      close_period?.let { stream.writeInt32(it) }
      close_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x58747131U
    }
  }

  public data class TL_poll_layer111(
    public val id: Long,
    public val closed: Boolean,
    public val public_voters: Boolean,
    public val multiple_choice: Boolean,
    public val quiz: Boolean,
    public val question: String,
    public val answers: List<TlGen_PollAnswer>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (closed) result = result or 1U
        if (public_voters) result = result or 2U
        if (multiple_choice) result = result or 4U
        if (quiz) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(flags.toInt())
      stream.writeString(question)
      TlGen_Vector.serialize(stream, answers)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD5529D06U
    }
  }

  public data class TL_poll_layer178(
    public val id: Long,
    public val closed: Boolean,
    public val public_voters: Boolean,
    public val multiple_choice: Boolean,
    public val quiz: Boolean,
    public val question: String,
    public val answers: List<TlGen_PollAnswer>,
    public val close_period: Int?,
    public val close_date: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (closed) result = result or 1U
        if (public_voters) result = result or 2U
        if (multiple_choice) result = result or 4U
        if (quiz) result = result or 8U
        if (close_period != null) result = result or 16U
        if (close_date != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(flags.toInt())
      stream.writeString(question)
      TlGen_Vector.serialize(stream, answers)
      close_period?.let { stream.writeInt32(it) }
      close_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x86E18161U
    }
  }
}
