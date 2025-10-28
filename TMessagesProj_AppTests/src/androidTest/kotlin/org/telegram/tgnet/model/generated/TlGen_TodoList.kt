package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_TodoList : TlGen_Object {
  public data class TL_todoList(
    public val others_can_append: Boolean,
    public val others_can_complete: Boolean,
    public val title: TlGen_TextWithEntities,
    public val list: List<TlGen_TodoItem>,
  ) : TlGen_TodoList() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (others_can_append) result = result or 1U
        if (others_can_complete) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      title.serializeToStream(stream)
      TlGen_Vector.serialize(stream, list)
    }

    public companion object {
      public const val MAGIC: UInt = 0x49B92A26U
    }
  }
}
