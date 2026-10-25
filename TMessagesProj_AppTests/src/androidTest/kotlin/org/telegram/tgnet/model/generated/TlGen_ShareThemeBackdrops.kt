package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ShareThemeBackdrops : TlGen_Object {
  public data object TL_shareThemeBackdropsNotModified : TlGen_ShareThemeBackdrops() {
    public const val MAGIC: UInt = 0x7A3F6DFFU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_shareThemeBackdrops(
    public val hash: Int,
    public val backdrops: List<TlGen_ShareThemeBackdropOption>,
  ) : TlGen_ShareThemeBackdrops() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(hash)
      TlGen_Vector.serialize(stream, backdrops)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAAEADCE0U
    }
  }
}
