package org.telegram.messenger.utils.tlutils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.tl.TL_stars;

import java.math.BigDecimal;
import java.math.MathContext;

public class AmountUtils {
    public enum Currency { STARS, TON }

    public static class Amount {
        public final Currency currency;
        private final long nanos;

        private Amount(@NonNull Currency currency, long nanos) {
            this.currency = currency;
            this.nanos = nanos;
        }

        public long asDecimal() {
            return nanos / getDecimals(currency);
        }

        public long asNano() {
            return nanos;
        }

        public double asDouble() {
            return (double) nanos / getDecimals(currency);
        }

        public String asDecimalString() {
            return new BigDecimal(asNano())
                .divide(BigDecimal.valueOf(getDecimals(currency)), MathContext.UNLIMITED)
                .stripTrailingZeros()
                .toPlainString();
        }

        public boolean isZero() {
            return nanos == 0;
        }

        public boolean isRound() {
            return nanos % getDecimals(currency) == 0;
        }

        public String formatAsDecimalSpaced() {
            if (isRound()) {
                switch (currency) {
                    case STARS:
                        return LocaleController.formatPluralStringSpaced("StarsCount", (int) asDecimal());
                    case TON:
                        return LocaleController.formatPluralStringSpaced("TonCount", (int) asDecimal());
                }
            } else {
                switch (currency) {
                    case STARS:
                        return LocaleController.formatString(R.string.StarsCountX, asDecimalString());
                    case TON:
                        return LocaleController.formatString(R.string.TonCountX, asDecimalString());
                }
            }

            return "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;

            if (o instanceof Amount) {
                return equals(this, (Amount) o);
            }
            return false;
        }

        public TL_stars.StarsAmount toTl() {
            if (currency == AmountUtils.Currency.STARS) {
                final TL_stars.StarsAmount amount = new TL_stars.TL_starsAmount();
                final long decimals = getDecimals(currency);
                amount.amount = nanos / decimals;
                amount.nanos = (int) (nanos % decimals);
                return amount;
            }

            if (currency == AmountUtils.Currency.TON) {
                final TL_stars.StarsAmount amount = new TL_stars.TL_starsTonAmount();
                amount.amount = nanos;
                return amount;
            }

            return null;
        }

        public static Amount fromNano(long nanos, Currency currency) {
            if (currency == null) {
                return null;
            }

            return new Amount(currency, nanos);
        }

        public static Amount fromDecimal(long decimal, Currency currency) {
            if (currency == null) {
                return null;
            }

            return new Amount(currency, decimal * getDecimals(currency));
        }

        @Nullable
        public static Amount fromDecimal(String string, Currency currency) {
            try {
                final BigDecimal nanosBig = new BigDecimal(string)
                        .multiply(BigDecimal.valueOf(getDecimals(currency)));
                if (nanosBig.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                    return null;
                }

                final long nanos = nanosBig.longValue();
                return fromNano(nanos, currency);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public static Amount of(TL_stars.StarsAmount amount) {
            if (amount instanceof TL_stars.TL_starsAmount) {
                return fromNano(amount.amount * getDecimals(Currency.STARS) + amount.nanos, Currency.STARS);
            } else if (amount instanceof TL_stars.TL_starsTonAmount) {
                return fromNano(amount.amount, Currency.TON);
            }

            return null;
        }

        @NonNull
        public static Amount ofSafe(TL_stars.StarsAmount amount) {
            Amount a = of(amount);
            return a != null ? a : Amount.fromNano(0, Currency.STARS);
        }

        public static boolean equals(TL_stars.StarsAmount a, TL_stars.StarsAmount b) {
            return equals(Amount.of(a), Amount.of(b));
        }

        public static boolean equals(Amount a, Amount b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null) {
                return false;
            }

            return a.currency == b.currency && a.nanos == b.nanos;
        }

        private static long getDecimals(Currency ignoredCurrency) {
            return 1_000_000_000;
        }
    }
}
