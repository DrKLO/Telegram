package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftAttributeRarity : TlGen_Object {
  public data class TL_starGiftAttributeRarity(
    public val permille: Int,
  ) : TlGen_StarGiftAttributeRarity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(permille)
    }

    public companion object {
      public const val MAGIC: UInt = 0x36437737U
    }
  }

  public data object TL_starGiftAttributeRarityUncommon : TlGen_StarGiftAttributeRarity() {
    public const val MAGIC: UInt = 0xDBCE6389U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_starGiftAttributeRarityRare : TlGen_StarGiftAttributeRarity() {
    public const val MAGIC: UInt = 0xF08D516BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_starGiftAttributeRarityEpic : TlGen_StarGiftAttributeRarity() {
    public const val MAGIC: UInt = 0x78FBF3A8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_starGiftAttributeRarityLegendary : TlGen_StarGiftAttributeRarity() {
    public const val MAGIC: UInt = 0xCEF7E7A8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
