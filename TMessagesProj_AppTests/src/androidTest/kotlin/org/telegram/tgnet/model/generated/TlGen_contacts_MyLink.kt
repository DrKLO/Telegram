package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_contacts_MyLink : TlGen_Object {
  public data object TL_contacts_myLinkEmpty_layer23 : TlGen_contacts_MyLink() {
    public const val MAGIC: UInt = 0xD22A1C60U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_contacts_myLinkRequested_layer23(
    public val contact: Boolean,
  ) : TlGen_contacts_MyLink() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(contact)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6C69EFEEU
    }
  }

  public data object TL_contacts_myLinkContact_layer23 : TlGen_contacts_MyLink() {
    public const val MAGIC: UInt = 0xC240EBD9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
