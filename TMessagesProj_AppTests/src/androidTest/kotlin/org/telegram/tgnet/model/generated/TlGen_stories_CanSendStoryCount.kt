package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_stories_CanSendStoryCount : TlGen_Object {
  public data class TL_stories_canSendStoryCount(
    public val count_remains: Int,
  ) : TlGen_stories_CanSendStoryCount() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count_remains)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC387C04EU
    }
  }
}
