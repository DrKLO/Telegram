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
    public val open_answers: Boolean,
    public val revoting_disabled: Boolean,
    public val shuffle_answers: Boolean,
    public val hide_results_until_close: Boolean,
    public val creator: Boolean,
    public val question: TlGen_TextWithEntities,
    public val answers: List<TlGen_PollAnswer>,
    public val close_period: Int?,
    public val close_date: Int?,
    public val hash: Long,
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
        if (open_answers) result = result or 64U
        if (revoting_disabled) result = result or 128U
        if (shuffle_answers) result = result or 256U
        if (hide_results_until_close) result = result or 512U
        if (creator) result = result or 1024U
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
      stream.writeInt64(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB8425BE9U
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

  public data class TL_poll_layer223(
    public val id: Long,
    public val closed: Boolean,
    public val public_voters: Boolean,
    public val multiple_choice: Boolean,
    public val quiz: Boolean,
    public val question: TlGen_TextWithEntities,
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
      question.serializeToStream(stream)
      TlGen_Vector.serialize(stream, answers)
      close_period?.let { stream.writeInt32(it) }
      close_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x58747131U
    }
  }
}
