package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessageFwdHeader : TlGen_Object {
  public data class TL_messageFwdHeader(
    public val imported: Boolean,
    public val saved_out: Boolean,
    public val from_id: TlGen_Peer?,
    public val from_name: String?,
    public val date: Int,
    public val channel_post: Int?,
    public val post_author: String?,
    public val saved_from_id: TlGen_Peer?,
    public val saved_from_name: String?,
    public val saved_date: Int?,
    public val psa_type: String?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_MessageFwdHeader() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (from_id != null) result = result or 1U
        if (channel_post != null) result = result or 4U
        if (post_author != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (from_name != null) result = result or 32U
        if (psa_type != null) result = result or 64U
        if (imported) result = result or 128U
        if (saved_from_id != null) result = result or 256U
        if (saved_from_name != null) result = result or 512U
        if (saved_date != null) result = result or 1024U
        if (saved_out) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      from_id?.serializeToStream(stream)
      from_name?.let { stream.writeString(it) }
      stream.writeInt32(date)
      channel_post?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      multiflags_4?.let { it.saved_from_peer.serializeToStream(stream) }
      multiflags_4?.let { stream.writeInt32(it.saved_from_msg_id) }
      saved_from_id?.serializeToStream(stream)
      saved_from_name?.let { stream.writeString(it) }
      saved_date?.let { stream.writeInt32(it) }
      psa_type?.let { stream.writeString(it) }
    }

    public data class Multiflags_4(
      public val saved_from_peer: TlGen_Peer,
      public val saved_from_msg_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x4E4DF4BBU
    }
  }

  public data class TL_messageFwdHeader_layer69(
    public val from_id: Int?,
    public val date: Int,
    public val channel_id: Int?,
    public val channel_post: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (from_id != null) result = result or 1U
        if (channel_id != null) result = result or 2U
        if (channel_post != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      from_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      channel_id?.let { stream.writeInt32(it) }
      channel_post?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC786DDCBU
    }
  }

  public data class TL_messageFwdHeader_layer72(
    public val from_id: Int?,
    public val date: Int,
    public val channel_id: Int?,
    public val channel_post: Int?,
    public val post_author: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (from_id != null) result = result or 1U
        if (channel_id != null) result = result or 2U
        if (channel_post != null) result = result or 4U
        if (post_author != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      from_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      channel_id?.let { stream.writeInt32(it) }
      channel_post?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xFADFF4ACU
    }
  }

  public data class TL_messageFwdHeader_layer96(
    public val from_id: Int?,
    public val date: Int,
    public val channel_id: Int?,
    public val channel_post: Int?,
    public val post_author: String?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (from_id != null) result = result or 1U
        if (channel_id != null) result = result or 2U
        if (channel_post != null) result = result or 4U
        if (post_author != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      from_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      channel_id?.let { stream.writeInt32(it) }
      channel_post?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      multiflags_4?.let { it.saved_from_peer.serializeToStream(stream) }
      multiflags_4?.let { stream.writeInt32(it.saved_from_msg_id) }
    }

    public data class Multiflags_4(
      public val saved_from_peer: TlGen_Peer,
      public val saved_from_msg_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x559EBE6DU
    }
  }

  public data class TL_messageFwdHeader_layer112(
    public val from_id: Int?,
    public val from_name: String?,
    public val date: Int,
    public val channel_id: Int?,
    public val channel_post: Int?,
    public val post_author: String?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (from_id != null) result = result or 1U
        if (channel_id != null) result = result or 2U
        if (channel_post != null) result = result or 4U
        if (post_author != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (from_name != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      from_id?.let { stream.writeInt32(it) }
      from_name?.let { stream.writeString(it) }
      stream.writeInt32(date)
      channel_id?.let { stream.writeInt32(it) }
      channel_post?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      multiflags_4?.let { it.saved_from_peer.serializeToStream(stream) }
      multiflags_4?.let { stream.writeInt32(it.saved_from_msg_id) }
    }

    public data class Multiflags_4(
      public val saved_from_peer: TlGen_Peer,
      public val saved_from_msg_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xEC338270U
    }
  }

  public data class TL_messageFwdHeader_layer118(
    public val from_id: Int?,
    public val from_name: String?,
    public val date: Int,
    public val channel_id: Int?,
    public val channel_post: Int?,
    public val post_author: String?,
    public val psa_type: String?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (from_id != null) result = result or 1U
        if (channel_id != null) result = result or 2U
        if (channel_post != null) result = result or 4U
        if (post_author != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (from_name != null) result = result or 32U
        if (psa_type != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      from_id?.let { stream.writeInt32(it) }
      from_name?.let { stream.writeString(it) }
      stream.writeInt32(date)
      channel_id?.let { stream.writeInt32(it) }
      channel_post?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      multiflags_4?.let { it.saved_from_peer.serializeToStream(stream) }
      multiflags_4?.let { stream.writeInt32(it.saved_from_msg_id) }
      psa_type?.let { stream.writeString(it) }
    }

    public data class Multiflags_4(
      public val saved_from_peer: TlGen_Peer,
      public val saved_from_msg_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x353A686BU
    }
  }

  public data class TL_messageFwdHeader_layer169(
    public val imported: Boolean,
    public val from_id: TlGen_Peer?,
    public val from_name: String?,
    public val date: Int,
    public val channel_post: Int?,
    public val post_author: String?,
    public val psa_type: String?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (from_id != null) result = result or 1U
        if (channel_post != null) result = result or 4U
        if (post_author != null) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (from_name != null) result = result or 32U
        if (psa_type != null) result = result or 64U
        if (imported) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      from_id?.serializeToStream(stream)
      from_name?.let { stream.writeString(it) }
      stream.writeInt32(date)
      channel_post?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      multiflags_4?.let { it.saved_from_peer.serializeToStream(stream) }
      multiflags_4?.let { stream.writeInt32(it.saved_from_msg_id) }
      psa_type?.let { stream.writeString(it) }
    }

    public data class Multiflags_4(
      public val saved_from_peer: TlGen_Peer,
      public val saved_from_msg_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x5F777DCEU
    }
  }
}
