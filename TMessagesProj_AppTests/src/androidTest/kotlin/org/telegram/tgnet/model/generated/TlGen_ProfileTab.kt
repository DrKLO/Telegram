package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ProfileTab : TlGen_Object {
  public data object TL_profileTabPosts : TlGen_ProfileTab() {
    public const val MAGIC: UInt = 0xB98CD696U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_profileTabGifts : TlGen_ProfileTab() {
    public const val MAGIC: UInt = 0x4D4BD46AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_profileTabMedia : TlGen_ProfileTab() {
    public const val MAGIC: UInt = 0x72C64955U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_profileTabFiles : TlGen_ProfileTab() {
    public const val MAGIC: UInt = 0xAB339C00U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_profileTabMusic : TlGen_ProfileTab() {
    public const val MAGIC: UInt = 0x9F27D26EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_profileTabVoice : TlGen_ProfileTab() {
    public const val MAGIC: UInt = 0xE477092EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_profileTabLinks : TlGen_ProfileTab() {
    public const val MAGIC: UInt = 0xD3656499U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_profileTabGifs : TlGen_ProfileTab() {
    public const val MAGIC: UInt = 0xA2C0F695U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
