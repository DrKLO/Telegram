package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputQuickReplyShortcut : TlGen_Object {
  public data class TL_inputQuickReplyShortcut(
    public val shortcut: String,
  ) : TlGen_InputQuickReplyShortcut() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(shortcut)
    }

    public companion object {
      public const val MAGIC: UInt = 0x24596D41U
    }
  }

  public data class TL_inputQuickReplyShortcutId(
    public val shortcut_id: Int,
  ) : TlGen_InputQuickReplyShortcut() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(shortcut_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x01190CF1U
    }
  }
}
