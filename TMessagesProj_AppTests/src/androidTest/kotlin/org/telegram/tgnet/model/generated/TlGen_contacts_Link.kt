package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_contacts_Link : TlGen_Object {
  public data class TL_contacts_link_layer23(
    public val my_link: TlGen_contacts_MyLink,
    public val foreign_link: TlGen_contacts_ForeignLink,
    public val user: TlGen_User,
  ) : TlGen_contacts_Link() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      my_link.serializeToStream(stream)
      foreign_link.serializeToStream(stream)
      user.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xECCEA3F5U
    }
  }

  public data class TL_contacts_link_layer101(
    public val my_link: TlGen_ContactLink,
    public val foreign_link: TlGen_ContactLink,
    public val user: TlGen_User,
  ) : TlGen_contacts_Link() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      my_link.serializeToStream(stream)
      foreign_link.serializeToStream(stream)
      user.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3ACE484CU
    }
  }
}
