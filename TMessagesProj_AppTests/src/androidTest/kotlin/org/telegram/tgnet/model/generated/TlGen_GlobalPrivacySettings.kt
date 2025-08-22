package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GlobalPrivacySettings : TlGen_Object {
  public data class TL_globalPrivacySettings(
    public val archive_and_mute_new_noncontact_peers: Boolean,
    public val keep_archived_unmuted: Boolean,
    public val keep_archived_folders: Boolean,
    public val hide_read_marks: Boolean,
    public val new_noncontact_peers_require_premium: Boolean,
    public val display_gifts_button: Boolean,
    public val noncontact_peers_paid_stars: Long?,
    public val disallowed_gifts: TlGen_DisallowedGiftsSettings?,
  ) : TlGen_GlobalPrivacySettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (archive_and_mute_new_noncontact_peers) result = result or 1U
        if (keep_archived_unmuted) result = result or 2U
        if (keep_archived_folders) result = result or 4U
        if (hide_read_marks) result = result or 8U
        if (new_noncontact_peers_require_premium) result = result or 16U
        if (noncontact_peers_paid_stars != null) result = result or 32U
        if (disallowed_gifts != null) result = result or 64U
        if (display_gifts_button) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      noncontact_peers_paid_stars?.let { stream.writeInt64(it) }
      disallowed_gifts?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFE41B34FU
    }
  }
}
