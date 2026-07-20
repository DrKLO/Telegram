package org.telegram.tgnet.tl;

import android.text.TextUtils;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_aicompose {

    public static class InputAiComposeTone extends TLObject {

        public static InputAiComposeTone from(AiComposeTone tone) {
            if (tone instanceof TL_aiComposeTone) {
                final inputAiComposeToneID input = new inputAiComposeToneID();
                input.id = ((TL_aiComposeTone) tone).id;
                input.access_hash = ((TL_aiComposeTone) tone).access_hash;
                return input;
            } else if (tone instanceof TL_aiComposeToneDefault) {
                final inputAiComposeToneDefault input = new inputAiComposeToneDefault();
                input.tone = ((TL_aiComposeToneDefault) tone).tone;
                return input;
            }
            return null;
        }

        public static inputAiComposeToneDefault fromDefault(String tone) {
            inputAiComposeToneDefault i = new inputAiComposeToneDefault();
            i.tone = tone;
            return i;
        }

        public static boolean equals(InputAiComposeTone a, InputAiComposeTone b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            if (a instanceof inputAiComposeToneDefault)
                return b instanceof inputAiComposeToneDefault && TextUtils.equals(((inputAiComposeToneDefault) a).tone, ((inputAiComposeToneDefault) b).tone);
            if (a instanceof inputAiComposeToneID)
                return b instanceof inputAiComposeToneID && ((inputAiComposeToneID) a).id == ((inputAiComposeToneID) b).id && ((inputAiComposeToneID) a).access_hash == ((inputAiComposeToneID) b).access_hash;
            if (a instanceof inputAiComposeToneSlug)
                return b instanceof inputAiComposeToneSlug && TextUtils.equals(((inputAiComposeToneSlug) a).slug, ((inputAiComposeToneSlug) b).slug);
            if (a instanceof inputAiComposeToneSingleUse)
                return b instanceof inputAiComposeToneSingleUse && TextUtils.equals(((inputAiComposeToneSingleUse) a).custom_prompt, ((inputAiComposeToneSingleUse) b).custom_prompt);
            return false;
        }

        public static InputAiComposeTone TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(InputAiComposeTone.class, fromConstructor(constructor), stream, constructor, exception);
        }

        private static InputAiComposeTone fromConstructor(int constructor) {
            switch (constructor) {
                case inputAiComposeToneDefault.constructor: return new inputAiComposeToneDefault();
                case inputAiComposeToneID.constructor:      return new inputAiComposeToneID();
                case inputAiComposeToneSlug.constructor:    return new inputAiComposeToneSlug();
                case inputAiComposeToneSingleUse.constructor:   return new inputAiComposeToneSingleUse();
                default: return null;
            }
        }
    }
    public static class inputAiComposeToneDefault extends InputAiComposeTone {
        public static final int constructor = 0x1fe9a9bf;

        public String tone;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(tone);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            tone = stream.readString(exception);
        }
    }
    public static class inputAiComposeToneID extends InputAiComposeTone {
        public static final int constructor = 0x0773C080;

        public long id;
        public long access_hash;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }
    }
    public static class inputAiComposeToneSlug extends InputAiComposeTone {
        public static final int constructor = 0x1fa01357;

        public String slug;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            slug = stream.readString(exception);
        }
    }
    public static class inputAiComposeToneSingleUse extends InputAiComposeTone {
        public static final int constructor = 0xe0c35af;

        public String custom_prompt;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(custom_prompt);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            custom_prompt = stream.readString(exception);
        }
    }

    public static class AiComposeTone extends TLObject {
        public String title;
        public long emoji_id;

        public static AiComposeTone TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(AiComposeTone.class, fromConstructor(constructor), stream, constructor, exception);
        }

        private static AiComposeTone fromConstructor(int constructor) {
            switch (constructor) {
                case TL_aiComposeTone.constructor:        return new TL_aiComposeTone();
                case TL_aiComposeToneDefault.constructor: return new TL_aiComposeToneDefault();
            }
            return null;
        }
    }
    public static class TL_aiComposeTone extends AiComposeTone {
        public static final int constructor = 0xcff63ea9;

        public int flags;
        public boolean creator;
        public long id;
        public long access_hash;
        public String slug;
        public String prompt;
        public int installs_count;
        public long author_id;
        public aiComposeToneExample example_english;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            creator = hasFlag(flags, FLAG_0);
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            slug = stream.readString(exception);
            title = stream.readString(exception);
            if (hasFlag(flags, FLAG_1)) {
                emoji_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                prompt = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                installs_count = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                author_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_5)) {
                example_english = aiComposeToneExample.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, creator);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeString(slug);
            stream.writeString(title);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt64(emoji_id);
            }
            if (hasFlag(flags, FLAG_4)) {
                stream.writeString(prompt);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeInt32(installs_count);
            }
            if (hasFlag(flags, FLAG_3)) {
                stream.writeInt64(author_id);
            }
            if (hasFlag(flags, FLAG_5)) {
                example_english.serializeToStream(stream);
            }
        }
    }
    public static class TL_aiComposeToneDefault extends AiComposeTone {
        public static final int constructor = 0x9bad6414;

        public String tone;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            tone = stream.readString(exception);
            emoji_id = stream.readInt64(exception);
            title = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(tone);
            stream.writeInt64(emoji_id);
            stream.writeString(title);
        }
    }


    public static class Tones extends TLObject {

        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static Tones TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(Tones.class, fromConstructor(constructor), stream, constructor, exception);
        }

        private static Tones fromConstructor(int constructor) {
            switch (constructor) {
                case TL_tones.constructor:            return new TL_tones();
                case TL_tonesNotModified.constructor: return new TL_tonesNotModified();
            }
            return null;
        }
    }
    public static class TL_tones extends Tones {
        public static final int constructor = 0x6c9d0efe;

        public long hash;
        public ArrayList<AiComposeTone> tones = new ArrayList<>();

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            hash = stream.readInt64(exception);
            tones = Vector.deserialize(stream, AiComposeTone::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
            Vector.serialize(stream, tones);
            Vector.serialize(stream, users);
        }
    }
    public static class TL_tonesNotModified extends Tones {
        public static final int constructor = 0xc1f46103;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {}
        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class aiComposeToneExample extends TLObject {
        public static final int constructor = 0xf1d628ec;

        public TLRPC.TL_textWithEntities from;
        public TLRPC.TL_textWithEntities to;

        public static aiComposeToneExample TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(aiComposeToneExample.class, constructor == aiComposeToneExample.constructor ? new aiComposeToneExample() : null, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            from = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            to = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
        }
        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            from.serializeToStream(stream);
            to.serializeToStream(stream);
        }
    }

    public static class createTone extends TLMethod<AiComposeTone> {
        public static final int constructor = 0x4aa83913;

        public int flags;
        public boolean display_author;
        public long emoji_id;
        public String title;
        public String prompt;

        @Override
        public AiComposeTone deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return AiComposeTone.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, display_author);
            stream.writeInt32(flags);
            stream.writeInt64(emoji_id);
            stream.writeString(title);
            stream.writeString(prompt);
        }
    }
    public static class updateTone extends TLMethod<AiComposeTone> {
        public static final int constructor = 0x903bcf59;

        public int flags;
        public InputAiComposeTone tone;
        public boolean display_author;
        public long emoji_id;
        public String title;
        public String prompt;

        @Override
        public AiComposeTone deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return AiComposeTone.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            tone.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeBool(display_author);
            }
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt64(emoji_id);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeString(title);
            }
            if (hasFlag(flags, FLAG_3)) {
                stream.writeString(prompt);
            }
        }
    }
    public static class saveTone extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x1782cbb1;

        public InputAiComposeTone tone;
        public boolean unsave;

        @Override
        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            tone.serializeToStream(stream);
            stream.writeBool(unsave);
        }
    }
    public static class deleteTone extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0xdd39316a;

        public InputAiComposeTone tone;

        @Override
        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            tone.serializeToStream(stream);
        }
    }
    public static class getTone extends TLMethod<Tones> {
        public static final int constructor = 0xb2e8ba03;

        public InputAiComposeTone tone;

        @Override
        public Tones deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return Tones.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            tone.serializeToStream(stream);
        }
    }
    public static class getTones extends TLMethod<Tones> {
        public static final int constructor = 0xabd59201;

        public long hash;

        @Override
        public Tones deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return Tones.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }
    public static class getToneExample extends TLMethod<aiComposeToneExample> {
        public static final int constructor = 0xd1b4ab14;

        public InputAiComposeTone tone;
        public int num;

        @Override
        public aiComposeToneExample deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return aiComposeToneExample.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            tone.serializeToStream(stream);
            stream.writeInt32(num);
        }
    }

}
