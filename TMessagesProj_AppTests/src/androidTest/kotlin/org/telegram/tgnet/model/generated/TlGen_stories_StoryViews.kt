package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_stories_StoryViews : TlGen_Object {
  public data class TL_stories_storyViews(
    public val views: List<TlGen_StoryViews>,
    public val users: List<TlGen_User>,
  ) : TlGen_stories_StoryViews() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, views)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDE9EED1DU
    }
  }
}
