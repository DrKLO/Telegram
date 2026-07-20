package org.telegram.tgnet.tl;

import android.graphics.Bitmap;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.io.File;
import java.util.ArrayList;

public class TL_iv {

    public static abstract class Page extends TLObject {

        public int flags;
        public boolean part;
        public boolean rtl;
        public String url;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
        public ArrayList<TLRPC.Photo> photos = new ArrayList<>();
        public ArrayList<TLRPC.Document> documents = new ArrayList<>();
        public boolean v2;
        public int views;
        public boolean web; //custom
        public File local; //custom

        private static Page fromConstructor(int constructor) {
            switch (constructor) {
                case TL_page.constructor: return new TL_page();
                case TL_page_layer110.constructor: return new TL_page_layer110();
                case TL_page_layer88.constructor: return new TL_page_layer88();
                case TL_pagePart_layer82.constructor: return new TL_pagePart_layer82();
                case TL_pagePart_layer67.constructor: return new TL_pagePart_layer67();
                case TL_pageFull_layer82.constructor: return new TL_pageFull_layer82();
                case TL_pageFull_layer67.constructor: return new TL_pageFull_layer67();
            }
            return null;
        }

        public static Page TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(Page.class, fromConstructor(constructor), stream, constructor, exception);
        }
    }
    public static class TL_page extends Page {
        public static final int constructor = 0x98657f0d;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            part = hasFlag(flags, FLAG_0);
            rtl = hasFlag(flags, FLAG_1);
            v2 = hasFlag(flags, FLAG_2);
            url = stream.readString(exception);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            photos = Vector.deserialize(stream, TLRPC.Photo::TLdeserialize, exception);
            documents = Vector.deserialize(stream, TLRPC.Document::TLdeserialize, exception);
            if (hasFlag(flags, FLAG_3)) {
                views = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, part);
            flags = setFlag(flags, FLAG_1, rtl);
            flags = setFlag(flags, FLAG_2, v2);
            stream.writeInt32(flags);
            stream.writeString(url);
            Vector.serialize(stream, blocks);
            Vector.serialize(stream, photos);
            Vector.serialize(stream, documents);
            if (hasFlag(flags, FLAG_3)) {
                stream.writeInt32(views);
            }
        }
    }
    public static class TL_page_layer110 extends TL_page {
        public static final int constructor = 0xae891bec;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            part = hasFlag(flags, FLAG_0);
            rtl = hasFlag(flags, FLAG_1);
            url = stream.readString(exception);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            photos = Vector.deserialize(stream, TLRPC.Photo::TLdeserialize, exception);
            documents = Vector.deserialize(stream, TLRPC.Document::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, part);
            flags = setFlag(flags, FLAG_1, rtl);
            stream.writeInt32(flags);
            stream.writeString(url);
            Vector.serialize(stream, blocks);
            Vector.serialize(stream, photos);
            Vector.serialize(stream, documents);
        }
    }
    public static class TL_page_layer88 extends TL_page {
        public static final int constructor = 0xf199a0a8;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            part = hasFlag(flags, FLAG_0);
            rtl = hasFlag(flags, FLAG_1);
            v2 = hasFlag(flags, FLAG_2);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            photos = Vector.deserialize(stream, TLRPC.Photo::TLdeserialize, exception);
            documents = Vector.deserialize(stream, TLRPC.Document::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, part);
            flags = setFlag(flags, FLAG_1, rtl);
            flags = setFlag(flags, FLAG_2, v2);
            stream.writeInt32(flags);
            Vector.serialize(stream, blocks);
            Vector.serialize(stream, photos);
            Vector.serialize(stream, documents);
        }
    }
    public static class TL_pagePart_layer82 extends TL_page {
        public static final int constructor = 0x8e3f9ebe;

        public void readParams(InputSerializedData stream, boolean exception) {
            part = true;
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            photos = Vector.deserialize(stream, TLRPC.Photo::TLdeserialize, exception);
            documents = Vector.deserialize(stream, TLRPC.Document::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, blocks);
            Vector.serialize(stream, photos);
            Vector.serialize(stream, documents);
        }
    }
    public static class TL_pagePart_layer67 extends TL_pagePart_layer82 {
        public static final int constructor = 0x8dee6c44;

        public void readParams(InputSerializedData stream, boolean exception) {
            part = true;
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            photos = Vector.deserialize(stream, TLRPC.Photo::TLdeserialize, exception);
            documents = Vector.deserialize(stream, TLRPC.Document::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, blocks);
            Vector.serialize(stream, photos);
            Vector.serialize(stream, documents);
        }
    }
    public static class TL_pageFull_layer82 extends TL_page {
        public static final int constructor = 0x556ec7aa;

        public void readParams(InputSerializedData stream, boolean exception) {
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            photos = Vector.deserialize(stream, TLRPC.Photo::TLdeserialize, exception);
            documents = Vector.deserialize(stream, TLRPC.Document::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, blocks);
            Vector.serialize(stream, photos);
            Vector.serialize(stream, documents);
        }
    }
    public static class TL_pageFull_layer67 extends TL_page {
        public static final int constructor = 0xd7a19d69;

        public void readParams(InputSerializedData stream, boolean exception) {
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            photos = Vector.deserialize(stream, TLRPC.Photo::TLdeserialize, exception);
            documents = Vector.deserialize(stream, TLRPC.Document::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, blocks);
            Vector.serialize(stream, photos);
            Vector.serialize(stream, documents);
        }
    }

    public static class RichMessage extends TLObject {
        public static final int constructor = 0xbaf39d8b;

        public int flags;
        public boolean rtl;
        public boolean part;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
        public ArrayList<TLRPC.Photo> photos = new ArrayList<>();
        public ArrayList<TLRPC.Document> documents = new ArrayList<>();

        public static RichMessage TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            RichMessage result = null;
            switch (constructor) {
                case RichMessage.constructor:
                    result = new RichMessage();
                    break;
            }
            return TLdeserialize(RichMessage.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            rtl = hasFlag(flags, FLAG_0);
            part = hasFlag(flags, FLAG_1);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            photos = Vector.deserialize(stream, TLRPC.Photo::TLdeserialize, exception);
            documents = Vector.deserialize(stream, TLRPC.Document::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, rtl);
            flags = setFlag(flags, FLAG_1, part);
            stream.writeInt32(flags);
            Vector.serialize(stream, blocks);
            Vector.serialize(stream, photos);
            Vector.serialize(stream, documents);
        }
    }
    public static class TL_inputRichMessage extends TLObject {
        public static final int constructor = 0xe4c449fc;

        public int flags;
        public boolean rtl;
        public boolean noautolink;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
        public ArrayList<TLRPC.InputPhoto> photos = new ArrayList<>();
        public ArrayList<TLRPC.InputDocument> documents = new ArrayList<>();
        public ArrayList<TLRPC.InputUser> users = new ArrayList<>();

        public static TL_inputRichMessage TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            TL_inputRichMessage result = null;
            switch (constructor) {
                case TL_inputRichMessage.constructor:
                    result = new TL_inputRichMessage();
                    break;
            }
            return TLdeserialize(TL_inputRichMessage.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            rtl = hasFlag(flags, FLAG_0);
            noautolink = hasFlag(flags, FLAG_1);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            if (hasFlag(flags, FLAG_2)) {
                photos = Vector.deserialize(stream, TLRPC.InputPhoto::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                documents = Vector.deserialize(stream, TLRPC.InputDocument::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                users = Vector.deserialize(stream, TLRPC.InputUser::TLdeserialize, exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, rtl);
            flags = setFlag(flags, FLAG_1, noautolink);
            stream.writeInt32(flags);
            Vector.serialize(stream, blocks);
            if (hasFlag(flags, FLAG_2)) {
                Vector.serialize(stream, photos);
            }
            if (hasFlag(flags, FLAG_3)) {
                Vector.serialize(stream, documents);
            }
            if (hasFlag(flags, FLAG_4)) {
                Vector.serialize(stream, users);
            }
        }
    }

    public static abstract class RichText extends TLObject {
        public String url;
        public long webpage_id;
        public String email;
        public RichText text;
        public ArrayList<RichText> texts = new ArrayList<>();
        public RichText parentRichText;

        private static RichText fromConstructor(int constructor) {
            switch (constructor) {
                case textEmpty.constructor: return new textEmpty();
                case textPlain.constructor: return new textPlain();
                case textBold.constructor: return new textBold();
                case textItalic.constructor: return new textItalic();
                case textUnderline.constructor: return new textUnderline();
                case textStrike.constructor: return new textStrike();
                case textFixed.constructor: return new textFixed();
                case textUrl.constructor: return new textUrl();
                case textEmail.constructor: return new textEmail();
                case textConcat.constructor: return new textConcat();
                case textSubscript.constructor: return new textSubscript();
                case textSuperscript.constructor: return new textSuperscript();
                case textMarked.constructor: return new textMarked();
                case textPhone.constructor: return new textPhone();
                case textImage.constructor: return new textImage();
                case textAnchor.constructor: return new textAnchor();
                case textMath.constructor: return new textMath();
                case textCustomEmoji.constructor: return new textCustomEmoji();
                case textSpoiler.constructor: return new textSpoiler();
                case textMention.constructor: return new textMention();
                case textHashtag.constructor: return new textHashtag();
                case textBotCommand.constructor: return new textBotCommand();
                case textCashtag.constructor: return new textCashtag();
                case textAutoUrl.constructor: return new textAutoUrl();
                case textAutoEmail.constructor: return new textAutoEmail();
                case textAutoPhone.constructor: return new textAutoPhone();
                case textBankCard.constructor: return new textBankCard();
                case textMentionName.constructor: return new textMentionName();
                case textDate.constructor: return new textDate();
                case textDiff.constructor: return new textDiff();
            }
            return null;
        }

        public static RichText TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(RichText.class, fromConstructor(constructor), stream, constructor, exception);
        }
    }
    public static class textEmpty extends RichText {
        public static final int constructor = 0xdc3d824f;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }
    public static class textPlain extends RichText {
        public static final int constructor = 0x744694e0;

        public String text;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
        }
    }
    public static class textBold extends RichText {
        public static final int constructor = 0x6724abc4;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textItalic extends RichText {
        public static final int constructor = 0xd912a59c;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textUnderline extends RichText {
        public static final int constructor = 0xc12622c4;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textStrike extends RichText {
        public static final int constructor = 0x9bf8bb95;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textFixed extends RichText {
        public static final int constructor = 0x6c3f19b9;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textUrl extends RichText {
        public static final int constructor = 0x3c2884c1;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            url = stream.readString(exception);
            webpage_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            stream.writeString(url);
            stream.writeInt64(webpage_id);
        }
    }
    public static class textEmail extends RichText {
        public static final int constructor = 0xde5a0dd6;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            email = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            stream.writeString(email);
        }
    }
    public static class textConcat extends RichText {
        public static final int constructor = 0x7e6260d7;

        public void readParams(InputSerializedData stream, boolean exception) {
            texts = Vector.deserialize(stream, RichText::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, texts);
        }
    }
    public static class textSubscript extends RichText {
        public static final int constructor = 0xed6a8504;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textSuperscript extends RichText {
        public static final int constructor = 0xc7fb5e01;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textMarked extends RichText {
        public static final int constructor = 0x34b8621;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textPhone extends RichText {
        public static final int constructor = 0x1ccb966a;

        public String phone;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            phone = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            stream.writeString(phone);
        }
    }
    public static class textImage extends RichText {
        public static final int constructor = 0x81ccf4f;

        public long document_id;
        public long photo_id; //custom
        public int w;
        public int h;

        public void readParams(InputSerializedData stream, boolean exception) {
            document_id = stream.readInt64(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(document_id);
            stream.writeInt32(w);
            stream.writeInt32(h);
        }
    }
    public static class textAnchor extends RichText {
        public static final int constructor = 0x35553762;
        public String name;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            name = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            stream.writeString(name);
        }
    }
    public static class textMath extends RichText {
        public static final int constructor = 0x9d2eac97;

        public int w; //custom
        public int h; //custom
        public String source;
        public int depth; //custom
        public Bitmap bitmap; //custom
        public boolean tried; //custom

        public void readParams(InputSerializedData stream, boolean exception) {
            source = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(source);
        }
    }
    public static class textCustomEmoji extends RichText {
        public static final int constructor = 0xa26156c0;

        public long document_id;
        public String alt;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            document_id = stream.readInt64(exception);
            alt = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(document_id);
            stream.writeString(alt);
        }
    }
    public static class textSpoiler extends RichText {
        public static final int constructor = 0x4c2a5d62;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textMention extends RichText {
        public static final int constructor = 0xcd24cf44;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textHashtag extends RichText {
        public static final int constructor = 0x519524ea;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textBotCommand extends RichText {
        public static final int constructor = 0x2ff29d3;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textCashtag extends RichText {
        public static final int constructor = 0x7b9e1801;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textAutoUrl extends RichText {
        public static final int constructor = 0xac6a83aa;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textAutoEmail extends RichText {
        public static final int constructor = 0xc556a45d;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textAutoPhone extends RichText {
        public static final int constructor = 0x24c26789;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textBankCard extends RichText {
        public static final int constructor = 0xb956812d;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class textMentionName extends RichText {
        public static final int constructor = 0x1a9fbfc;

        public long user_id;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            user_id = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            stream.writeInt64(user_id);
        }
    }
    public static class textDate extends RichText {
        public static final int constructor = 0xa5b45e2b;

        public int flags;
        public boolean relative;
        public boolean short_time;
        public boolean long_time;
        public boolean short_date;
        public boolean long_date;
        public boolean day_of_week;
        public int date;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            relative = hasFlag(flags, FLAG_0);
            short_time = hasFlag(flags, FLAG_1);
            long_time = hasFlag(flags, FLAG_2);
            short_date = hasFlag(flags, FLAG_3);
            long_date = hasFlag(flags, FLAG_4);
            day_of_week = hasFlag(flags, FLAG_5);
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, relative);
            flags = setFlag(flags, FLAG_1, short_time);
            flags = setFlag(flags, FLAG_2, long_time);
            flags = setFlag(flags, FLAG_3, short_date);
            flags = setFlag(flags, FLAG_4, long_date);
            flags = setFlag(flags, FLAG_5, day_of_week);
            stream.writeInt32(flags);
            text.serializeToStream(stream);
            stream.writeInt32(date);
        }
    }
    public static class textDiff extends RichText {
        public static final int constructor = 0x9686cb50;

        public RichText text;
        public RichText old_text;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            old_text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            old_text.serializeToStream(stream);
        }
    }

    public static abstract class PageBlock extends TLObject {

        public RichText text;
        public PageCaption caption;

        public boolean first; //custom
        public boolean bottom; //custom
        public int level; //custom
        public int quoteLevels; //custom — bitmask: bit i set => layer i is a blockquote, draw a vertical line at that layer's x offset
        public int mid; //custom
        public int groupId; //custom
        public TLRPC.PhotoSize thumb; //custom
        public TLObject thumbObject; //custom
        public int cachedWidth; //custom
        public int cachedHeight; //custom

        private static PageBlock fromConstructor(int constructor) {
            switch (constructor) {
                case pageBlockUnsupported.constructor: return new pageBlockUnsupported();
                case pageBlockTitle.constructor: return new pageBlockTitle();
                case pageBlockSubtitle.constructor: return new pageBlockSubtitle();
                case pageBlockAuthorDate.constructor: return new pageBlockAuthorDate();
                case pageBlockAuthorDate_layer60.constructor: return new pageBlockAuthorDate_layer60();
                case pageBlockHeader.constructor: return new pageBlockHeader();
                case pageBlockSubheader.constructor: return new pageBlockSubheader();
                case pageBlockParagraph.constructor: return new pageBlockParagraph();
                case pageBlockPreformatted.constructor: return new pageBlockPreformatted();
                case pageBlockFooter.constructor: return new pageBlockFooter();
                case pageBlockDivider.constructor: return new pageBlockDivider();
                case pageBlockAnchor.constructor: return new pageBlockAnchor();
                case pageBlockList.constructor: return new pageBlockList();
                case pageBlockList_layer82.constructor: return new pageBlockList_layer82();
                case pageBlockBlockquote.constructor: return new pageBlockBlockquote();
                case pageBlockBlockquoteBlocks.constructor: return new pageBlockBlockquoteBlocks();
                case pageBlockPullquote.constructor: return new pageBlockPullquote();
                case pageBlockPhoto.constructor: return new pageBlockPhoto();
                case pageBlockPhoto_layer82.constructor: return new pageBlockPhoto_layer82();
                case pageBlockVideo.constructor: return new pageBlockVideo();
                case pageBlockVideo_layer82.constructor: return new pageBlockVideo_layer82();
                case pageBlockCover.constructor: return new pageBlockCover();
                case pageBlockEmbed.constructor: return new pageBlockEmbed();
                case pageBlockEmbed_layer82.constructor: return new pageBlockEmbed_layer82();
                case pageBlockEmbed_layer60.constructor: return new pageBlockEmbed_layer60();
                case pageBlockEmbedPost.constructor: return new pageBlockEmbedPost();
                case pageBlockEmbedPost_layer82.constructor: return new pageBlockEmbedPost_layer82();
                case pageBlockCollage.constructor: return new pageBlockCollage();
                case pageBlockCollage_layer82.constructor: return new pageBlockCollage_layer82();
                case pageBlockSlideshow.constructor: return new pageBlockSlideshow();
                case pageBlockSlideshow_layer82.constructor: return new pageBlockSlideshow_layer82();
                case pageBlockChannel.constructor: return new pageBlockChannel();
                case pageBlockAudio.constructor: return new pageBlockAudio();
                case pageBlockAudio_layer82.constructor: return new pageBlockAudio_layer82();
                case pageBlockKicker.constructor: return new pageBlockKicker();
                case pageBlockTable.constructor: return new pageBlockTable();
                case pageBlockOrderedList.constructor: return new pageBlockOrderedList();
                case pageBlockOrderedList_layer226.constructor: return new pageBlockOrderedList_layer226();
                case pageBlockDetails.constructor: return new pageBlockDetails();
                case pageBlockRelatedArticles.constructor: return new pageBlockRelatedArticles();
                case pageBlockMap.constructor: return new pageBlockMap();
                case pageBlockHeading1.constructor: return new pageBlockHeading1();
                case pageBlockHeading2.constructor: return new pageBlockHeading2();
                case pageBlockHeading3.constructor: return new pageBlockHeading3();
                case pageBlockHeading4.constructor: return new pageBlockHeading4();
                case pageBlockHeading5.constructor: return new pageBlockHeading5();
                case pageBlockHeading6.constructor: return new pageBlockHeading6();
                case pageBlockMath.constructor: return new pageBlockMath();
                case inputPageBlockMap.constructor: return new inputPageBlockMap();
                case pageBlockThinking.constructor: return new pageBlockThinking();
            }
            return null;
        }

        public static PageBlock TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(PageBlock.class, fromConstructor(constructor), stream, constructor, exception);
        }
    }
    public static class pageBlockUnsupported extends PageBlock {
        public static final int constructor = 0x13567e8a;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }
    public static class pageBlockTitle extends PageBlock {
        public static final int constructor = 0x70abc3fd;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockSubtitle extends PageBlock {
        public static final int constructor = 0x8ffa9a1f;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockAuthorDate extends PageBlock {
        public static final int constructor = 0xbaafe5e0;

        public RichText author;
        public int published_date;

        public void readParams(InputSerializedData stream, boolean exception) {
            author = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            published_date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            author.serializeToStream(stream);
            stream.writeInt32(published_date);
        }
    }
    public static class pageBlockAuthorDate_layer60 extends pageBlockAuthorDate {
        public static final int constructor = 0x3d5b64f2;

        public void readParams(InputSerializedData stream, boolean exception) {
            String authorString = stream.readString(exception);
            author = new textPlain();
            ((textPlain) author).text = authorString;
            published_date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(((textPlain) author).text);
            stream.writeInt32(published_date);
        }
    }
    public static class pageBlockHeader extends PageBlock {
        public static final int constructor = 0xbfd064ec;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockSubheader extends PageBlock {
        public static final int constructor = 0xf12bb6e1;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockParagraph extends PageBlock {
        public static final int constructor = 0x467a0766;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockPreformatted extends PageBlock {
        public static final int constructor = 0xc070d93e;

        public String language;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            language = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            stream.writeString(language);
        }
    }
    public static class pageBlockFooter extends PageBlock {
        public static final int constructor = 0x48870999;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockDivider extends PageBlock {
        public static final int constructor = 0xdb20b188;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }
    public static class pageBlockAnchor extends PageBlock {
        public static final int constructor = 0xce0d37b0;

        public String name;

        public void readParams(InputSerializedData stream, boolean exception) {
            name = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(name);
        }
    }
    public static class pageBlockList extends PageBlock {
        public static final int constructor = 0xe4e88011;

        public boolean ordered;
        public ArrayList<PageListItem> items = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            items = Vector.deserialize(stream, PageListItem::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, items);
        }
    }
    public static class pageBlockList_layer82 extends pageBlockList {
        public static final int constructor = 0x3a58c7f4;

        public void readParams(InputSerializedData stream, boolean exception) {
            ordered = stream.readBool(exception);
            final ArrayList<RichText> richTexts = Vector.deserialize(stream, RichText::TLdeserialize, exception);
            for (RichText richText : richTexts) {
                TL_pageListItemText item = new TL_pageListItemText();
                item.text = richText;
                items.add(item);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(ordered);
            ArrayList<RichText> richTexts = new ArrayList<>(items.size());
            for (PageListItem item : items) {
                if (item instanceof TL_pageListItemText) {
                    richTexts.add(((TL_pageListItemText) item).text);
                }
            }
            Vector.serialize(stream, richTexts);
        }
    }
    public static class pageBlockBlockquote extends PageBlock {
        public static final int constructor = 0x263d7c26;

        public RichText caption;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockBlockquoteBlocks extends PageBlock {
        public static final int constructor = 0xe6e47c4;

        public ArrayList<PageBlock> blocks = new ArrayList<>();
        public RichText caption;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            caption = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, blocks);
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockPullquote extends PageBlock {
        public static final int constructor = 0x4f4456d3;

        public RichText caption;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockPhoto extends PageBlock {
        public static final int constructor = 0x1759c560;

        public int flags;
        public boolean spoiler;
        public long photo_id;
        public String url;
        public long webpage_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            spoiler = hasFlag(flags, FLAG_1);
            photo_id = stream.readInt64(exception);
            caption = PageCaption.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_0)) {
                url = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_0)) {
                webpage_id = stream.readInt64(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, spoiler);
            stream.writeInt32(flags);
            stream.writeInt64(photo_id);
            caption.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(url);
            }
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt64(webpage_id);
            }
        }
    }
    public static class pageBlockPhoto_layer82 extends pageBlockPhoto {
        public static final int constructor = 0xe9c69982;

        public void readParams(InputSerializedData stream, boolean exception) {
            photo_id = stream.readInt64(exception);
            caption = new PageCaption();
            caption.text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption.credit = new textEmpty();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(photo_id);
            caption.text.serializeToStream(stream);
        }
    }
    public static class pageBlockVideo extends PageBlock {
        public static final int constructor = 0x7c8fe7b6;

        public int flags;
        public boolean spoiler;
        public boolean autoplay;
        public boolean loop;
        public long video_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            autoplay = hasFlag(flags, FLAG_0);
            loop = hasFlag(flags, FLAG_1);
            spoiler = hasFlag(flags, FLAG_2);
            video_id = stream.readInt64(exception);
            caption = PageCaption.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, autoplay);
            flags = setFlag(flags, FLAG_1, loop);
            flags = setFlag(flags, FLAG_2, spoiler);
            stream.writeInt32(flags);
            stream.writeInt64(video_id);
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockVideo_layer82 extends pageBlockVideo {
        public static final int constructor = 0xd9d71866;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            autoplay = hasFlag(flags, FLAG_0);
            loop = hasFlag(flags, FLAG_1);
            video_id = stream.readInt64(exception);
            caption = new PageCaption();
            caption.text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption.credit = new textEmpty();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, autoplay);
            flags = setFlag(flags, FLAG_1, loop);
            stream.writeInt32(flags);
            stream.writeInt64(video_id);
            caption.text.serializeToStream(stream);
        }
    }
    public static class pageBlockCover extends PageBlock {
        public static final int constructor = 0x39f23300;

        public PageBlock cover;

        public void readParams(InputSerializedData stream, boolean exception) {
            cover = TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            cover.serializeToStream(stream);
        }
    }
    public static class pageBlockEmbed extends PageBlock {
        public static final int constructor = 0xa8718dc5;

        public int flags;
        public boolean full_width;
        public boolean allow_scrolling;
        public String url;
        public String html;
        public long poster_photo_id;
        public int w;
        public int h;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            full_width = hasFlag(flags, FLAG_0);
            allow_scrolling = hasFlag(flags, FLAG_3);
            if (hasFlag(flags, FLAG_1)) {
                url = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                html = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                poster_photo_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_5)) {
                w = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_5)) {
                h = stream.readInt32(exception);
            }
            caption = PageCaption.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, full_width);
            flags = setFlag(flags, FLAG_3, allow_scrolling);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeString(url);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeString(html);
            }
            if (hasFlag(flags, FLAG_4)) {
                stream.writeInt64(poster_photo_id);
            }
            if (hasFlag(flags, FLAG_5)) {
                stream.writeInt32(w);
            }
            if (hasFlag(flags, FLAG_5)) {
                stream.writeInt32(h);
            }
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockEmbed_layer82 extends pageBlockEmbed {
        public static final int constructor = 0xcde200d1;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            full_width = hasFlag(flags, FLAG_0);
            allow_scrolling = hasFlag(flags, FLAG_3);
            if (hasFlag(flags, FLAG_1)) {
                url = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                html = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                poster_photo_id = stream.readInt64(exception);
            }
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            caption = new PageCaption();
            caption.text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption.credit = new textEmpty();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, full_width);
            flags = setFlag(flags, FLAG_3, allow_scrolling);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeString(url);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeString(html);
            }
            if (hasFlag(flags, FLAG_4)) {
                stream.writeInt64(poster_photo_id);
            }
            stream.writeInt32(w);
            stream.writeInt32(h);
            caption.text.serializeToStream(stream);
        }
    }
    public static class pageBlockEmbed_layer60 extends pageBlockEmbed {
        public static final int constructor = 0xd935d8fb;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            full_width = hasFlag(flags, FLAG_0);
            allow_scrolling = hasFlag(flags, FLAG_3);
            if (hasFlag(flags, FLAG_1)) {
                url = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                html = stream.readString(exception);
            }
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            this.caption = new PageCaption();
            this.caption.text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            this.caption.credit = new textEmpty();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, full_width);
            flags = setFlag(flags, FLAG_3, allow_scrolling);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeString(url);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeString(html);
            }
            stream.writeInt32(w);
            stream.writeInt32(h);
            caption.text.serializeToStream(stream);
        }
    }
    public static class pageBlockEmbedPost extends PageBlock {
        public static final int constructor = 0xf259a80b;

        public String url;
        public long webpage_id;
        public long author_photo_id;
        public String author;
        public int date;
        public ArrayList<PageBlock> blocks = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
            webpage_id = stream.readInt64(exception);
            author_photo_id = stream.readInt64(exception);
            author = stream.readString(exception);
            date = stream.readInt32(exception);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            caption = PageCaption.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
            stream.writeInt64(webpage_id);
            stream.writeInt64(author_photo_id);
            stream.writeString(author);
            stream.writeInt32(date);
            Vector.serialize(stream, blocks);
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockEmbedPost_layer82 extends pageBlockEmbedPost {
        public static final int constructor = 0x292c7be9;

        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
            webpage_id = stream.readInt64(exception);
            author_photo_id = stream.readInt64(exception);
            author = stream.readString(exception);
            date = stream.readInt32(exception);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            caption = new PageCaption();
            caption.text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption.credit = new textEmpty();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
            stream.writeInt64(webpage_id);
            stream.writeInt64(author_photo_id);
            stream.writeString(author);
            stream.writeInt32(date);
            Vector.serialize(stream, blocks);
            caption.text.serializeToStream(stream);
        }
    }
    public static class pageBlockCollage extends PageBlock {
        public static final int constructor = 0x65a0fa4d;

        public ArrayList<PageBlock> items = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            items = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            caption = PageCaption.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, items);
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockCollage_layer82 extends pageBlockCollage {
        public static final int constructor = 0x8b31c4f;

        public void readParams(InputSerializedData stream, boolean exception) {
            items = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            caption = new PageCaption();
            caption.text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption.credit = new textEmpty();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, items);
            caption.text.serializeToStream(stream);
        }
    }
    public static class pageBlockSlideshow extends PageBlock {
        public static final int constructor = 0x31f9590;

        public ArrayList<PageBlock> items = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            items = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            caption = PageCaption.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, items);
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockSlideshow_layer82 extends pageBlockSlideshow {
        public static final int constructor = 0x130c8963;

        public void readParams(InputSerializedData stream, boolean exception) {
            items = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            caption = new PageCaption();
            caption.text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption.credit = new textEmpty();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, items);
            caption.text.serializeToStream(stream);
        }
    }
    public static class pageBlockChannel extends PageBlock {
        public static final int constructor = 0xef1751b5;

        public TLRPC.Chat channel;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel = TLRPC.Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            channel.serializeToStream(stream);
        }
    }
    public static class pageBlockAudio extends PageBlock {
        public static final int constructor = 0x804361ea;

        public long audio_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            audio_id = stream.readInt64(exception);
            caption = PageCaption.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(audio_id);
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockAudio_layer82 extends pageBlockAudio {
        public static final int constructor = 0x31b81a7f;

        public void readParams(InputSerializedData stream, boolean exception) {
            audio_id = stream.readInt64(exception);
            caption = new PageCaption();
            caption.text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption.credit = new textEmpty();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(audio_id);
            caption.text.serializeToStream(stream);
        }
    }
    public static class pageBlockKicker extends PageBlock {
        public static final int constructor = 0x1e148390;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockTable extends PageBlock {
        public static final int constructor = 0xbf4dea82;

        public int flags;
        public boolean bordered;
        public boolean striped;
        public RichText title;
        public ArrayList<pageTableRow> rows = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            bordered = hasFlag(flags, FLAG_0);
            striped = hasFlag(flags, FLAG_1);
            title = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            rows = Vector.deserialize(stream, pageTableRow::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, bordered);
            flags = setFlag(flags, FLAG_1, striped);
            stream.writeInt32(flags);
            title.serializeToStream(stream);
            Vector.serialize(stream, rows);
        }
    }
    public static class pageTableRow extends TLObject {
        public static final int constructor = 0xe0c0c5e5;

        public ArrayList<pageTableCell> cells = new ArrayList<>();

        public static pageTableRow TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final pageTableRow result = pageTableRow.constructor != constructor ? null : new pageTableRow();
            return TLdeserialize(pageTableRow.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            cells = Vector.deserialize(stream, pageTableCell::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, cells);
        }
    }
    public static class pageTableCell extends TLObject {
        public static final int constructor = 0x34566b6a;

        public int flags;
        public boolean header;
        public boolean align_center;
        public boolean align_right;
        public boolean valign_middle;
        public boolean valign_bottom;
        public RichText text;
        public int colspan;
        public int rowspan;

        public static pageTableCell TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final pageTableCell result = pageTableCell.constructor != constructor ? null : new pageTableCell();
            return TLdeserialize(pageTableCell.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            header = hasFlag(flags, FLAG_0);
            align_center = hasFlag(flags, FLAG_3);
            align_right = hasFlag(flags, FLAG_4);
            valign_middle = hasFlag(flags, FLAG_5);
            valign_bottom = hasFlag(flags, FLAG_6);
            if (hasFlag(flags, FLAG_7)) {
                text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_1)) {
                colspan = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                rowspan = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, header);
            flags = setFlag(flags, FLAG_3, align_center);
            flags = setFlag(flags, FLAG_4, align_right);
            flags = setFlag(flags, FLAG_5, valign_middle);
            flags = setFlag(flags, FLAG_6, valign_bottom);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_7)) {
                text.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt32(colspan);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeInt32(rowspan);
            }
        }
    }
    public static class pageBlockOrderedList extends PageBlock {
        public static final int constructor = 0x1fd6f6c1;

        public int flags;
        public boolean reversed;
        public ArrayList<PageListOrderedItem> items = new ArrayList<>();
        public int start;
        public String type;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            reversed = hasFlag(flags, FLAG_2);
            items = Vector.deserialize(stream, PageListOrderedItem::TLdeserialize, exception);
            if (hasFlag(flags, FLAG_0)) {
                start = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_1)) {
                type = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_2, reversed);
            stream.writeInt32(flags);
            Vector.serialize(stream, items);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(start);
            }
            if (hasFlag(flags, FLAG_1)) {
                stream.writeString(type);
            }
        }
    }
    public static class pageBlockOrderedList_layer226 extends pageBlockOrderedList {
        public static final int constructor = 0x9A8AE1E1;

        public void readParams(InputSerializedData stream, boolean exception) {
            items = Vector.deserialize(stream, PageListOrderedItem::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, items);
        }
    }
    public static class pageBlockDetails extends PageBlock {
        public static final int constructor = 0x76768bed;

        public int flags;
        public boolean open;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
        public RichText title;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            open = hasFlag(flags, FLAG_0);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            title = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, open);
            stream.writeInt32(flags);
            Vector.serialize(stream, blocks);
            title.serializeToStream(stream);
        }
    }
    public static class pageBlockRelatedArticles extends PageBlock {
        public static final int constructor = 0x16115a96;

        public RichText title;
        public ArrayList<pageRelatedArticle> articles = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            title = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            articles = Vector.deserialize(stream, pageRelatedArticle::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            title.serializeToStream(stream);
            Vector.serialize(stream, articles);
        }
    }
    public static class pageRelatedArticle extends TLObject {
        public static final int constructor = 0xb390dc08;

        public int flags;
        public String url;
        public long webpage_id;
        public String title;
        public String description;
        public long photo_id;
        public String author;
        public int published_date;

        public static pageRelatedArticle TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final pageRelatedArticle result = pageRelatedArticle.constructor != constructor ? null : new pageRelatedArticle();
            return TLdeserialize(pageRelatedArticle.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            url = stream.readString(exception);
            webpage_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                title = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_1)) {
                description = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                photo_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                published_date = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(url);
            stream.writeInt64(webpage_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(title);
            }
            if (hasFlag(flags, FLAG_1)) {
                stream.writeString(description);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeInt64(photo_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                stream.writeString(author);
            }
            if (hasFlag(flags, FLAG_4)) {
                stream.writeInt32(published_date);
            }
        }
    }
    public static class pageBlockMap extends PageBlock {
        public static final int constructor = 0xa44f3ef6;

        public TLRPC.GeoPoint geo;
        public int zoom;
        public int w;
        public int h;

        public void readParams(InputSerializedData stream, boolean exception) {
            geo = TLRPC.GeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
            zoom = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            caption = PageCaption.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            geo.serializeToStream(stream);
            stream.writeInt32(zoom);
            stream.writeInt32(w);
            stream.writeInt32(h);
            caption.serializeToStream(stream);
        }
    }
    public static class pageBlockHeading1 extends PageBlock {
        public static final int constructor = 0xbaff072f;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockHeading2 extends PageBlock {
        public static final int constructor = 0x96b2aec;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockHeading3 extends PageBlock {
        public static final int constructor = 0x67e731ad;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockHeading4 extends PageBlock {
        public static final int constructor = 0xb532772b;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockHeading5 extends PageBlock {
        public static final int constructor = 0xdbbe6c6a;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockHeading6 extends PageBlock {
        public static final int constructor = 0x682a41a9;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class pageBlockMath extends PageBlock {
        public static final int constructor = 0x59080c20;

        public String source;

        public void readParams(InputSerializedData stream, boolean exception) {
            source = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(source);
        }
    }
    public static class pageBlockThinking extends PageBlock {
        public static final int constructor = 0x3c29a3e2;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class inputPageBlockMap extends PageBlock {
        public static final int constructor = 0x574b617f;

        public TLRPC.InputGeoPoint geo;
        public int zoom;
        public int w;
        public int h;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            geo = TLRPC.InputGeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
            zoom = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            caption = PageCaption.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            geo.serializeToStream(stream);
            stream.writeInt32(zoom);
            stream.writeInt32(w);
            stream.writeInt32(h);
            caption.serializeToStream(stream);
        }
    }

    public static abstract class PageListOrderedItem extends TLObject {

        public int flags;
        public boolean checkbox;
        public boolean checked;
        public String num;
        public int value;
        public String type;

        public static PageListOrderedItem TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            PageListOrderedItem result = null;
            switch (constructor) {
                case TL_pageListOrderedItemText.constructor: result = new TL_pageListOrderedItemText(); break;
                case TL_pageListOrderedItemText_layer226.constructor: result = new TL_pageListOrderedItemText_layer226(); break;
                case TL_pageListOrderedItemBlocks.constructor: result = new TL_pageListOrderedItemBlocks(); break;
                case TL_pageListOrderedItemBlocks_layer226.constructor: result = new TL_pageListOrderedItemBlocks_layer226(); break;
            }
            return TLdeserialize(PageListOrderedItem.class, result, stream, constructor, exception);
        }
    }
    public static class TL_pageListOrderedItemText extends PageListOrderedItem {
        public static final int constructor = 0x15031189;

        public RichText text;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            checkbox = hasFlag(flags, FLAG_0);
            checked = hasFlag(flags, FLAG_1);
            if (hasFlag(flags, FLAG_2)) {
                num = stream.readString(exception);
            }
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_3)) {
                value = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                type = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, checkbox);
            flags = setFlag(flags, FLAG_1, checked);
            flags = setFlag(flags, FLAG_2, num != null);
            flags = setFlag(flags, FLAG_4, type != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_2)) {
                stream.writeString(num);
            }
            text.serializeToStream(stream);
            if (hasFlag(flags, FLAG_3)) {
                stream.writeInt32(value);
            }
            if (hasFlag(flags, FLAG_4)) {
                stream.writeString(type);
            }
        }
    }
    public static class TL_pageListOrderedItemText_layer226 extends TL_pageListOrderedItemText {
        public static final int constructor = 0x5e068047;

        public void readParams(InputSerializedData stream, boolean exception) {
            num = stream.readString(exception);
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(num);
            text.serializeToStream(stream);
        }
    }
    public static class TL_pageListOrderedItemBlocks extends PageListOrderedItem {
        public static final int constructor = 0x8FF2D5F0;

        public ArrayList<PageBlock> blocks = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            checkbox = hasFlag(flags, FLAG_0);
            checked = hasFlag(flags, FLAG_1);
            if (hasFlag(flags, FLAG_2)) {
                num = stream.readString(exception);
            }
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
            if (hasFlag(flags, FLAG_3)) {
                value = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                type = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, checkbox);
            flags = setFlag(flags, FLAG_1, checked);
            flags = setFlag(flags, FLAG_2, num != null);
            flags = setFlag(flags, FLAG_4, type != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_2)) {
                stream.writeString(num);
            }
            Vector.serialize(stream, blocks);
            if (hasFlag(flags, FLAG_3)) {
                stream.writeInt32(value);
            }
            if (hasFlag(flags, FLAG_4)) {
                stream.writeString(type);
            }
        }
    }
    public static class TL_pageListOrderedItemBlocks_layer226 extends TL_pageListOrderedItemBlocks {
        public static final int constructor = 0x98dd8936;

        public void readParams(InputSerializedData stream, boolean exception) {
            num = stream.readString(exception);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(num);
            Vector.serialize(stream, blocks);
        }
    }

    public static abstract class PageListItem extends TLObject {

        public int flags;
        public boolean checkbox;
        public boolean checked;

        public static PageListItem TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            PageListItem result = null;
            switch (constructor) {
                case TL_pageListItemText.constructor: result = new TL_pageListItemText(); break;
                case TL_pageListItemText_layer226.constructor: result = new TL_pageListItemText_layer226(); break;
                case TL_pageListItemBlocks.constructor: result = new TL_pageListItemBlocks(); break;
                case TL_pageListItemBlocks_226.constructor: result = new TL_pageListItemBlocks_226(); break;
            }
            return TLdeserialize(PageListItem.class, result, stream, constructor, exception);
        }
    }
    public static class TL_pageListItemText extends PageListItem {
        public static final int constructor = 0x2f58683c;

        public RichText text;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            checkbox = hasFlag(flags, FLAG_0);
            checked = hasFlag(flags, FLAG_1);
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, checkbox);
            flags = setFlag(flags, FLAG_1, checked);
            stream.writeInt32(flags);
            text.serializeToStream(stream);
        }
    }
    public static class TL_pageListItemText_layer226 extends TL_pageListItemText {
        public static final int constructor = 0xb92fb6cd;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
        }
    }
    public static class TL_pageListItemBlocks extends PageListItem {
        public static final int constructor = 0x63ca67aa;

        public ArrayList<PageBlock> blocks = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            checkbox = hasFlag(flags, FLAG_0);
            checked = hasFlag(flags, FLAG_1);
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, checkbox);
            flags = setFlag(flags, FLAG_1, checked);
            stream.writeInt32(flags);
            Vector.serialize(stream, blocks);
        }
    }
    public static class TL_pageListItemBlocks_226 extends TL_pageListItemBlocks {
        public static final int constructor = 0x25e073fc;

        public void readParams(InputSerializedData stream, boolean exception) {
            blocks = Vector.deserialize(stream, PageBlock::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, blocks);
        }
    }

    public static class PageCaption extends TLObject {
        public static final int constructor = 0x6f747657;

        public RichText text;
        public RichText credit;

        public static PageCaption TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final PageCaption result = PageCaption.constructor != constructor ? null : new PageCaption();
            return TLdeserialize(PageCaption.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            text = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            credit = RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            text.serializeToStream(stream);
            credit.serializeToStream(stream);
        }
    }

    public static class getRichMessage extends TLMethod<TLRPC.messages_Messages> {
        public static final int constructor = 0x501569cf;

        public TLRPC.InputPeer peer;
        public int id;

        @Override
        public TLRPC.messages_Messages deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.messages_Messages.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
        }
    }
}
