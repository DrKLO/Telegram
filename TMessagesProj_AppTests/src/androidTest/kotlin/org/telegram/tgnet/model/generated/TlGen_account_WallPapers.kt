package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_WallPapers : TlGen_Object {
  public data object TL_account_wallPapersNotModified : TlGen_account_WallPapers() {
    public const val MAGIC: UInt = 0x1C199183U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_account_wallPapers(
    public val hash: Long,
    public val wallpapers: List<TlGen_WallPaper>,
  ) : TlGen_account_WallPapers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, wallpapers)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCDC3858CU
    }
  }
}
