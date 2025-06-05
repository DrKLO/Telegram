package org.telegram.ui.Components.Paint;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class PaintTypeface {
    private final static boolean SYSTEM_FONTS_ENABLED = true;

    public static final PaintTypeface ROBOTO_MEDIUM = new PaintTypeface("roboto", "PhotoEditorTypefaceRoboto", new LazyTypeface(() -> AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)));
    public static final PaintTypeface ROBOTO_ITALIC = new PaintTypeface("italic", "PhotoEditorTypefaceItalic", new LazyTypeface(() -> AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC)));
    public static final PaintTypeface ROBOTO_SERIF = new PaintTypeface("serif", "PhotoEditorTypefaceSerif", new LazyTypeface(() -> Typeface.create("serif", Typeface.BOLD)));
    public static final PaintTypeface ROBOTO_CONDENSED = new PaintTypeface("condensed", "PhotoEditorTypefaceCondensed", new LazyTypeface(() -> AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf")));
    public static final PaintTypeface ROBOTO_MONO = new PaintTypeface ("mono", "PhotoEditorTypefaceMono", new LazyTypeface(() -> AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO)));
    public static final PaintTypeface MW_BOLD = new PaintTypeface("mw_bold", "PhotoEditorTypefaceMerriweather", new LazyTypeface(() -> AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_MERRIWEATHER_BOLD)));
    public static final PaintTypeface COURIER_NEW_BOLD = new PaintTypeface("courier_new_bold", "PhotoEditorTypefaceCourierNew", new LazyTypeface(() -> AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_COURIER_NEW_BOLD)));

    public final static List<PaintTypeface> BUILT_IN_FONTS = Arrays.asList(ROBOTO_MEDIUM, ROBOTO_ITALIC, ROBOTO_SERIF, ROBOTO_CONDENSED, ROBOTO_MONO, MW_BOLD, COURIER_NEW_BOLD);

    private static final List<String> preferable = Arrays.asList(
        "Google Sans",
        "Dancing Script",
        "Carrois Gothic SC",
        "Cutive Mono",
        "Droid Sans Mono",
        "Coming Soon"
    );

    private final String key;
    private final String nameKey;
    private final String name;
    private final Typeface typeface;
    private final LazyTypeface lazyTypeface;
    private final Font font;
    private Paint paint;

    private static class LazyTypeface {
        public interface LazyTypefaceLoader {
            Typeface load();
        }

        private final LazyTypefaceLoader loader;
        private Typeface typeface;
        public LazyTypeface(LazyTypefaceLoader loader) {
            this.loader = loader;
        }

        public Typeface get() {
            if (typeface == null) {
                typeface = loader.load();
            }
            return typeface;
        }
    }

    PaintTypeface(String key, String nameKey, Typeface typeface) {
        this.key = key;
        this.nameKey = nameKey;
        this.name = null;
        this.typeface = typeface;
        this.lazyTypeface = null;
        this.font = null;
    }

    PaintTypeface(String key, String nameKey, LazyTypeface lazyTypeface) {
        this.key = key;
        this.nameKey = nameKey;
        this.name = null;
        this.typeface = null;
        this.lazyTypeface = lazyTypeface;
        this.font = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    PaintTypeface(Font font, String name) {
        this.key = name;
        this.name = name;
        this.nameKey = null;
        this.typeface = null;
        this.lazyTypeface = new LazyTypeface(() -> Typeface.createFromFile(font.getFile()));
        this.font = font;
    }

    public boolean supports(String text) {
        if (this.font == null) {
            // TODO
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (paint == null) {
                paint = new Paint();
                paint.setTypeface(this.typeface);
            }
            return paint.hasGlyph(text);
        }
        // TODO
        return true;
    }

    public String getKey() {
        return key;
    }

    public Typeface getTypeface() {
        if (lazyTypeface != null) {
            return lazyTypeface.get();
        }
        return typeface;
    }

    public String getName() {
        if (name != null) {
            return name;
        }
        return LocaleController.getString(nameKey);
    }

    private static List<PaintTypeface> typefaces;
    public static boolean loadingTypefaces;

    private static void load() {
        if (typefaces != null || loadingTypefaces) {
            return;
        }
        loadingTypefaces = true;
        Utilities.themeQueue.postRunnable(() -> {
            ArrayList<PaintTypeface> typefaces = new ArrayList<PaintTypeface>(BUILT_IN_FONTS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && SYSTEM_FONTS_ENABLED) {
                Set<Font> fonts = SystemFonts.getAvailableFonts();
                Iterator<Font> i = fonts.iterator();
                HashMap<String, Family> families = new HashMap<>();
                while (i.hasNext()) {
                    Font font = i.next();
                    if (font.getFile().getName().contains("Noto"))
                        continue;
                    FontData data = parseFont(font);
                    if (data != null) {
                        Family family = families.get(data.family);
                        if (family == null) {
                            family = new Family();
                            families.put(family.family = data.family, family);
                        }
                        family.fonts.add(data);
                    }
                }

                for (String familyName : preferable) {
                    Family family = families.get(familyName);
                    if (family != null) {
                        FontData font = family.getBold();
                        if (font == null) {
                            font = family.getRegular();
                        }
                        if (font != null) {
                            typefaces.add(new PaintTypeface(font.font, font.getName()));
                        }
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                PaintTypeface.typefaces = typefaces;
                loadingTypefaces = false;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.customTypefacesLoaded);
            });
        });
    }

    public static List<PaintTypeface> get() {
        if (typefaces == null) {
            load();
            return BUILT_IN_FONTS;
        }
        return typefaces;
    }

    public static PaintTypeface find(String key) {
        if (key == null || TextUtils.isEmpty(key)) {
            return null;
        }
        List<PaintTypeface> typefaces = get();
        for (int i = 0; i < typefaces.size(); ++i) {
            PaintTypeface typeface = typefaces.get(i);
            if (typeface != null && TextUtils.equals(key, typeface.key)) {
                return typeface;
            }
        }
        return null;
    }

    static class Family {
        String family;
        ArrayList<FontData> fonts = new ArrayList<>();

        public FontData getRegular() {
            FontData regular = null;
            for (int j = 0; j < fonts.size(); ++j) {
                if ("Regular".equalsIgnoreCase(fonts.get(j).subfamily)) {
                    regular = fonts.get(j);
                    break;
                }
            }
            if (regular == null && !fonts.isEmpty()) {
                regular = fonts.get(0);
            }
            return regular;
        }

        @Nullable
        public FontData getBold() {
            for (int j = 0; j < fonts.size(); ++j) {
                if ("Bold".equalsIgnoreCase(fonts.get(j).subfamily)) {
                    return fonts.get(j);
                }
            }
            return null;
        }
    }

    static class FontData {
        Font font;
        String family;
        String subfamily;

        public String getName() {
            if ("Regular".equals(subfamily) || TextUtils.isEmpty(subfamily)) {
                return family;
            }
            return family + " " + subfamily;
        }
    }

    private static class NameRecord {
        final int platformID;
        final int encodingID;
        final int languageID;
        final int nameID;
        final int nameLength;
        final int stringOffset;

        public NameRecord(RandomAccessFile reader) throws IOException {
            platformID = reader.readUnsignedShort();
            encodingID = reader.readUnsignedShort();
            languageID = reader.readUnsignedShort();
            nameID = reader.readUnsignedShort();
            nameLength = reader.readUnsignedShort();
            stringOffset = reader.readUnsignedShort();
        }

        public String read(RandomAccessFile reader, int offset) throws IOException {
            reader.seek(offset + stringOffset);
            byte[] bytes = new byte[ nameLength ];
            reader.read(bytes);
            Charset charset;
            if (encodingID == 1) {
                charset = StandardCharsets.UTF_16BE;
            } else {
                charset = StandardCharsets.UTF_8;
            }
            return new String(bytes, charset);
        }
    }

    private static String parseString(RandomAccessFile reader, int offset, NameRecord nameRecord) throws IOException {
        if (nameRecord == null) {
            return null;
        }
        return nameRecord.read(reader, offset);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static FontData parseFont(Font font) {
        if (font == null) {
            return null;
        }
        final File file = font.getFile();
        if (file == null) {
            return null;
        }
        RandomAccessFile reader = null;
        try {
            reader = new RandomAccessFile(file, "r");
            final int version = reader.readInt();
            if (version != 0x00010000 && version != 0x4F54544F) {
                return null;
            }
            final int numTables = reader.readUnsignedShort();
            reader.skipBytes(2 + 2 + 2);
            for (int i = 0; i < numTables; ++i) {
                int tag = reader.readInt();
                reader.skipBytes(4);
                int offset = reader.readInt();
                int length = reader.readInt();

                if (tag == 0x6E616D65) {
                    reader.seek(offset + 2);
                    final int count = reader.readUnsignedShort();
                    final int storageOffset = reader.readUnsignedShort();

                    HashMap<Integer, NameRecord> records = new HashMap<>();
                    for (int j = 0; j < count; ++j) {
                        NameRecord record = new NameRecord(reader);
                        records.put(record.nameID, record);
                    }

                    FontData data = new FontData();
                    data.font = font;
                    data.family = parseString(reader, offset + storageOffset, records.get(1));
                    data.subfamily = parseString(reader, offset + storageOffset, records.get(2));
                    return data;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e2) {}
            }
        }
        return null;
    }
}
