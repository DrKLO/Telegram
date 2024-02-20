package org.telegram.tgnet;

public class TL_smsjobs {

    public static class TL_smsjobs_eligibleToJoin extends TLObject {
        public static final int constructor = 0xdc8b44cf;

        public String terms_of_use;
        public int monthly_sent_sms;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            terms_of_use = stream.readString(exception);
            monthly_sent_sms = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(terms_of_use);
            stream.writeInt32(monthly_sent_sms);
        }
    }

    public static class TL_smsjobs_status extends TLObject {
        public static final int constructor = 0x2aee9191;

        public int flags;
        public boolean allow_international;
        public int recent_sent;
        public int recent_since;
        public int recent_remains;
        public int total_sent;
        public int total_since;
        public String last_gift_slug;
        public String terms_url;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            allow_international = (flags & 1) != 0;
            recent_sent = stream.readInt32(exception);
            recent_since = stream.readInt32(exception);
            recent_remains = stream.readInt32(exception);
            total_sent = stream.readInt32(exception);
            total_since = stream.readInt32(exception);
            if ((flags & 2) != 0) {
                last_gift_slug = stream.readString(exception);
            }
            terms_url = stream.readString(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = allow_international ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            stream.writeInt32(recent_sent);
            stream.writeInt32(recent_since);
            stream.writeInt32(recent_remains);
            stream.writeInt32(total_sent);
            stream.writeInt32(total_since);
            if ((flags & 2) != 0) {
                stream.writeString(last_gift_slug);
            }
            stream.writeString(terms_url);
        }
    }

    public static class TL_updateSmsJob extends TLRPC.Update {
        public static final int constructor = 0xf16269d4;

        public String job_id;
        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            job_id = stream.readString(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(job_id);
        }
    }

    public static class TL_smsJob extends TLObject {
        public static final int constructor = 0xe6a1eeb8;

        public String job_id;
        public String phone_number;
        public String text;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            job_id = stream.readString(exception);
            phone_number = stream.readString(exception);
            text = stream.readString(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(job_id);
            stream.writeString(phone_number);
            stream.writeString(text);
        }
    }

    public static class TL_smsjobs_isEligibleToJoin extends TLObject {
        public static final int constructor = 0xedc39d0;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            if (constructor == TL_smsjobs_eligibleToJoin.constructor) {
                TL_smsjobs_eligibleToJoin result = new TL_smsjobs_eligibleToJoin();
                result.readParams(stream, exception);
                return result;
            }
            return null;
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_smsjobs_join extends TLObject {
        public static final int constructor = 0xa74ece2d;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_smsjobs_leave extends TLObject {
        public static final int constructor = 0x9898ad73;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_smsjobs_getStatus extends TLObject {
        public static final int constructor = 0x10a698e8;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            if (constructor == TL_smsjobs_status.constructor) {
                TL_smsjobs_status result = new TL_smsjobs_status();
                result.readParams(stream, exception);
                return result;
            }
            return null;
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_smsjobs_getSmsJob extends TLObject {
        public static final int constructor = 0x778d902f;

        public String job_id;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            if (constructor == TL_smsJob.constructor) {
                TL_smsJob result = new TL_smsJob();
                result.readParams(stream, exception);
                return result;
            }
            return null;
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(job_id);
        }
    }

    public static class TL_smsjobs_finishJob extends TLObject {
        public static final int constructor = 0x4f1ebf24;

        public int flags;
        public String job_id;
        public String error;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(job_id);
            if ((flags & 1) != 0) {
                stream.writeString(error);
            }
        }
    }

    public static class TL_smsjobs_updateSettings extends TLObject {
        public static final int constructor = 0x93fa0bf;

        public int flags;
        public boolean allow_international;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = allow_international ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
        }
    }
}
