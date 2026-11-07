package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.StyleSpan;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.PersianFontManager;
import org.telegram.messenger.SharedConfig;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/** Comparative microbenchmarks, not device frame-time or acceptance thresholds. */
@RunWith(AndroidJUnit4.class)
public class PersianFontPerformanceTest {
    private static final int ROUNDS = 9;
    private static volatile Object consumedObject;
    private static volatile float consumedWidth;

    @Test
    public void reportOffOnTextCosts() throws Exception {
        boolean previousSetting = SharedConfig.useVazirmatnFont;
        try {
            PersianFontManager.regular();
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(20);
            StringBuilder mixed = new StringBuilder();
            for (int i = 0; i < 40; i++) {
                mixed.append("\u0633\u0644\u0627\u0645 \u062f\u0646\u06cc\u0627! English 123 \u06f1\u06f2\u06f3 \ud83d\ude42 ");
            }
            String text = mixed.toString();
            JSONArray cases = new JSONArray();
            compare(cases, "apply_mixed", 30, () -> consumedObject = PersianFontApplier.applyForPaint(text, paint));
            compare(cases, "apply_latin", 100, () -> consumedObject = PersianFontApplier.applyForPaint("English 123 !?", paint));
            compare(cases, "wrapped_layout_mixed", 10, () -> {
                CharSequence styled = PersianFontApplier.applyForPaint(text, paint);
                consumedObject = new StaticLayout(styled, paint, 720, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            });
            PersianFontCanvasText cache = new PersianFontCanvasText();
            compare(cases, "canvas_warm_cache", 300, () -> consumedWidth = cache.measure("\u06f1\u06f2\u06f3 \u067e\u06cc\u0627\u0645", paint));
            SharedConfig.useVazirmatnFont = true;
            CharSequence alreadyStyled = PersianFontApplier.applyForPaint(text, paint);
            compare(cases, "apply_already_styled", 30, () -> consumedObject = PersianFontApplier.applyForPaint(alreadyStyled, paint));
            SpannableStringBuilder editable = new SpannableStringBuilder(text);
            compare(cases, "apply_editable_unchanged", 30, () -> PersianFontApplier.applyToEditable(editable, paint));
            SpannableStringBuilder formatted = new SpannableStringBuilder(text);
            for (int start = 0; start < formatted.length(); start += 30) {
                formatted.setSpan(new StyleSpan(Typeface.BOLD), start, Math.min(start + 8, formatted.length()),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            compare(cases, "apply_formatted_mixed", 30, () -> consumedObject = PersianFontApplier.applyForPaint(formatted, paint));
            SharedConfig.useVazirmatnFont = true;
            CharSequence styledFormatted = PersianFontApplier.applyForPaint(formatted, paint);
            compare(cases, "reapply_formatted_mixed", 30, () -> consumedObject = PersianFontApplier.applyForPaint(styledFormatted, paint));

            JSONObject report = new JSONObject();
            report.put("sdk", Build.VERSION.SDK_INT);
            report.put("model", Build.MODEL);
            report.put("rounds", ROUNDS);
            report.put("clock", "System.nanoTime, elapsed time per operation");
            report.put("allocationScope", "Process-wide ART bytes; includes background activity and is not a per-thread allocation count");
            report.put("limitations", "Emulator microbenchmarks do not establish real-device scroll, typing, frame-time or release performance");
            report.put("cases", cases);
            Bundle results = new Bundle();
            results.putString("persianFontPerformance", report.toString());
            InstrumentationRegistry.getInstrumentation().addResults(results);
        } finally {
            SharedConfig.useVazirmatnFont = previousSetting;
            consumedObject = null;
        }
    }

    private static void compare(JSONArray output, String name, int iterations, Runnable operation) throws Exception {
        long[][] times = new long[2][ROUNDS];
        long[][] allocated = new long[2][ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            for (int order = 0; order < 2; order++) {
                int mode = (round + order) % 2;
                SharedConfig.useVazirmatnFont = mode == 1;
                for (int warmup = 0; warmup < 10; warmup++) {
                    operation.run();
                }
                long beforeBytes = allocatedBytes();
                long before = System.nanoTime();
                for (int iteration = 0; iteration < iterations; iteration++) {
                    operation.run();
                }
                times[mode][round] = (System.nanoTime() - before) / iterations;
                long afterBytes = allocatedBytes();
                allocated[mode][round] = beforeBytes >= 0 && afterBytes >= beforeBytes
                        ? (afterBytes - beforeBytes) / iterations : -1;
            }
        }
        for (int mode = 0; mode < 2; mode++) {
            Arrays.sort(times[mode]);
            Arrays.sort(allocated[mode]);
            assertTrue("The benchmark clock must advance", times[mode][ROUNDS / 2] > 0);
            JSONObject result = new JSONObject();
            result.put("case", name);
            result.put("enabled", mode == 1);
            result.put("iterationsPerRound", iterations);
            result.put("medianNanosecondsPerOp", times[mode][ROUNDS / 2]);
            result.put("maxRoundNanosecondsPerOp", times[mode][ROUNDS - 1]);
            result.put("medianProcessAllocatedBytesPerOp", allocated[mode][ROUNDS / 2]);
            output.put(result);
        }
    }

    private static long allocatedBytes() {
        String value = Debug.getRuntimeStat("art.gc.bytes-allocated");
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                // An unavailable runtime counter is reported as -1.
            }
        }
        return -1;
    }
}
