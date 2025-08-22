package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_contacts_ImportedContacts : TlGen_Object {
  public data class TL_contacts_importedContacts(
    public val imported: List<TlGen_ImportedContact>,
    public val popular_invites: List<TlGen_PopularContact>,
    public val retry_contacts: List<Long>,
    public val users: List<TlGen_User>,
  ) : TlGen_contacts_ImportedContacts() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, imported)
      TlGen_Vector.serialize(stream, popular_invites)
      TlGen_Vector.serializeLong(stream, retry_contacts)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x77D01C3BU
    }
  }
}
