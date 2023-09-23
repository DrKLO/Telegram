package org.telegram.ui.Components.Paint;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.Paint.Views.PaintTextOptionsView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class PersistColorPalette {

    public final static int COLOR_BLACK = 0xff000000;
    public final static int COLOR_WHITE = 0xffffffff;
    public final static int COLOR_RED = 0xffff453a;
    public final static int COLOR_ORANGE = 0xffff8a00;
    public final static int COLOR_YELLOW = 0xffffd60a;
    public final static int COLOR_GREEN = 0xff34c759;
    public final static int COLOR_LIGHT_BLUE = 0xff63e6e2;
    public final static int COLOR_BLUE = 0xff0a84ff;
    public final static int COLOR_VIOLET = 0xffbf5af2;

    private final static List<Integer> DEFAULT_MODIFIABLE_COLORS = Arrays.asList(
            0xffD7A07C,
            0xff7faffe,
            0xffA58FDB,
            0xffDB95AE,
            0xffBADC9F
    );

    private final static List<Integer> PRESET_COLORS = Arrays.asList(
            COLOR_RED,
            COLOR_ORANGE,
            COLOR_YELLOW,
            COLOR_GREEN,
            COLOR_LIGHT_BLUE,
            COLOR_BLUE,
            COLOR_VIOLET,
            COLOR_BLACK,
            COLOR_WHITE
    );

    public final static int MODIFIABLE_COLORS_COUNT = DEFAULT_MODIFIABLE_COLORS.size();
    public final static int PRESET_COLORS_COUNT = PRESET_COLORS.size();
    public final static int COLORS_COUNT = MODIFIABLE_COLORS_COUNT + PRESET_COLORS_COUNT;

    private final static int BRUSH_TEXT = -1;
    private static PersistColorPalette[] instances = new PersistColorPalette[UserConfig.MAX_ACCOUNT_COUNT];

    private final SharedPreferences mConfig;
    private final List<Integer> colors = new ArrayList<>(COLORS_COUNT);
    private final HashMap<Integer, Integer> brushColor = new HashMap<>(Brush.BRUSHES_LIST.size());
    private List<Integer> pendingChange = new ArrayList<>(COLORS_COUNT);
    private boolean needSaveBrushColor;

    private int currentBrush;
    private int currentAlignment;
    private int currentTextType;
    private float currentWeight;
    private String currentTypeface;
    private boolean fillShapes;
    private boolean inTextMode;

    public PersistColorPalette(int currentUser) {
        mConfig = ApplicationLoader.applicationContext.getSharedPreferences("photo_color_palette_" + currentUser, Context.MODE_PRIVATE);
        currentBrush = mConfig.getInt("brush", 0);
        currentWeight = mConfig.getFloat("weight", .5f);
        currentTypeface = mConfig.getString("typeface", "roboto");
        currentAlignment = mConfig.getInt("text_alignment", PaintTextOptionsView.ALIGN_LEFT);
        currentTextType = mConfig.getInt("text_type", 0);
        fillShapes = mConfig.getBoolean("fill_shapes", false);

        loadColors();
    }

    public static PersistColorPalette getInstance(int currentAccount) {
        if (instances[currentAccount] == null) {
            instances[currentAccount] = new PersistColorPalette(currentAccount);
        }
        return instances[currentAccount];
    }

    public int getCurrentTextType() {
        return currentTextType;
    }

    public void setCurrentTextType(int currentTextType) {
        this.currentTextType = currentTextType;
        mConfig.edit().putInt("text_type", currentTextType).apply();
    }

    public void setInTextMode(boolean inTextMode) {
        if (this.inTextMode != inTextMode) {
            this.inTextMode = inTextMode;
            if (inTextMode) {
                setCurrentBrush(BRUSH_TEXT, false);
            } else {
                setCurrentBrush(mConfig.getInt("brush", 0), false);
            }
        }
    }

    public int getCurrentAlignment() {
        return currentAlignment;
    }

    public void setCurrentAlignment(int currentAlignment) {
        this.currentAlignment = currentAlignment;
        mConfig.edit().putInt("text_alignment", currentAlignment).apply();
    }

    public String getCurrentTypeface() {
        return currentTypeface;
    }

    public void setCurrentTypeface(String currentTypeface) {
        this.currentTypeface = currentTypeface;
        mConfig.edit().putString("typeface", currentTypeface).apply();
    }

    public float getWeight(String key, float defaultWeight) {
        return mConfig.getFloat("weight_" + key, defaultWeight);
    }

    public void setWeight(String key, float weight) {
        mConfig.edit().putFloat("weight_" + key, weight).apply();
    }

    public float getCurrentWeight() {
        return currentWeight;
    }

    public void setCurrentWeight(float currentWeight) {
        this.currentWeight = currentWeight;
        mConfig.edit().putFloat("weight", currentWeight).apply();
    }

    public int getCurrentBrush() {
        return currentBrush;
    }

    public void setCurrentBrush(int currentBrush) {
        setCurrentBrush(currentBrush, true);
    }

    public void setCurrentBrush(int currentBrush, boolean saveBrush) {
        this.currentBrush = currentBrush;
        if (saveBrush) {
            mConfig.edit().putInt("brush", currentBrush).apply();
        }

        Integer color = brushColor.get(currentBrush);
        if (color != null) {
            selectColor(color, false);
            saveColors();
        }
    }

    public boolean getFillShapes() {
        return fillShapes;
    }

    public void toggleFillShapes() {
        this.fillShapes = !this.fillShapes;
        mConfig.edit().putBoolean("fill_shapes", fillShapes).apply();
    }

    public void cleanup() {
        pendingChange.clear();
        pendingChange.addAll(DEFAULT_MODIFIABLE_COLORS);
        SharedPreferences.Editor editor = mConfig.edit();
        for (int i = 0; i < Brush.BRUSHES_LIST.size(); i++) {
            editor.remove("brush_color_" + i);
        }
        editor.remove("brush_color_" + BRUSH_TEXT);
        brushColor.clear();
        editor.apply();

        saveColors();
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= COLORS_COUNT) {
            throw new IndexOutOfBoundsException("Color palette index should be in range 0 ... " + COLORS_COUNT);
        }
    }

    public int getColor(int index) {
        checkIndex(index);
        List<Integer> allColors = getAllColors();
        if (index >= allColors.size()) {
            if (index < PRESET_COLORS_COUNT) {
                return PRESET_COLORS.get(index);
            } else {
                return DEFAULT_MODIFIABLE_COLORS.get(index - PRESET_COLORS_COUNT);
            }
        }
        return allColors.get(index);
    }

    public int getCurrentColor() {
        Integer currentColor = brushColor.get(currentBrush);
        if (currentColor == null) {
            currentColor = (int) mConfig.getLong("brush_color_" + currentBrush, currentBrush == BRUSH_TEXT ? COLOR_WHITE : Brush.BRUSHES_LIST.get(currentBrush).getDefaultColor());
            brushColor.put(currentBrush, currentColor);
        }
        return currentColor;
    }

    public int getCurrentColorPosition() {
        int currentColor = getCurrentColor();
        List<Integer> allColors = getAllColors();
        for (int i = 0; i < allColors.size(); i++) {
            if (allColors.get(i) == currentColor) {
                return i;
            }
        }
        return 0;
    }

    private List<Integer> getAllColors() {
        List<Integer> allColors = new ArrayList<>(PRESET_COLORS);
        allColors.addAll(colors);
        return allColors;
    }

    public void selectColor(int color) {
        selectColor(color, true);
    }

    public void selectColor(int color, boolean updateBrush) {
        List<Integer> allColors = getAllColors();
        int i = allColors.indexOf(color);
        if (i != -1) {
            if (updateBrush) {
                setCurrentBrushColorByColorIndex(i);
            }
        } else {
            List<Integer> from = new ArrayList<>(pendingChange.isEmpty() ? colors : pendingChange);
            pendingChange.clear();
            pendingChange.add(color);

            for (int j = 0; j < from.size() - 1; j++) {
                pendingChange.add(from.get(j));
            }

            if (pendingChange.size() < DEFAULT_MODIFIABLE_COLORS.size()) {
                for (int j = pendingChange.size(); j < DEFAULT_MODIFIABLE_COLORS.size(); ++j) {
                    pendingChange.add(DEFAULT_MODIFIABLE_COLORS.get(j));
                }
            } else if (pendingChange.size() > DEFAULT_MODIFIABLE_COLORS.size()) {
                pendingChange = pendingChange.subList(0, DEFAULT_MODIFIABLE_COLORS.size());
            }
            if (updateBrush) {
                brushColor.put(currentBrush, color);
                needSaveBrushColor = true;
            }
        }
    }

    public void setCurrentBrushColorByColorIndex(int index) {
        int color = getColor(index);
        brushColor.put(currentBrush, color);
        needSaveBrushColor = true;
    }

    private void loadColors() {
        for (int i = 0; i < MODIFIABLE_COLORS_COUNT; i++) {
            colors.add((int) mConfig.getLong("color_" + i, DEFAULT_MODIFIABLE_COLORS.get(i)));
        }

        for (int i = 0; i < Brush.BRUSHES_LIST.size(); i++) {
            int color = (int) mConfig.getLong("brush_color_" + i, Brush.BRUSHES_LIST.get(i).getDefaultColor());
            brushColor.put(i, color);
        }

        int color = (int) mConfig.getLong("brush_color_" + BRUSH_TEXT, COLOR_WHITE);
        brushColor.put(BRUSH_TEXT, color);
    }

    public void resetCurrentColor() {
        setCurrentBrush(0);
    }

    public void saveColors() {
        if (pendingChange.isEmpty() && !needSaveBrushColor) {
            return;
        }
        SharedPreferences.Editor editor = mConfig.edit();
        if (!pendingChange.isEmpty()) {
            for (int i = 0; i < MODIFIABLE_COLORS_COUNT; i++) {
                editor.putLong("color_" + i, i < pendingChange.size() ? pendingChange.get(i) : (long) DEFAULT_MODIFIABLE_COLORS.get(i));
            }

            colors.clear();
            colors.addAll(pendingChange);
            pendingChange.clear();
        }

        if (needSaveBrushColor) {
            Integer currentBrushColor = brushColor.get(currentBrush);
            if (currentBrushColor != null) {
                editor.putLong("brush_color_" + currentBrush, currentBrushColor);
            }
            needSaveBrushColor = false;
        }
        editor.apply();
    }
}