package org.telegram.ui.QrShare;

/**
 * The collectible gift backdrops of the palette: name, centre, edge, pattern, light accent (text). The server palette
 * ({@link #apply}) replaces the built-in table, which is kept for a palette that has never been loaded.
 */
final class Backdrops {
    static String[] NAMES = {
        "Rosewood", "Carmine", "Fire Engine", "Seal Brown", "Chestnut", "Coral Red", "Strawberry", "Chocolate", "Mystic Pearl", "Burnt Sienna", "Tomato", "Cappuccino", "Copper", "Carrot Juice", "Persimmon", "Desert Sand", "Orange", "Caramel", "Old Gold", "Mustard", "Satin Gold", "Amber", "Pure Gold", "Light Olive", "Khaki Green", "Lemongrass", "Malachite", "Camo Green", "Pistachio", "Shamrock Green", "Dark Green", "Ranger Green", "Hunter Green", "Mint Green", "Emerald", "Pine Green", "Gunship Green", "Pacific Green", "Tactical Pine", "Jade Green", "Aquamarine", "Pacific Cyan", "Deep Cyan", "Turquoise", "Moonstone", "Sky Blue", "Azure Blue", "Gunmetal", "Celtic Blue", "Sapphire", "Silver Blue", "Indigo Dye", "French Blue", "Navy Blue", "Cobalt Blue", "Marine Blue", "Midnight Blue", "Neon Blue", "Cyberpunk", "Electric Indigo", "Lavender", "Grape", "French Violet", "English Violet", "Purple", "Electric Purple", "Dark Lilac", "Fandango", "Mexican Pink", "Burgundy", "Raspberry", "Black", "Onyx Black", "Rifle Green", "Battleship Grey", "Feldgrau", "Steel Grey", "Roman Silver", "Platinum", "Ivory White"
    };
    static int[] CENTER = {
        0xFFB77A77, 0xFFE0574A, 0xFFF05F4F, 0xFF664D45, 0xFFBE6F54, 0xFFDA896B, 0xFFDD8E6F, 0xFFA46E58, 0xFFD08B6D, 0xFFD66F3C, 0xFFE6793E, 0xFFB1907E, 0xFFD08656, 0xFFDB9867, 0xFFE7A75A, 0xFFB39F82, 0xFFD19A3A, 0xFFD09932, 0xFFB58D38, 0xFFD4980D, 0xFFBF9B47, 0xFFDAB345, 0xFFCCAB41, 0xFFC2AF64, 0xFFADB070, 0xFFAEB85A, 0xFF95B457, 0xFF75944D, 0xFF97B07C, 0xFF8AB163, 0xFF516341, 0xFF5F7849, 0xFF8FAE78, 0xFF7ECB82, 0xFF78C585, 0xFF6BA97C, 0xFF558A65, 0xFF6FC793, 0xFF44826B, 0xFF55C49C, 0xFF60B195, 0xFF5ABEA6, 0xFF31B5AA, 0xFF5EC0B8, 0xFF7EB1B4, 0xFF58B4C8, 0xFF5DB1CB, 0xFF4C5D63, 0xFF45B8ED, 0xFF58A3C8, 0xFF80A4B8, 0xFF537991, 0xFF5C9BC4, 0xFF6C9EDD, 0xFF6088CF, 0xFF4E689C, 0xFF5C6985, 0xFF7596F9, 0xFF858FF3, 0xFFA980F3, 0xFFB789E4, 0xFF9D74C1, 0xFFC260E6, 0xFFB186BB, 0xFFAE6CAE, 0xFFCA70C6, 0xFFB17DA5, 0xFFE28AB6, 0xFFE36692, 0xFFA35E66, 0xFFE07B85, 0xFF363738, 0xFF464A4D, 0xFF64695C, 0xFF8C8C85, 0xFF899288, 0xFF97A2AC, 0xFFA3A8B5, 0xFFB2AEA7, 0xFFBAB6B1
    };
    static int[] EDGE = {
        0xFF814C52, 0xFFA8383B, 0xFFC43949, 0xFF47362E, 0xFF994838, 0xFFC4654F, 0xFFB75A60, 0xFF74443B, 0xFFB05770, 0xFFB54B2D, 0xFFD44E3F, 0xFF7C6356, 0xFF9D6531, 0xFFC76F4F, 0xFFC5675F, 0xFF7E735B, 0xFFC06F47, 0xFFB77431, 0xFF946925, 0xFFC47712, 0xFF8D7739, 0xFFB1802A, 0xFF987B32, 0xFF887E45, 0xFF6B7D54, 0xFF559345, 0xFF3D9755, 0xFF547341, 0xFF5C814C, 0xFF559345, 0xFF2B452F, 0xFF3C4F3B, 0xFF4B825B, 0xFF459E5A, 0xFF42A171, 0xFF3E7970, 0xFF3D6657, 0xFF3B9C84, 0xFF2F6369, 0xFF3B9977, 0xFF46ABB4, 0xFF3D95BA, 0xFF189599, 0xFF3D928E, 0xFF588390, 0xFF538BC2, 0xFF448BAB, 0xFF2F3B42, 0xFF3886D9, 0xFF5379C2, 0xFF607C91, 0xFF416479, 0xFF37739A, 0xFF5C6EC9, 0xFF5162B8, 0xFF3B4B7A, 0xFF354057, 0xFF6862E4, 0xFF865FD3, 0xFF5B62D8, 0xFF8A5ABC, 0xFF794DA0, 0xFF914ED9, 0xFF875A91, 0xFF844784, 0xFF9662D4, 0xFF8C577A, 0xFFA4588B, 0xFFC9497C, 0xFF6D414A, 0xFFB65980, 0xFF0E0F0F, 0xFF282C2E, 0xFF4B5241, 0xFF6C6C66, 0xFF5E6B63, 0xFF63727C, 0xFF7C808A, 0xFF88847E, 0xFFA19D97
    };
    static int[] PATTERN = {
        0xFF551C22, 0xFF4F0100, 0xFF690009, 0xFF0A0605, 0xFF601508, 0xFF891200, 0xFFA90C0C, 0xFF3E0A02, 0xFF9B0526, 0xFF6B0902, 0xFF800B00, 0xFFEBD4C8, 0xFF602901, 0xFF8E2100, 0xFFAD0E00, 0xFF504429, 0xFF9D3907, 0xFF7D3600, 0xFF4F3302, 0xFF7A2500, 0xFF5D3B00, 0xFF7A3100, 0xFF703C00, 0xFF594C04, 0xFF39501B, 0xFF466A07, 0xFF046B06, 0xFF163701, 0xFF28471B, 0xFF126B00, 0xFF000501, 0xFF102209, 0xFF1C491F, 0xFF026B22, 0xFF006532, 0xFF0B4833, 0xFF07261D, 0xFF006149, 0xFF002624, 0xFF044931, 0xFF035F67, 0xFF02648D, 0xFF004F4F, 0xFF11534C, 0xFF164552, 0xFF07609B, 0xFF025074, 0xFF04080A, 0xFF003E85, 0xFF0D45B6, 0xFF15374B, 0xFF031B29, 0xFF073B5C, 0xFF1239A2, 0xFF13247C, 0xFF010821, 0xFF030A18, 0xFF2828BC, 0xFF4318A6, 0xFF3722AB, 0xFF5B10AB, 0xFF3E0A6B, 0xFF4A018A, 0xFF54225F, 0xFF470C47, 0xFF620FB4, 0xFF652852, 0xFF8E054E, 0xFF750230, 0xFF340307, 0xFF890638, 0xFF6C6868, 0xFF000000, 0xFF0F120B, 0xFF2B2A20, 0xFF1C261F, 0xFF334552, 0xFF3F4550, 0xFF3D382D, 0xFF665F52
    };
    static int[] TEXT = {
        0xFFEDCACD, 0xFFFFB7AD, 0xFFFFB7A6, 0xFFD4BCB5, 0xFFFEC6B9, 0xFFFFD9D2, 0xFFFFD3D3, 0xFFE4B6AC, 0xFFFEDDE0, 0xFFFFCCB0, 0xFFFFCCBD, 0xFF4A3226, 0xFFF4D8BE, 0xFFFFD7CA, 0xFFFFE4D7, 0xFFF2E5CD, 0xFFFFE1C3, 0xFFFFD9B3, 0xFFFFDD8C, 0xFFFFDE9C, 0xFFFEE4A9, 0xFFFFEDC7, 0xFFFFE5AB, 0xFFF5EBBC, 0xFFD3E6BB, 0xFFD8F2C2, 0xFFC2EFBE, 0xFFCFE8B9, 0xFFD9F2C9, 0xFFD5FBC8, 0xFFBFD1A9, 0xFFB7C4B5, 0xFFD8F5DE, 0xFFBDFFCC, 0xFFB9F9D9, 0xFFD8F5E5, 0xFFB5E0D4, 0xFFC6FFF0, 0xFFB7E6D3, 0xFFBEFEE7, 0xFFC7FDFE, 0xFFB6EFFF, 0xFFD1FFFD, 0xFFBDF8F2, 0xFFDAF5FD, 0xFFCDE8FD, 0xFFB5ECFF, 0xFFB6C5CC, 0xFFC2E7FF, 0xFFC1DEFF, 0xFFC9E4F4, 0xFFC2DCEE, 0xFFC1E3F9, 0xFFD3E1FF, 0xFFC2D3F5, 0xFFBBCBF2, 0xFFBFCCE0, 0xFFCFDDFF, 0xFFE0D9FF, 0xFFD8D8FF, 0xFFE8D1FF, 0xFFE0BDFE, 0xFFEBC7FF, 0xFFE6C7ED, 0xFFF3CBF3, 0xFFEBCEFF, 0xFFF0C4E2, 0xFFFFC7EB, 0xFFFFD6E6, 0xFFE7BCC0, 0xFFFFD6E6, 0xFF8C8F91, 0xFF9EA1A2, 0xFFC3C7BD, 0xFFCFCEC4, 0xFFDEE9E1, 0xFFDFE4E8, 0xFFDADFE2, 0xFFE9E7E2, 0xFFF5F4F2
    };
    /**
     * Swatch order: a monochrome first row (Black, Onyx Black, Gunmetal, Midnight Blue, Steel Grey, Roman Silver),
     * then saturated colours by hue (red → pink), then muted ones by hue, then the dark greens, browns and greys
     * (computed from the centre colours: OKLCH hue; chroma ≥ 0.11 = saturated; L < 0.55, browns or chroma < 0.03 = last).
     */
    static final String[] SKETCH_ORDER = {
        "Black", "Onyx Black", "Gunmetal", "Midnight Blue", "Steel Grey", "Roman Silver", "Raspberry", "Carmine", "Fire Engine", "Burnt Sienna",
        "Tomato", "Copper", "Persimmon", "Orange", "Caramel", "Mustard", "Old Gold", "Satin Gold", "Amber", "Pure Gold",
        "Lemongrass", "Malachite", "Shamrock Green", "Mint Green", "Emerald", "Pacific Green", "Jade Green", "Deep Cyan", "Celtic Blue", "Cobalt Blue",
        "Neon Blue", "Cyberpunk", "Electric Indigo", "Lavender", "Grape", "French Violet", "Purple", "Electric Purple", "Fandango", "Mexican Pink",
        "Burgundy", "Rosewood", "Chestnut", "Coral Red", "Strawberry", "Mystic Pearl", "Carrot Juice", "Light Olive", "Khaki Green", "Camo Green",
        "Pistachio", "Hunter Green", "Pine Green", "Gunship Green", "Tactical Pine", "Aquamarine", "Pacific Cyan", "Turquoise", "Moonstone", "Sky Blue",
        "Azure Blue", "Sapphire", "Silver Blue", "Indigo Dye", "French Blue", "Navy Blue", "English Violet", "Dark Lilac", "Seal Brown", "Chocolate",
        "Cappuccino", "Desert Sand", "Dark Green", "Ranger Green", "Marine Blue", "Ivory White", "Platinum", "Battleship Grey", "Rifle Green", "Feldgrau"
    };
    static int[] ORDER;

