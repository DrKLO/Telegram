package org.telegram.ui.Components.Paint;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.Paint.Views.PaintTextOptionsView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PersistColorPalette {
    public final static int COLORS_COUNT = 14;

    private final static List<Integer> DEFAULT_COLORS = Arrays.asList(
            0xff1D99FF,
            0xff03BCD4,
            0xff39BA2B,
            0xffF9A30F,
            0xffFA6E16,
            0xffE83544,
            0xffB24DFF,
            0xffD7A07C,
            0xffAC734C,
            0xff90512C,
            0xff532E1F,
            0xff000000,
            0xff818181,
            0xffFFFFFF
    );
    private final static Integer DEFAULT_MARKER_COLOR = 0xff0a84ff;

    private static PersistColorPalette[] instances = new PersistColorPalette[UserConfig.MAX_ACCOUNT_COUNT];

    private SharedPreferences mConfig;
    private List<Integer> colors = new ArrayList<>(COLORS_COUNT);
    private List<Integer> pendingChange = new ArrayList<>(COLORS_COUNT);
    private Integer markerColor;

    private int currentBrush;
    private int currentAlignment;
    private int currentTextType;
    private float currentWeight;
    private String currentTypeface;
    private boolean fillShapes;

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
        this.currentBrush = currentBrush;
        mConfig.edit().putInt("brush", currentBrush).apply();
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
        pendingChange.addAll(DEFAULT_COLORS);
        saveColors();
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= COLORS_COUNT) {
            throw new IndexOutOfBoundsException("Color palette index should be in range 0 ... " + COLORS_COUNT);
        }
    }

    public int getColor(int index) {
        checkIndex(index);

        return colors.get(index);
    }

    public void selectColor(int color) {
        int i = colors.indexOf(color);
        if (i != -1) {
            selectColorIndex(i);
        } else {
            List<Integer> from = new ArrayList<>(pendingChange.isEmpty() ? colors : pendingChange);

            pendingChange.clear();
            pendingChange.add(color);
            pendingChange.addAll(from);
            pendingChange.remove(pendingChange.size() - 1);
        }
    }

    public void selectColorIndex(int index) {
        int color = colors.get(index);
        List<Integer> from = new ArrayList<>(pendingChange.isEmpty() ? colors : pendingChange);
        pendingChange.clear();
        pendingChange.add(color);
        for (int i = 0; i < COLORS_COUNT; i++) {
            if (from.get(i) != color) {
                pendingChange.add(from.get(i));
            }
        }
    }

    private void loadColors() {
        for (int i = 0; i < COLORS_COUNT; i++) {
            colors.add((int) mConfig.getLong("color_" + i, DEFAULT_COLORS.get(i)));
        }
        markerColor = (int) mConfig.getLong("color_marker", DEFAULT_MARKER_COLOR);
    }

    public void saveColors() {
        if (pendingChange.isEmpty()) {
            return;
        }

        SharedPreferences.Editor editor = mConfig.edit();
        for (int i = 0; i < COLORS_COUNT; i++) {
            editor.putLong("color_" + i, pendingChange.get(i));
        }
        editor.apply();

        colors.clear();
        colors.addAll(pendingChange);
        pendingChange.clear();
    }
}
