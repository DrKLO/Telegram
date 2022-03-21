/**
 * Copyright 2022  Nikita S. <nikita@saraeff.net>
 * <p>
 * This file is part of Telegraher.
 * <p>
 * Telegraher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Telegraher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with Telegraher.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.evildayz.code.telegraher;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class ThePenisMightierThanTheSword {

    public static ArrayList<TLRPC.TL_dcOption> getTheDC(int dcId, ArrayList<TLRPC.TL_dcOption> options) {
        ArrayList<TLRPC.TL_dcOption> filtered = new ArrayList<>();
        for (TLRPC.TL_dcOption t : options) {
            if (t.id == dcId) filtered.add(t);
        }
        if (filtered.isEmpty()) return null;
        return filtered;
    }

    public static String getDCGeoDummy(int dcId) {
        switch (dcId) {
            case 1:
            case 3:
                return "USA, Miami";
            case 2:
            case 4:
                return "NLD, Amsterdam";
            case 5:
                return "SGP, Singapore";
            default:
                return "UNK, unknown";
        }
    }
}