    /** Server ids of the backdrops, null while the built-in table is in use. */
    static int[] IDS;

    /** The default backdrop: shown without a theme, saved as the default theme. */
    static final String DEFAULT_NAME = "Black";
    /** The backdrop drawn with the screen's own tuned colours instead of the table's. */
    static final String TUNED_NAME = "Neon Blue";

    /** Index of the backdrop with the name, -1 if there is none. */
    static int indexOfName(String name) {
        for (int i = 0; i < NAMES.length; ++i) {
            if (name.equals(NAMES[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Uses the server palette (its colours and ids), shown in the server's order. A palette without the default backdrop
     * is not taken, the screen needs it as its default.
     */
    static boolean apply(java.util.List<org.telegram.tgnet.TLRPC.TL_shareThemeBackdropOption> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        int defaultIndex = -1;
        for (int i = 0; i < list.size(); ++i) {
            if (DEFAULT_NAME.equals(list.get(i).name)) {
                defaultIndex = i;
            }
        }
        if (defaultIndex < 0) {
            return false;
        }
        final int n = list.size();
        NAMES = new String[n];
        CENTER = new int[n];
        EDGE = new int[n];
        PATTERN = new int[n];
        TEXT = new int[n];
        IDS = new int[n];
        for (int i = 0; i < n; ++i) {
            final org.telegram.tgnet.TLRPC.TL_shareThemeBackdropOption o = list.get(i);
            NAMES[i] = o.name;
            CENTER[i] = o.center_color | 0xFF000000;
            EDGE[i] = o.edge_color | 0xFF000000;
            PATTERN[i] = o.pattern_color | 0xFF000000;
            TEXT[i] = o.text_color | 0xFF000000;
            IDS[i] = o.backdrop_id;
        }
        ORDER = null; // the swatch order is rebuilt for these names (see orderToIndex)
        QrShareView.onBackdropsChanged(defaultIndex, indexOfName(TUNED_NAME));
        return true;
    }

    /** Index of the backdrop with the server id, -1 if there is none. */
    static int indexOfId(int id) {
        if (IDS == null) {
            return -1;
        }
        for (int i = 0; i < IDS.length; ++i) {
            if (IDS[i] == id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The backdrop of swatch k: the server palette in the server's own order (it is fixed there, no sorting); the
     * built-in table in SKETCH_ORDER (its names first, in that order, then the others).
     */
    static int orderToIndex(int k) {
        if (IDS != null) {
            return k;
        }
        if (ORDER == null) {
            final int n = NAMES.length;
            final int[] order = new int[n];
            final boolean[] placed = new boolean[n];
            int count = 0;
            for (String name : SKETCH_ORDER) {
                final int j = indexOfName(name);
                if (j >= 0 && !placed[j]) {
                    placed[j] = true;
                    order[count++] = j;
                }
            }
            for (int j = 0; j < n; j++) {
                if (!placed[j]) order[count++] = j;
            }
            ORDER = order;
        }
        return ORDER[k];
    }
}
