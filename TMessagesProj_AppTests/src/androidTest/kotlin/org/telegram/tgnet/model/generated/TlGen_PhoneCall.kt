package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PhoneCall : TlGen_Object {
  public data class TL_phoneCallEmpty(
    public val id: Long,
  ) : TlGen_PhoneCall() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5366C915U
    }
  }

  public data class TL_phoneCallDiscarded(
    public val need_rating: Boolean,
    public val need_debug: Boolean,
    public val video: Boolean,
    public val id: Long,
    public val reason: TlGen_PhoneCallDiscardReason?,
    public val duration: Int?,
  ) : TlGen_PhoneCall() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reason != null) result = result or 1U
        if (duration != null) result = result or 2U
        if (need_rating) result = result or 4U
        if (need_debug) result = result or 8U
        if (video) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      reason?.serializeToStream(stream)
      duration?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x50CA4DE1U
    }
  }

  public data class TL_phoneCallWaiting(
    public val video: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val admin_id: Long,
    public val participant_id: Long,
    public val protocol: TlGen_PhoneCallProtocol,
    public val receive_date: Int?,
  ) : TlGen_PhoneCall() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (receive_date != null) result = result or 1U
        if (video) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeInt64(admin_id)
      stream.writeInt64(participant_id)
      protocol.serializeToStream(stream)
      receive_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC5226F17U
    }
  }

  public data class TL_phoneCallRequested(
    public val video: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val admin_id: Long,
    public val participant_id: Long,
    public val g_a_hash: List<Byte>,
    public val protocol: TlGen_PhoneCallProtocol,
  ) : TlGen_PhoneCall() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (video) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeInt64(admin_id)
      stream.writeInt64(participant_id)
      stream.writeByteArray(g_a_hash.toByteArray())
      protocol.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x14B0ED0CU
    }
  }

  public data class TL_phoneCallAccepted(
    public val video: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val admin_id: Long,
    public val participant_id: Long,
    public val g_b: List<Byte>,
    public val protocol: TlGen_PhoneCallProtocol,
  ) : TlGen_PhoneCall() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (video) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeInt64(admin_id)
      stream.writeInt64(participant_id)
      stream.writeByteArray(g_b.toByteArray())
      protocol.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3660C311U
    }
  }

  public data class TL_phoneCall(
    public val p2p_allowed: Boolean,
    public val video: Boolean,
    public val conference_supported: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val admin_id: Long,
    public val participant_id: Long,
    public val g_a_or_b: List<Byte>,
    public val key_fingerprint: Long,
    public val protocol: TlGen_PhoneCallProtocol,
    public val connections: List<TlGen_PhoneConnection>,
    public val start_date: Int,
    public val custom_parameters: TlGen_DataJSON?,
  ) : TlGen_PhoneCall() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (p2p_allowed) result = result or 32U
        if (video) result = result or 64U
        if (custom_parameters != null) result = result or 128U
        if (conference_supported) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeInt64(admin_id)
      stream.writeInt64(participant_id)
      stream.writeByteArray(g_a_or_b.toByteArray())
      stream.writeInt64(key_fingerprint)
      protocol.serializeToStream(stream)
      TlGen_Vector.serialize(stream, connections)
      stream.writeInt32(start_date)
      custom_parameters?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x30535AF5U
    }
  }
}
