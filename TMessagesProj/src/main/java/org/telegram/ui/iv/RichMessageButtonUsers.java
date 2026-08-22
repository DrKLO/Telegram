package org.telegram.ui.iv;

import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Collects the access hashes required by user-profile buttons in an input rich message. */
public final class RichMessageButtonUsers {

    private RichMessageButtonUsers() {}

    public static ArrayList<TLRPC.InputUser> collect(int currentAccount, ArrayList<TL_iv.PageBlock> blocks) {
        final LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        if (blocks != null) {
            for (TL_iv.PageBlock block : blocks) {
                collectBlock(block, userIds);
            }
        }

        final ArrayList<TLRPC.InputUser> result = new ArrayList<>(userIds.size());
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        for (long userId : userIds) {
            final TLRPC.User user = controller.getUser(userId);
            if (user == null) continue;
            final TLRPC.InputUser inputUser = controller.getInputUser(user);
            if (!(inputUser instanceof TLRPC.TL_inputUserEmpty)) {
                result.add(inputUser);
            }
        }
        return result;
    }

    private static void collectBlock(TL_iv.PageBlock block, LinkedHashSet<Long> userIds) {
        if (block == null) return;
        collectText(block.text, userIds);
        collectCaption(block.caption, userIds);

        if (block instanceof TL_iv.pageBlockButtonRow) {
            final TL_iv.pageBlockButtonRow row = (TL_iv.pageBlockButtonRow) block;
            if (row.buttons != null) {
                for (TL_keyboard.PageButton button : row.buttons) {
                    if (button == null) continue;
                    collectType(button.type, userIds);
                    collectText(button.text, userIds);
                }
            }
        } else if (block instanceof TL_iv.pageBlockBlockquote) {
            collectText(((TL_iv.pageBlockBlockquote) block).caption, userIds);
        } else if (block instanceof TL_iv.pageBlockPullquote) {
            collectText(((TL_iv.pageBlockPullquote) block).caption, userIds);
        } else if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
            final TL_iv.pageBlockBlockquoteBlocks quote = (TL_iv.pageBlockBlockquoteBlocks) block;
            collectText(quote.caption, userIds);
            collectBlocks(quote.blocks, userIds);
        } else if (block instanceof TL_iv.pageBlockDetails) {
            final TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) block;
            collectText(details.title, userIds);
            collectBlocks(details.blocks, userIds);
        } else if (block instanceof TL_iv.pageBlockList) {
            final TL_iv.pageBlockList list = (TL_iv.pageBlockList) block;
            if (list.items != null) {
                for (TL_iv.PageListItem item : list.items) {
                    if (item instanceof TL_iv.TL_pageListItemText) {
                        collectText(((TL_iv.TL_pageListItemText) item).text, userIds);
                    } else if (item instanceof TL_iv.TL_pageListItemBlocks) {
                        collectBlocks(((TL_iv.TL_pageListItemBlocks) item).blocks, userIds);
                    }
                }
            }
        } else if (block instanceof TL_iv.pageBlockOrderedList) {
            final TL_iv.pageBlockOrderedList list = (TL_iv.pageBlockOrderedList) block;
            if (list.items != null) {
                for (TL_iv.PageListOrderedItem item : list.items) {
                    if (item instanceof TL_iv.TL_pageListOrderedItemText) {
                        collectText(((TL_iv.TL_pageListOrderedItemText) item).text, userIds);
                    } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
                        collectBlocks(((TL_iv.TL_pageListOrderedItemBlocks) item).blocks, userIds);
                    }
                }
            }
        } else if (block instanceof TL_iv.pageBlockTable) {
            final TL_iv.pageBlockTable table = (TL_iv.pageBlockTable) block;
            collectText(table.title, userIds);
            if (table.rows != null) {
                for (TL_iv.pageTableRow row : table.rows) {
                    if (row == null || row.cells == null) continue;
                    for (TL_iv.pageTableCell cell : row.cells) {
                        if (cell != null) collectText(cell.text, userIds);
                    }
                }
            }
        } else if (block instanceof TL_iv.pageBlockCollage) {
            collectBlocks(((TL_iv.pageBlockCollage) block).items, userIds);
        } else if (block instanceof TL_iv.pageBlockSlideshow) {
            collectBlocks(((TL_iv.pageBlockSlideshow) block).items, userIds);
        } else if (block instanceof TL_iv.pageBlockEmbedPost) {
            collectBlocks(((TL_iv.pageBlockEmbedPost) block).blocks, userIds);
        } else if (block instanceof TL_iv.pageBlockCover) {
            collectBlock(((TL_iv.pageBlockCover) block).cover, userIds);
        } else if (block instanceof TL_iv.pageBlockRelatedArticles) {
            collectText(((TL_iv.pageBlockRelatedArticles) block).title, userIds);
        }
    }

    private static void collectBlocks(ArrayList<TL_iv.PageBlock> blocks, LinkedHashSet<Long> userIds) {
        if (blocks == null) return;
        for (TL_iv.PageBlock block : blocks) {
            collectBlock(block, userIds);
        }
    }

    private static void collectCaption(TL_iv.PageCaption caption, LinkedHashSet<Long> userIds) {
        if (caption == null) return;
        collectText(caption.text, userIds);
        collectText(caption.credit, userIds);
    }

    private static void collectText(TL_iv.RichText text, LinkedHashSet<Long> userIds) {
        if (text == null) return;
        if (text instanceof TL_iv.textButton) {
            collectType(((TL_iv.textButton) text).type, userIds);
        } else if (text instanceof TL_iv.textDiff) {
            collectText(((TL_iv.textDiff) text).old_text, userIds);
        }
        collectText(text.text, userIds);
        if (text.texts != null) {
            for (TL_iv.RichText child : text.texts) {
                collectText(child, userIds);
            }
        }
    }

    private static void collectType(TL_keyboard.InlineButtonType type, LinkedHashSet<Long> userIds) {
        if (type instanceof TL_keyboard.TL_inlineButtonTypeUserProfile) {
            final long userId = ((TL_keyboard.TL_inlineButtonTypeUserProfile) type).user_id;
            if (userId != 0) userIds.add(userId);
        }
    }
}
