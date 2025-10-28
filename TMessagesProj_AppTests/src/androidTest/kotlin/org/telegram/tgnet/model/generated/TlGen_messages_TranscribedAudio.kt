package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_TranscribedAudio : TlGen_Object {
  public data class TL_messages_transcribedAudio(
    public val pending: Boolean,
    public val transcription_id: Long,
    public val text: String,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_messages_TranscribedAudio() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pending) result = result or 1U
        if (multiflags_1 != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(transcription_id)
      stream.writeString(text)
      multiflags_1?.let { stream.writeInt32(it.trial_remains_num) }
      multiflags_1?.let { stream.writeInt32(it.trial_remains_until_date) }
    }

    public data class Multiflags_1(
      public val trial_remains_num: Int,
      public val trial_remains_until_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xCFB9D957U
    }
  }
}
