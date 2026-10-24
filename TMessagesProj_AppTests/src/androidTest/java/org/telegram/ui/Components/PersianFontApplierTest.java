package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.TextViewWithLoading;
import org.telegram.ui.ActionBar.ActionBarAnimatedSubtitleOverlayContainer;
import org.telegram.ui.bots.BotAdView;
import org.telegram.ui.bots.BotCommandsMenuView;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.messenger.PersianFontManager;
import org.telegram.messenger.PersianTextUtils;
import org.telegram.messenger.SharedConfig;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PersianFontApplierTest {
    private boolean previousSetting;

    @Before
    public void enableFont() {
        previousSetting = SharedConfig.useVazirmatnFont;
        SharedConfig.useVazirmatnFont = true;
    }

    @After
    public void restoreSetting() {
        SharedConfig.useVazirmatnFont = previousSetting;
    }

    @Test
    public void formattingInsertedAfterFontCannotOverrideIt() {
        SpannableStringBuilder text = new SpannableStringBuilder("\u0633\u0644\u0627\u0645");
        TextPaint paint = new TextPaint();
        PersianFontApplier.applyToEditable(text, paint);
        text.setSpan(new TypefaceSpan(Typeface.DEFAULT), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        PersianFontApplier.applyToEditable(text, paint);
        for (MetricAffectingSpan span : text.getSpans(0, text.length(), MetricAffectingSpan.class)) {
            span.updateMeasureState(paint);
        }
        assertSame(PersianFontManager.regular(), paint.getTypeface());
        assertSame(text, PersianFontApplier.applyRegular(text));
    }

    @Test
    public void telegramMediumTypefaceKeepsSameWeightInPaintAndSpans() {
        String value = "\u0633\u0644\u0627\u0645";
        TextPaint paint = new TextPaint();
        paint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        assertTypeface(PersianFontApplier.applyForPaint(value, paint), PersianFontManager.medium());
        SpannableStringBuilder text = new SpannableStringBuilder(value);
        text.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertTypeface(PersianFontApplier.applyRegular(text), PersianFontManager.medium());
    }

    @Test
    public void sharedLayoutMeasuresTheStyledText() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(32);
        String value = "\u0633\u0644\u0627\u0645 English";
        StaticLayout layout = StaticLayoutEx.createStaticLayout(value, paint, 1000,
                Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false, TextUtils.TruncateAt.END, 1000, 1);
        assertNotNull(layout);
        assertTypeface(layout.getText(), PersianFontManager.regular());
        assertEquals(Layout.getDesiredWidth(PersianFontApplier.applyRegular(value), paint),
                layout.getLineWidth(0), 1f);
    }

    @Test
    public void bundledWeightsLoadAsDistinctFonts() {
        assertNotSame(Typeface.DEFAULT, PersianFontManager.regular());
        assertNotSame(Typeface.DEFAULT_BOLD, PersianFontManager.medium());
        assertNotSame(Typeface.DEFAULT_BOLD, PersianFontManager.bold());
        assertNotSame(PersianFontManager.regular(), PersianFontManager.medium());
        assertNotSame(PersianFontManager.medium(), PersianFontManager.bold());
    }

    @Test
    public void fontSpansStayInsideScriptAndFormattingBoundaries() {
        SpannableStringBuilder text = new SpannableStringBuilder("A \u0633\u0644\u0627\u0645 B");
        text.setSpan(new StyleSpan(Typeface.BOLD), 2, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Spanned styled = (Spanned) PersianFontApplier.applyRegular(text);
        PersianFontSpan[] spans = styled.getSpans(0, styled.length(), PersianFontSpan.class);
        assertEquals(2, spans.length);
        assertEquals(2, styled.getSpanStart(spans[0]));
        assertEquals(4, styled.getSpanEnd(spans[0]));
        assertEquals(4, styled.getSpanStart(spans[1]));
        assertEquals(6, styled.getSpanEnd(spans[1]));
        TextPaint paint = new TextPaint();
        spans[0].updateMeasureState(paint);
        assertSame(PersianFontManager.bold(), paint.getTypeface());
        spans[1].updateMeasureState(paint);
        assertSame(PersianFontManager.regular(), paint.getTypeface());
        assertEquals(text.toString(), styled.toString());
    }

    @Test
    public void scriptRangesPreserveLatinEmojiAndJoiners() {
        String text = "Hi \ud83d\ude42 \u0645\u06cc\u200c\u0631\u0648\u0645\u064e 123 \u06f1\u06f2";
        ArrayList<int[]> ranges = PersianTextUtils.findPersianOrArabicRanges(text);
        assertEquals(2, ranges.size());
        assertEquals("\u0645\u06cc\u200c\u0631\u0648\u0645\u064e", text.substring(ranges.get(0)[0], ranges.get(0)[1]));
        assertEquals("\u06f1\u06f2", text.substring(ranges.get(1)[0], ranges.get(1)[1]));
        assertSame("Latin 123 \ud83d\ude42", PersianFontApplier.applyRegular("Latin 123 \ud83d\ude42"));
    }

    @Test
    public void repeatedApplicationReusesSpansAndDisablingRemovesOnlyFont() {
        SpannableStringBuilder source = new SpannableStringBuilder("\u0633\u0644\u0627\u0645");
        StyleSpan bold = new StyleSpan(Typeface.BOLD);
        source.setSpan(bold, 0, source.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        CharSequence styled = PersianFontApplier.applyRegular(source);
        assertEquals(0, source.getSpans(0, source.length(), PersianFontSpan.class).length);
        assertSame(styled, PersianFontApplier.applyRegular(styled));
        assertTypeface(styled, PersianFontManager.bold());

        SharedConfig.useVazirmatnFont = false;
        Spanned cleaned = (Spanned) PersianFontApplier.applyRegular(styled);
        assertEquals(source.toString(), cleaned.toString());
        assertEquals(0, cleaned.getSpans(0, cleaned.length(), PersianFontSpan.class).length);
        assertSame(bold, cleaned.getSpans(0, cleaned.length(), StyleSpan.class)[0]);
    }

    @Test
    public void explicitRegularTypefaceOverridesBoldBase() {
        SpannableStringBuilder text = new SpannableStringBuilder("\u0633\u0644\u0627\u0645");
        text.setSpan(new TypefaceSpan(Typeface.DEFAULT), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertTypeface(PersianFontApplier.applyBold(text), PersianFontManager.regular());
    }

    @Test
    public void linkFormattingAndItalicArePreserved() {
        SpannableStringBuilder text = new SpannableStringBuilder("\u0633\u0644\u0627\u0645");
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = TextStyleSpan.FLAG_STYLE_BOLD | TextStyleSpan.FLAG_STYLE_ITALIC;
        URLSpanNoUnderline link = new URLSpanNoUnderline("https://example.org", run);
        text.setSpan(link, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Spanned styled = (Spanned) PersianFontApplier.applyRegular(text);
        assertSame(link, styled.getSpans(0, styled.length(), URLSpanNoUnderline.class)[0]);
        assertTypeface(styled, PersianFontManager.medium());
        TextPaint paint = new TextPaint();
        styled.getSpans(0, styled.length(), PersianFontSpan.class)[0].updateMeasureState(paint);
        assertEquals(-0.25f, paint.getTextSkewX(), 0f);
    }

    @Test
    public void monospaceAndReplacementSpansKeepOriginalFont() {
        String value = "\u0633\u0644\u0627\u0645";
        SpannableStringBuilder text = new SpannableStringBuilder(value);
        text.setSpan(new android.text.style.TypefaceSpan("monospace"), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertNoFont(text);

        text = new SpannableStringBuilder(value);
        text.setSpan(new URLSpanMono(value, 0, value.length(), (byte) 0), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertNoFont(text);

        text = new SpannableStringBuilder(value);
        text.setSpan(new ReplacementSpan() {
            @Override
            public int getSize(Paint paint, CharSequence source, int start, int end, Paint.FontMetricsInt fm) {
                return 20;
            }

            @Override
            public void draw(Canvas canvas, CharSequence source, int start, int end, float x,
                             int top, int y, int bottom, Paint paint) {
            }
        }, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertNoFont(text);

        TextPaint paint = new TextPaint();
        paint.setTypeface(Typeface.MONOSPACE);
        assertSame(value, PersianFontApplier.applyForPaint(value, paint));
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC));
        assertSame(value, PersianFontApplier.applyForPaint(value, paint));
    }

    @Test
    public void editablePreservesSelectionCompositionAndUnchangedWords() {
        SpannableStringBuilder text = new SpannableStringBuilder("\u0633\u0644\u0627\u0645 \u062f\u0646\u06cc\u0627");
        Object composing = new Object();
        text.setSpan(composing, 5, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_COMPOSING);
        Selection.setSelection(text, 2);
        TextPaint paint = new TextPaint();
        PersianFontApplier.applyToEditable(text, paint);
        PersianFontSpan first = text.getSpans(0, 4, PersianFontSpan.class)[0];
        text.append("\u0645");
        PersianFontApplier.applyToEditable(text, paint);
        assertSame(first, text.getSpans(0, 4, PersianFontSpan.class)[0]);
        assertEquals(2, Selection.getSelectionStart(text));
        assertEquals(5, text.getSpanStart(composing));
        assertTrue((text.getSpanFlags(composing) & Spanned.SPAN_COMPOSING) != 0);
        assertEquals("\u0633\u0644\u0627\u0645 \u062f\u0646\u06cc\u0627\u0645", text.toString());
        SharedConfig.useVazirmatnFont = false;
        PersianFontApplier.applyToEditable(text, paint);
        assertEquals(0, text.getSpans(0, text.length(), PersianFontSpan.class).length);
        assertEquals(2, Selection.getSelectionStart(text));
    }

    @Test
    public void nativeTextVariantsRefreshWeightAfterTextIsSet() {
        onUiThread(() -> {
            TextView[] views = {
                    new PersianTextView(ApplicationLoader.applicationContext),
                    new AnimatedEmojiSpan.TextViewEmojis(ApplicationLoader.applicationContext),
                    new LinkSpanDrawable.LinksTextView(ApplicationLoader.applicationContext),
                    new SpoilersTextView(ApplicationLoader.applicationContext),
                    new TextViewWithLoading(ApplicationLoader.applicationContext)
            };
            for (TextView view : views) {
                view.setText("\u0633\u0644\u0627\u0645 English");
                view.setTypeface(Typeface.DEFAULT_BOLD);
                assertTypeface(view.getText(), PersianFontManager.bold());
                view.setTypeface(Typeface.DEFAULT);
                assertTypeface(view.getText(), PersianFontManager.regular());
            }
            SharedConfig.useVazirmatnFont = false;
            for (TextView view : views) {
                measureView(view);
                assertEquals(0, ((Spanned) view.getText()).getSpans(0,
                        view.length(), PersianFontSpan.class).length);
            }
        });
    }

    @Test
    public void refreshingEditableTextViewPreservesBufferAndSelection() {
        onUiThread(() -> {
            PersianTextView view = new PersianTextView(ApplicationLoader.applicationContext);
            view.setText("\u0633\u0644\u0627\u0645", TextView.BufferType.EDITABLE);
            Editable buffer = (Editable) view.getText();
            Selection.setSelection(buffer, 1, 3);
            view.getPaint().setFakeBoldText(true);
            measureView(view);
            assertSame(buffer, view.getText());
            assertEquals(1, Selection.getSelectionStart(buffer));
            assertEquals(3, Selection.getSelectionEnd(buffer));
            assertTypeface(buffer, PersianFontManager.bold());
            SharedConfig.useVazirmatnFont = false;
            view.forceLayout();
            measureView(view);
            assertSame(buffer, view.getText());
            assertEquals(0, buffer.getSpans(0, buffer.length(), PersianFontSpan.class).length);
            assertEquals(1, Selection.getSelectionStart(buffer));
            assertEquals(3, Selection.getSelectionEnd(buffer));
        });
    }

    @Test
    public void richInputFormattingRefreshesFontWithoutPageDelegate() {
        onUiThread(() -> {
            EditTextCaption view = new EditTextCaption(ApplicationLoader.applicationContext, null);
            view.setTypeface(Typeface.DEFAULT);
            view.setText("\u0633\u0644\u0627\u0645");
            Editable buffer = view.getText();
            Selection.setSelection(buffer, 1, 3);
            view.addStyle(TextStyleSpan.FLAG_STYLE_BOLD, 0, buffer.length());
            assertTypeface(buffer, PersianFontManager.medium());
            view.removeStyle(TextStyleSpan.FLAG_STYLE_BOLD, 0, buffer.length());
            assertTypeface(buffer, PersianFontManager.regular());
            view.addStyle(TextStyleSpan.FLAG_STYLE_MONO, 0, buffer.length());
            assertEquals(0, buffer.getSpans(0, buffer.length(), PersianFontSpan.class).length);
            view.removeStyle(TextStyleSpan.FLAG_STYLE_MONO, 0, buffer.length());
            assertTypeface(buffer, PersianFontManager.regular());
            assertSame(buffer, view.getText());
            assertEquals(1, Selection.getSelectionStart(buffer));
            assertEquals(3, Selection.getSelectionEnd(buffer));
        });
    }

    @Test
    public void canvasMeasurementTracksFontSettingAndPaintChanges() {
        PersianFontCanvasText text = new PersianFontCanvasText();
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(32);
        String value = "\u0633\u0644\u0627\u0645 English";
        assertEquals(Layout.getDesiredWidth(PersianFontApplier.applyForPaint(value, paint), paint),
                text.measure(value, paint), 0.01f);
        paint.setTextSize(48);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        assertEquals(Layout.getDesiredWidth(PersianFontApplier.applyForPaint(value, paint), paint),
                text.measure(value, paint), 0.01f);
        SharedConfig.useVazirmatnFont = false;
        assertEquals(paint.measureText(value), text.measure(value, paint), 0.01f);
        SharedConfig.useVazirmatnFont = true;
        assertEquals(Layout.getDesiredWidth(PersianFontApplier.applyForPaint(value, paint), paint),
                text.measure(value, paint), 0.01f);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(Color.RED);
        paint.setAlpha(123);
        Bitmap bitmap = Bitmap.createBitmap(500, 100, Bitmap.Config.ARGB_8888);
        try {
            text.draw(new Canvas(bitmap), value, 450, 70, paint);
            assertSame(Typeface.DEFAULT_BOLD, paint.getTypeface());
            assertEquals(Paint.Align.RIGHT, paint.getTextAlign());
            assertEquals(123, paint.getAlpha());
            assertEquals(255, Color.red(paint.getColor()));
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void latinCanvasLabelsKeepNativeRendering() {
        PersianFontCanvasText text = new PersianFontCanvasText();
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(32);
        String value = "English 123";
        Bitmap expected = Bitmap.createBitmap(300, 80, Bitmap.Config.ARGB_8888);
        Bitmap actual = Bitmap.createBitmap(300, 80, Bitmap.Config.ARGB_8888);
        try {
            new Canvas(expected).drawText(value, 10, 50, paint);
            text.draw(new Canvas(actual), value, 10, 50, paint);
            assertTrue(expected.sameAs(actual));
            assertEquals(paint.measureText(value), text.measure(value, paint), 0f);
        } finally {
            expected.recycle();
            actual.recycle();
        }
    }

    @Test
    public void folderTitleCacheRefreshesAfterFontAndWeightChanges() {
        onUiThread(() -> {
            FilterTabsView view = new FilterTabsView(ApplicationLoader.applicationContext, null);
            view.textPaint.setTypeface(Typeface.DEFAULT);
            FilterTabsView.Tab tab = view.new Tab(1, "\u0633\u0644\u0627\u0645", false);
            assertTypeface(tab.title, PersianFontManager.regular());
            CharSequence first = tab.title;
            tab.getWidth(false);
            tab.getWidth(false);
            assertSame(first, tab.title);
            SharedConfig.useVazirmatnFont = false;
            tab.getWidth(false);
            assertEquals(0, ((Spanned) tab.title).getSpans(0, tab.title.length(), PersianFontSpan.class).length);
            SharedConfig.useVazirmatnFont = true;
            view.textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            tab.getWidth(false);
            assertTypeface(tab.title, PersianFontManager.bold());
        });
    }

    @Test
    public void headersAndNativeBoldKeepBoldWeight() {
        SpannableStringBuilder text = new SpannableStringBuilder("\u0633\u0644\u0627\u0645");
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.header = true;
        text.setSpan(new TextStyleSpan(run), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertTypeface(PersianFontApplier.applyRegular(text), PersianFontManager.bold());
        TextPaint paint = new TextPaint();
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        assertTypeface(PersianFontApplier.applyForPaint(text.toString(), paint), PersianFontManager.bold());
    }

    @Test
    public void counterDigitsRefreshWithoutChangingTextOrTextSize() {
        onUiThread(() -> {
            CounterView view = new CounterView(ApplicationLoader.applicationContext, null);
            CounterView.CounterDrawable counter = view.counterDrawable;
            String digits = "\u06f1\u06f2\u06f3";
            float size = counter.textPaint.getTextSize();
            counter.setText(digits, false);
            assertTypeface(counterLayout(counter).getText(), PersianFontManager.medium());
            assertEquals(size, counter.textPaint.getTextSize(), 0f);
            SharedConfig.useVazirmatnFont = false;
            counter.setText(digits, false);
            CharSequence plain = counterLayout(counter).getText();
            assertFalse(plain instanceof Spanned && ((Spanned) plain)
                    .getSpans(0, plain.length(), PersianFontSpan.class).length != 0);
            SharedConfig.useVazirmatnFont = true;
            counter.setText(digits, false);
            assertTypeface(counterLayout(counter).getText(), PersianFontManager.medium());
            assertEquals(digits, counterLayout(counter).getText().toString());
            assertEquals(size, counter.textPaint.getTextSize(), 0f);
        });
    }

    @Test
    public void botAdTitlesAndExplanationUseScriptFont() {
        onUiThread(() -> {
            BotAdView view = new BotAdView(ApplicationLoader.applicationContext, null);
            String value = "\u0627\u06cc\u0646";
            view.titleView.setText(value);
            view.channelTitleView.setText(value);
            view.removeView.setText(value);
            assertTypeface(view.titleView.getText(), PersianFontManager.medium());
            assertTypeface(view.channelTitleView.getText(), PersianFontManager.medium());
            assertTypeface(view.removeView.getText(), PersianFontManager.regular());
        });
    }

    @Test
    public void hiddenCounterDigitsReserveStyledWidth() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(AndroidUtilities.bold());
        paint.setTextSize(48);
        String digits = "\u06f1\u06f2\u06f3";
        float fullWidth = Layout.getDesiredWidth(PersianFontApplier.applyForPaint(digits, paint), paint);
        SpannableStringBuilder masked = new SpannableStringBuilder(digits);
        EmptyStubSpan placeholder = new EmptyStubSpan();
        masked.setSpan(placeholder, 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        CharSequence styled = PersianFontApplier.applyForPaint(masked, paint);
        assertSame(placeholder, ((Spanned) styled).getSpans(1, 2, EmptyStubSpan.class)[0]);
        assertEquals(fullWidth, Layout.getDesiredWidth(styled, paint), 1.1f);
        assertEquals(48f, paint.getTextSize(), 0f);
    }

    @Test
    public void opticalSizeMatchesMeasurementAndDrawingWithoutChangingBasePaint() {
        TextPaint base = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        base.setTextSize(100f);
        CharSequence styled = PersianFontApplier.applyForPaint("\u0633\u0644\u0627\u0645", base);
        PersianFontSpan span = ((Spanned) styled).getSpans(0, styled.length(), PersianFontSpan.class)[0];
        TextPaint measured = new TextPaint(base);
        TextPaint drawn = new TextPaint(base);
        span.updateMeasureState(measured);
        span.updateDrawState(drawn);
        assertEquals(94f, measured.getTextSize(), 0.001f);
        assertEquals(measured.getTextSize(), drawn.getTextSize(), 0f);
        assertEquals(100f, base.getTextSize(), 0f);
        assertSame(styled, PersianFontApplier.applyForPaint(styled, base));
        assertSame("English 123", PersianFontApplier.applyForPaint("English 123", base));
        SharedConfig.useVazirmatnFont = false;
        CharSequence plain = PersianFontApplier.applyForPaint(styled, base);
        assertEquals(0, ((Spanned) plain).getSpans(0, plain.length(), PersianFontSpan.class).length);
        assertEquals(100f, base.getTextSize(), 0f);
    }

    @Test
    public void proxySubtitleUsesFontAndRefreshesWhenDisabled() {
        onUiThread(() -> {
            View parent = new View(ApplicationLoader.applicationContext);
            ActionBarAnimatedSubtitleOverlayContainer container = new ActionBarAnimatedSubtitleOverlayContainer(
                    ApplicationLoader.applicationContext, null, new EllipsizeSpanAnimator(parent));
            container.setText("\u062a\u0646\u0638\u06cc\u0645", false);
            assertEquals(1, container.getChildCount());
            TextView subtitle = (TextView) container.getChildAt(0);
            assertTypeface(subtitle.getText(), PersianFontManager.regular());
            SharedConfig.useVazirmatnFont = false;
            measureView(subtitle);
            Spanned plain = (Spanned) subtitle.getText();
            assertEquals(0, plain.getSpans(0, plain.length(), PersianFontSpan.class).length);
        });
    }

    @Test
    public void botMenuLabelIsVisibleInsideButtonForBothScriptsAndFontSettings() {
        onUiThread(() -> {
            BotCommandsMenuView view = new BotCommandsMenuView(ApplicationLoader.applicationContext);
            view.setDrawBackgroundDrawable(false);
            view.setExpanded(true, false);
            for (boolean enabled : new boolean[] {false, true}) {
                SharedConfig.useVazirmatnFont = enabled;
                for (String label : new String[] {"\u0645\u0646\u0648", "Menu"}) {
                    view.setMenuText(label);
                    measureView(view);
                    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                    Bitmap image = Bitmap.createBitmap(view.getMeasuredWidth() + AndroidUtilities.dp(400),
                            view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                    try {
                        view.draw(new Canvas(image));
                        assertTrue(hasVisiblePixels(image, AndroidUtilities.dp(34), view.getMeasuredWidth()));
                        assertFalse(hasVisiblePixels(image, view.getMeasuredWidth() + AndroidUtilities.dp(2), image.getWidth()));
                    } finally {
                        image.recycle();
                    }
                }
            }
        });
    }

    private static boolean hasVisiblePixels(Bitmap image, int left, int right) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = left; x < right; x++) {
                if (Color.alpha(image.getPixel(x, y)) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void dedicatedPunctuationUsesFontInNativeTextView() {
        onUiThread(() -> {
            PersianTextView view = new PersianTextView(ApplicationLoader.applicationContext);
            String value = "\u061f\u060c\u061b\u06d4\u066a\u066b\u066c";
            view.setText(value);
            assertTypeface(view.getText(), PersianFontManager.regular());
            assertEquals(value, view.getText().toString());
            SharedConfig.useVazirmatnFont = false;
            measureView(view);
            Spanned plain = (Spanned) view.getText();
            assertEquals(0, plain.getSpans(0, plain.length(), PersianFontSpan.class).length);
        });
    }

    @Test
    public void contextualPunctuationPreservesFormattingAndCodeExclusions() {
        String value = "\u0633\u0644\u0627\u0645! English?";
        SpannableStringBuilder text = new SpannableStringBuilder(value);
        StyleSpan bold = new StyleSpan(Typeface.BOLD);
        text.setSpan(bold, 4, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Spanned styled = (Spanned) PersianFontApplier.applyRegular(text);
        assertSame(bold, styled.getSpans(4, 5, StyleSpan.class)[0]);
        PersianFontSpan[] punctuation = styled.getSpans(4, 5, PersianFontSpan.class);
        assertEquals(1, punctuation.length);
        TextPaint paint = new TextPaint();
        punctuation[0].updateMeasureState(paint);
        assertSame(PersianFontManager.bold(), paint.getTypeface());
        assertEquals(0, styled.getSpans(value.length() - 1, value.length(), PersianFontSpan.class).length);
        assertEquals(value, styled.toString());

        SpannableStringBuilder code = new SpannableStringBuilder("\u061f!");
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = TextStyleSpan.FLAG_STYLE_CODE;
        code.setSpan(new TextStyleSpan(run), 0, code.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertNoFont(code);
        assertSame("English?!", PersianFontApplier.applyRegular("English?!"));
    }

    @Test
    public void animatedPunctuationKeepsContextAcrossSplitsAndSizeChanges() {
        onUiThread(() -> {
            for (boolean words : new boolean[] {false, true}) {
                AnimatedTextView.AnimatedTextDrawable drawable = new AnimatedTextView.AnimatedTextDrawable(words, true, true);
                drawable.setBounds(0, 0, 600, 100);
                drawable.setTextSize(32);
                String word = "\u0633\u0644\u0627\u0645";
                drawable.setText(word + " " + word + "?", false);
                try {
                    drawable.setText(word + " " + word + "!", true);
                    assertAnimatedExclamationFont(drawable, true);
                    drawable.setTextSize(48);
                    assertAnimatedExclamationFont(drawable, true);
                    drawable.cancelAnimation();
                    SharedConfig.useVazirmatnFont = false;
                    drawable.setText(word + " " + word + "!", false);
                    assertAnimatedExclamationFont(drawable, false);
                    SharedConfig.useVazirmatnFont = true;
                    drawable.setTextSize(32);
                    assertAnimatedExclamationFont(drawable, true);
                } finally {
                    drawable.cancelAnimation();
                }
            }
        });
    }

    private static void assertAnimatedExclamationFont(AnimatedTextView.AnimatedTextDrawable drawable, boolean enabled) {
        try {
            java.lang.reflect.Field partsField = drawable.getClass().getDeclaredField("currentParts");
            partsField.setAccessible(true);
            Object[] parts = (Object[]) partsField.get(drawable);
            boolean found = false;
            boolean stablePart = false;
            for (Object part : parts) {
                java.lang.reflect.Field oppositeField = part.getClass().getDeclaredField("toOppositeIndex");
                oppositeField.setAccessible(true);
                stablePart |= oppositeField.getInt(part) >= 0;
                java.lang.reflect.Field layoutField = part.getClass().getDeclaredField("layout");
                layoutField.setAccessible(true);
                CharSequence text = ((StaticLayout) layoutField.get(part)).getText();
                int index = TextUtils.indexOf(text, '!');
                if (index >= 0) {
                    found = true;
                    int fonts = text instanceof Spanned
                            ? ((Spanned) text).getSpans(index, index + 1, PersianFontSpan.class).length : 0;
                    assertEquals(enabled ? 1 : 0, fonts);
                }
            }
            assertTrue("The animated label must retain its exclamation mark", found);
            if (drawable.isAnimating()) {
                assertTrue("Unchanged words must retain their stable animation parts", stablePart);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static StaticLayout counterLayout(CounterView.CounterDrawable counter) {
        try {
            java.lang.reflect.Field field = CounterView.CounterDrawable.class.getDeclaredField("countLayout");
            field.setAccessible(true);
            return (StaticLayout) field.get(counter);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void onUiThread(Runnable action) {
        FutureTask<Void> task = new FutureTask<>(action, null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
        try {
            task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (ExecutionException e) {
            throw new AssertionError(e.getCause());
        }
    }

    private static void measureView(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST));
    }

    private static void assertNoFont(Spanned text) {
        Spanned result = (Spanned) PersianFontApplier.applyRegular(text);
        assertEquals(0, result.getSpans(0, result.length(), PersianFontSpan.class).length);
    }

    private static void assertTypeface(CharSequence text, Typeface expected) {
        Spanned styled = (Spanned) text;
        PersianFontSpan[] spans = styled.getSpans(0, styled.length(), PersianFontSpan.class);
        assertEquals(1, spans.length);
        TextPaint paint = new TextPaint();
        spans[0].updateMeasureState(paint);
        assertSame(expected, paint.getTypeface());
    }
}
