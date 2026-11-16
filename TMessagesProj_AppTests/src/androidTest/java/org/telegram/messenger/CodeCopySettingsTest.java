package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableString;
import android.text.Spanned;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.utils.code.highlight.CodeHighlighting;

import java.util.ArrayList;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CodeCopySettingsTest {
    @Test
    public void defaultAndCustomBoundaries() {
        for (int minimum : new int[]{1, 10, 75, 120, Integer.MAX_VALUE}) {
            assertFalse(CodeCopySettings.shouldShow(minimum - 1, minimum, false));
            assertTrue(CodeCopySettings.shouldShow(minimum, minimum, false));
            assertFalse(CodeCopySettings.shouldShow(minimum, minimum, true));
        }
        assertFalse(CodeCopySettings.shouldShow(74, 75, false));
        assertTrue(CodeCopySettings.shouldShow(75, 75, false));
        assertTrue(CodeCopySettings.shouldShow(76, 75, false));
        assertFalse(CodeCopySettings.shouldShow(0, 1, false));
        assertEquals(75, CodeCopySettings.normalize(0));
        assertEquals(75, CodeCopySettings.normalize(-1));
    }

    @Test
    public void inputValidation() {
        for (String invalid : new String[]{null, "", "0", "-1", "+1", "1.5", " 75", "75 ", "abc", "2147483648"}) {
            assertNull(invalid, CodeCopySettings.parse(invalid));
        }
        assertEquals(Integer.valueOf(1), CodeCopySettings.parse("1"));
        assertEquals(Integer.valueOf(75), CodeCopySettings.parse("75"));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), CodeCopySettings.parse("2147483647"));
    }

    @Test
    public void persistedValues() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences("code_copy_regression", Context.MODE_PRIVATE);
        try {
            preferences.edit().clear().commit();
            assertEquals(75, SharedConfig.readCodeCopyMinLength(preferences));
            for (int value : new int[]{1, 75, 120, Integer.MAX_VALUE}) {
                preferences.edit().putInt(CodeCopySettings.KEY, value).commit();
                assertEquals(value, SharedConfig.readCodeCopyMinLength(preferences));
            }
            for (int value : new int[]{0, -1, Integer.MIN_VALUE}) {
                preferences.edit().putInt(CodeCopySettings.KEY, value).commit();
                assertEquals(75, SharedConfig.readCodeCopyMinLength(preferences));
            }
            preferences.edit().putString(CodeCopySettings.KEY, "10").commit();
            assertEquals(75, SharedConfig.readCodeCopyMinLength(preferences));
        } finally {
            preferences.edit().clear().commit();
        }
    }

    @Test
    public void realLayoutAndHitTestingUseSameEligibility() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Theme.createCommonMessageResources();
            int previous = SharedConfig.getCodeCopyMinLength();
            try {
                for (int minimum : new int[]{1, 10, 75, 120}) {
                    SharedConfig.setCodeCopyMinLength(minimum);
                    for (int length : new int[]{minimum - 1, minimum, minimum + 1}) {
                        for (String language : new String[]{null, "java"}) {
                            for (boolean protectedContent : new boolean[]{false, true}) {
                                MessageObject.TextLayoutBlock block = new MessageObject.TextLayoutBlock();
                                block.code = true;
                                block.layoutCode(language, length, protectedContent);
                                boolean visible = length >= minimum && !protectedContent;
                                assertEquals(visible, block.hasCodeCopyButton);
                                // The exact bottom edge belongs to the button, for both body and caption.
                                block.height = 100;
                                block.padTop = 4;
                                block.padBottom = visible ? AndroidUtilities.dp(38) + 4 : 4;
                                float bottom = block.padTop + block.height + block.padBottom;
                                assertEquals(visible, block.isCodeCopyHit(1, bottom, 200));
                                assertEquals(visible, block.isCodeCopyHit(200, bottom - AndroidUtilities.dp(38), 200));
                                assertFalse(block.isCodeCopyHit(0, bottom, 200));
                                assertFalse(block.isCodeCopyHit(201, bottom, 200));
                                assertFalse(block.isCodeCopyHit(1, bottom + 1, 200));
                                block.quote = true;
                                assertFalse(block.isCodeCopyHit(1, bottom, 200));
                            }
                        }
                    }
                }
                MessageObject.TextLayoutBlock compact = new MessageObject.TextLayoutBlock();
                compact.layoutCompactCode("java", false);
                assertTrue(compact.hasCodeCopyButton);
                compact.layoutCompactCode("java", true);
                assertFalse(compact.hasCodeCopyButton);
            } finally {
                SharedConfig.setCodeCopyMinLength(previous);
            }
        });
    }

    @Test
    public void shortCaptionReservesButtonSpace() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Theme.createCommonMessageResources();
            int previous = SharedConfig.getCodeCopyMinLength();
            try {
                SpannableString text = new SpannableString("x");
                text.setSpan(new CodeHighlighting.Span(true, 0, null, null, "x"),
                        0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                SharedConfig.setCodeCopyMinLength(75);
                MessageObject.TextLayoutBlocks original = new MessageObject.TextLayoutBlocks(
                        null, text, Theme.chat_msgTextPaint, AndroidUtilities.dp(300));
                assertFalse(original.textLayoutBlocks.get(0).hasCodeCopyButton);
                SharedConfig.setCodeCopyMinLength(1);
                MessageObject.TextLayoutBlocks lowered = new MessageObject.TextLayoutBlocks(
                        null, text, Theme.chat_msgTextPaint, AndroidUtilities.dp(300));
                MessageObject.TextLayoutBlock block = lowered.textLayoutBlocks.get(0);
                assertTrue(block.hasCodeCopyButton);
                assertEquals(original.textHeight() + AndroidUtilities.dp(38), lowered.textHeight());
                assertTrue(lowered.textWidth > original.textWidth);
                assertTrue(block.isCodeCopyHit(1, block.padTop + block.height + block.padBottom, lowered.textWidth));
            } finally {
                SharedConfig.setCodeCopyMinLength(previous);
            }
        });
    }

    @Test
    public void thresholdRebuildInvalidatesBothHeightCaches() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Theme.createCommonMessageResources();
            int previous = SharedConfig.getCodeCopyMinLength();
            try {
                SharedConfig.setCodeCopyMinLength(75);
                TLRPC.TL_message message = new TLRPC.TL_message();
                message.id = 1;
                message.message = "x";
                message.peer_id = new TLRPC.TL_peerUser();
                message.peer_id.user_id = 1;
                message.from_id = new TLRPC.TL_peerUser();
                message.from_id.user_id = UserConfig.getInstance(0).getClientUserId();
                message.out = true;
                TLRPC.TL_messageEntityPre pre = new TLRPC.TL_messageEntityPre();
                pre.offset = 0;
                pre.length = 1;
                pre.language = "";
                message.entities.add(pre);
                MessageObject object = new MessageObject(0, message, false, false);
                object.preview = true;
                object.parentWidth = AndroidUtilities.dp(400);
                assertTrue(object.checkLayout());
                assertFalse(object.textLayoutBlocks.get(0).hasCodeCopyButton);
                int originalTextHeight = object.textHeightCached();
                int originalApproximateHeight = object.getApproximateHeightCached();
                assertEquals(object.textHeight(), originalTextHeight);
                assertEquals(object.getApproximateHeight(), originalApproximateHeight);
                assertFalse(object.checkLayout());

                // Reuse the same object, with both caches populated before each rebuild.
                for (int minimum : new int[]{1, 75}) {
                    SharedConfig.setCodeCopyMinLength(minimum);
                    assertTrue(object.checkLayout());
                    assertEquals(minimum == 1, object.textLayoutBlocks.get(0).hasCodeCopyButton);
                    int extraHeight = minimum == 1 ? AndroidUtilities.dp(38) : 0;
                    assertEquals(originalTextHeight + extraHeight, object.textHeight());
                    // Read the outer cache first to also exercise its dependency on textHeightCached.
                    assertEquals(originalApproximateHeight + extraHeight, object.getApproximateHeightCached());
                    assertEquals(originalTextHeight + extraHeight, object.textHeightCached());
                    assertEquals(object.getApproximateHeight(), object.getApproximateHeightCached());
                    assertFalse(object.checkLayout());
                }
            } finally {
                SharedConfig.setCodeCopyMinLength(previous);
            }
        });
    }

    @Test
    public void effectiveRangeUsesUtf16AndExcludesTrailingNewline() {
        // 73 ASCII units + a surrogate pair = 75 units, 74 code points.
        String code = new String(new char[73]).replace('\0', 'a') + "\uD83D\uDE00";
        assertEquals(75, code.length());
        assertEquals(74, code.codePointCount(0, code.length()));
        for (String language : new String[]{null, "java"}) {
            SpannableString text = new SpannableString(code + "\n");
            text.setSpan(new CodeHighlighting.Span(true, 0, null, language, text.toString()),
                    0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ArrayList<MessageObject.TextRange> ranges = new ArrayList<>();
            MessageObject.cutIntoRanges(text, ranges);
            assertTrue(ranges.get(0).code);
            assertEquals(75, ranges.get(0).end - ranges.get(0).start);
            assertTrue(CodeCopySettings.shouldShow(ranges.get(0).end - ranges.get(0).start, 75, false));
        }
    }
}
