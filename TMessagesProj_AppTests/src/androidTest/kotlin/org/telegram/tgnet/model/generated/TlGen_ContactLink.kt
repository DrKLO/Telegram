package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_ContactLink : TlGen_Object {
  public data object TL_contactLinkUnknown_layer101 : TlGen_ContactLink() {
    public const val MAGIC: UInt = 0x5F4F9247U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_contactLinkNone_layer101 : TlGen_ContactLink() {
    public const val MAGIC: UInt = 0xFEEDD3ADU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_contactLinkHasPhone_layer99 : TlGen_ContactLink() {
    public const val MAGIC: UInt = 0x268F3FBDU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_contactLinkContact_layer101 : TlGen_ContactLink() {
    public const val MAGIC: UInt = 0xD502C2D0U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
