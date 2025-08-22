package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_SearchResultsCalendar : TlGen_Object {
  public data class TL_messages_searchResultsCalendar(
    public val inexact: Boolean,
    public val count: Int,
    public val min_date: Int,
    public val min_msg_id: Int,
    public val offset_id_offset: Int?,
    public val periods: List<TlGen_SearchResultsCalendarPeriod>,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_SearchResultsCalendar() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (inexact) result = result or 1U
        if (offset_id_offset != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(count)
      stream.writeInt32(min_date)
      stream.writeInt32(min_msg_id)
      offset_id_offset?.let { stream.writeInt32(it) }
      TlGen_Vector.serialize(stream, periods)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x147EE23CU
    }
  }
}
