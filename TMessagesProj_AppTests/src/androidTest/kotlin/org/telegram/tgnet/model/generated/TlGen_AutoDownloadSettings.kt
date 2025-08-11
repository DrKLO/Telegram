package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AutoDownloadSettings : TlGen_Object {
  public data class TL_autoDownloadSettings(
    public val disabled: Boolean,
    public val video_preload_large: Boolean,
    public val audio_preload_next: Boolean,
    public val phonecalls_less_data: Boolean,
    public val stories_preload: Boolean,
    public val photo_size_max: Int,
    public val video_size_max: Long,
    public val file_size_max: Long,
    public val video_upload_maxbitrate: Int,
    public val small_queue_active_operations_max: Int,
    public val large_queue_active_operations_max: Int,
  ) : TlGen_AutoDownloadSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (disabled) result = result or 1U
        if (video_preload_large) result = result or 2U
        if (audio_preload_next) result = result or 4U
        if (phonecalls_less_data) result = result or 8U
        if (stories_preload) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(photo_size_max)
      stream.writeInt64(video_size_max)
      stream.writeInt64(file_size_max)
      stream.writeInt32(video_upload_maxbitrate)
      stream.writeInt32(small_queue_active_operations_max)
      stream.writeInt32(large_queue_active_operations_max)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBAA57628U
    }
  }
}
