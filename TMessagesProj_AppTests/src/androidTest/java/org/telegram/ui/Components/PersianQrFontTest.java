package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.style.ReplacementSpan;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.QrShare.QrShareView;

import java.lang.reflect.Method;
import java.util.concurrent.FutureTask;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PersianQrFontTest {
    @Test
    public void mixedCaptionRefreshesAtTheSameWidthAfterFontToggles() throws Exception {
        onUiThread(() -> {
            boolean previous = SharedConfig.useVazirmatnFont;
            TestQrView view = new TestQrView("\u0633\u0644\u0627\u0645 English", false);
            try {
                SharedConfig.useVazirmatnFont = true;
                StaticLayout enabled = caption(view);
                assertTrue(fontCount(enabled) > 0);
                assertSame(enabled, caption(view));
                Bitmap bitmap = Bitmap.createBitmap(enabled.getWidth(), enabled.getHeight(), Bitmap.Config.ARGB_8888);
                enabled.draw(new Canvas(bitmap));
                bitmap.recycle();

                SharedConfig.useVazirmatnFont = false;
                StaticLayout disabled = caption(view);
                assertNotSame(enabled, disabled);
                assertEquals(0, fontCount(disabled));
                assertEquals(enabled.getText().toString(), disabled.getText().toString());

                SharedConfig.useVazirmatnFont = true;
                assertTrue(fontCount(caption(view)) > 0);
            } finally {
                SharedConfig.useVazirmatnFont = previous;
                view.dispose();
            }
        });
    }

    @Test
    public void latinUsernameRetainsIconAndLineWidths() throws Exception {
        onUiThread(() -> {
            boolean previous = SharedConfig.useVazirmatnFont;
            TestQrView view = new TestQrView("example_123", true);
            try {
                SharedConfig.useVazirmatnFont = false;
                StaticLayout disabled = caption(view);
                SharedConfig.useVazirmatnFont = true;
                StaticLayout enabled = caption(view);
                assertEquals(0, fontCount(enabled));
                assertEquals(disabled.getLineCount(), enabled.getLineCount());
                for (int i = 0; i < enabled.getLineCount(); i++) {
                    assertEquals(disabled.getLineWidth(i), enabled.getLineWidth(i), 0.01f);
                }
                Spanned text = (Spanned) enabled.getText();
                assertEquals(1, text.getSpans(0, text.length(), ReplacementSpan.class).length);
            } finally {
                SharedConfig.useVazirmatnFont = previous;
                view.dispose();
            }
        });
    }

    private static final class TestQrView extends QrShareView {
        TestQrView(String caption, boolean username) {
            super(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    UserConfig.selectedAccount, null, caption, username, null);
        }

        void dispose() {
            super.onDetachedFromWindow();
        }
    }

    private static StaticLayout caption(QrShareView view) {
        try {
            Method method = QrShareView.class.getDeclaredMethod("nameLayout", int.class);
            method.setAccessible(true);
            return (StaticLayout) method.invoke(view, AndroidUtilities.dp(280));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static int fontCount(StaticLayout layout) {
        if (!(layout.getText() instanceof Spanned)) return 0;
        Spanned text = (Spanned) layout.getText();
        return text.getSpans(0, text.length(), PersianFontSpan.class).length;
    }

    private static void onUiThread(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
        task.get();
    }
}
