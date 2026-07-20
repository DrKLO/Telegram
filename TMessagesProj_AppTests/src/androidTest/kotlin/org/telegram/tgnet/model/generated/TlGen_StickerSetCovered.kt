package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StickerSetCovered : TlGen_Object {
  public data class TL_stickerSetCovered(
    public val `set`: TlGen_StickerSet,
    public val cover: TlGen_Document,
  ) : TlGen_StickerSetCovered() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      set.serializeToStream(stream)
      cover.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6410A5D2U
    }
  }

  public data class TL_stickerSetMultiCovered(
    public val `set`: TlGen_StickerSet,
    public val covers: List<TlGen_Document>,
  ) : TlGen_StickerSetCovered() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      set.serializeToStream(stream)
      TlGen_Vector.serialize(stream, covers)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3407E51BU
    }
  }

  public data class TL_stickerSetFullCovered(
    public val `set`: TlGen_StickerSet,
    public val packs: List<TlGen_StickerPack>,
    public val keywords: List<TlGen_StickerKeyword>,
    public val documents: List<TlGen_Document>,
  ) : TlGen_StickerSetCovered() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      set.serializeToStream(stream)
      TlGen_Vector.serialize(stream, packs)
      TlGen_Vector.serialize(stream, keywords)
      TlGen_Vector.serialize(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0x40D13C0EU
    }
  }

  public data class TL_stickerSetNoCovered(
    public val `set`: TlGen_StickerSet,
  ) : TlGen_StickerSetCovered() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      set.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x77B15D1CU
    }
  }
}
