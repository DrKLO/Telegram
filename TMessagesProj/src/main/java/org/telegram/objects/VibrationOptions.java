/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.objects;

import org.telegram.messenger.R;

public class VibrationOptions {

    public static int DEFAULT_VIBRATION_COUNT = 2;

    public static enum VibrationSpeed {
        DEFAULT("VibrateSpeedDefault", R.string.VibrateSpeedDefault),
        FAST   ("VibrateSpeedFast",    R.string.VibrateSpeedFast),
        MEDIUM ("VibrateSpeedMedium",  R.string.VibrateSpeedMedium),
        SLOW   ("VibrateSpeedSlow",    R.string.VibrateSpeedSlow);

        private int value;
        private String localeKey;
        private int resourceId;

        VibrationSpeed(String localeKey, int resourceId) {
            this.value = this.ordinal();
            this.localeKey = localeKey;
            this.resourceId = resourceId;
        }

        public int getValue() {
            return value;
        }

        public String getLocaleKey() {
            return localeKey;
        }

        public int getResourceId() {
            return resourceId;
        }

        public static VibrationSpeed getDefault() {
            return DEFAULT;
        }

        public static VibrationSpeed fromValue(int value) {
            for (VibrationSpeed val : VibrationSpeed.values()) {
                if(val.value == value)
                    return val;
            }
            return getDefault();
        }
    }
}
