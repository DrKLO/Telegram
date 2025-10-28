package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Folder : TlGen_Object {
  public data class TL_folder(
    public val autofill_new_broadcasts: Boolean,
    public val autofill_public_groups: Boolean,
    public val autofill_new_correspondents: Boolean,
    public val id: Int,
    public val title: String,
    public val photo: TlGen_ChatPhoto?,
  ) : TlGen_Folder() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (autofill_new_broadcasts) result = result or 1U
        if (autofill_public_groups) result = result or 2U
        if (autofill_new_correspondents) result = result or 4U
        if (photo != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
      photo?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFF544E65U
    }
  }
}
