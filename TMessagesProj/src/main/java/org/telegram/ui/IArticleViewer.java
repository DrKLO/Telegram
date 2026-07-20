package org.telegram.ui;

import android.view.MotionEvent;
import android.view.View;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.video.VideoPlayerHolderBase;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.TextPaintUrlSpan;

import java.util.ArrayList;

public abstract class IArticleViewer {

    public abstract int getCurrentAccount();

    public int selectedFont = 0;
    public abstract int getTextColor();
    public abstract int getLinkTextColor();
    public abstract int getGrayTextColor();
    public abstract int getThemedColor(int colorKey);
    public abstract Theme.ResourcesProvider getResourcesProvider();
    public abstract ArticleViewer.Resources getResources();

    public int padx() { return 18 /* dp */; }
    public int pady() { return 8 /* dp */; }

    public abstract TextSelectionHelper.ArticleTextSelectionHelper getTextSelectionHelper(View view);

    public boolean allowTouches() {
        return true;
    }
    public boolean canStartSelection(View block) {
        return false;
    }
    public LinkSpanDrawable<TextPaintUrlSpan> pressedLink;
    public LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector();
    public ArticleViewer.DrawingText pressedLinkOwnerLayout;
    public int pressedLayoutY;
    public View pressedLinkOwnerView;
    public void openWebpageUrl(String url, String anchor, Browser.Progress progress) {}
    public void checkLayoutForLinks(MotionEvent event, View parentView) {}
    public boolean scrollToAnchor(String anchor, boolean animated) {
        return false;
    }
    public boolean drawBlockSelection;
    public void handleLinkClick(ArticleViewer.WebpageAdapter adapter, TextPaintUrlSpan span) {}

    public TLRPC.Chat loadedChannel;
    public boolean loadingChannel;
    public View loadingLinkView;
    public TextPaintUrlSpan loadingLink;
    public ArticleViewer.DrawingText loadingText;
    public LoadingDrawable loadingLinkDrawable;

    public VideoPlayerHolderBase videoPlayer;
    public ArticleViewer.BlockVideoCell currentPlayer;
    public LongSparseArray<ArticleViewer.BlockVideoCellState> videoStates = new LongSparseArray<>();

    public void reset() {
        loadedChannel = null;
        loadingChannel = false;
    }

    public ArrayList<ArticleViewer.SearchResult> searchResults = new ArrayList<>();
    public String searchText;
    public int currentSearchIndex;

    public ArticleViewer.WebpageAdapter getAdapter() {
        return null;
    }

    public boolean openPhoto(TL_iv.PageBlock block, ArticleViewer.WebpageAdapter adapter) {
        return false;
    }

    public ActionBarPopupWindow popupWindow;
    public BottomSheet linkSheet;

}
