package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GroupCallParticipantVideo : TlGen_Object {
  public data class TL_groupCallParticipantVideo(
    public val paused: Boolean,
    public val endpoint: String,
    public val source_groups: List<TlGen_GroupCallParticipantVideoSourceGroup>,
    public val audio_source: Int?,
  ) : TlGen_GroupCallParticipantVideo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (paused) result = result or 1U
        if (audio_source != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(endpoint)
      TlGen_Vector.serialize(stream, source_groups)
      audio_source?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x67753AC8U
    }
  }
}
