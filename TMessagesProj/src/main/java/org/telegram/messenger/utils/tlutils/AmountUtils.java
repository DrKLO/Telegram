package org.telegram.messenger.utils.tlutils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
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

        // todo rename: asPlainString
        public String asDecimalString() {
            return new BigDecimal(asNano())
                .divide(BigDecimal.valueOf(getDecimals(currency)), MathContext.UNLIMITED)
                .stripTrailingZeros()
                .toPlainString();
        }

        public String asFormatString() {
            return asFormatString(',');
        }

        public String asFormatString(char thousandsSeparator) {
            StringBuilder sb = new StringBuilder(LocaleController.formatNumber(asDecimal(), thousandsSeparator));

            final long part = nanos % getDecimals(currency);
            if (part == 0) {
                return sb.toString();
            }

            sb.append('.');
            String part2 = Long.toString(part);

            final int zerosCount = getTenPow(currency) - part2.length();
            for (int a = 0; a < zerosCount; a++) {
                sb.append('0');
            }

            int end = part2.length();
            while (end > 0 && part2.charAt(end - 1) == '0') {
                end--;
            }

            sb.append(part2, 0, end);
            return sb.toString();
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

        public Amount applyPerMille(int perMille) {
            return fromNano(this.nanos * perMille / 1000, this.currency);
        }

        public Amount round(int decimals) {
            final long nano = asNano();

            final long d = getTenPow(currency) - decimals;
            if (d <= 0) {
                return this;
            }

            long r = 1;
            for (int a = 0; a < d; a++) {
                r *= 10;
            }

            return fromNano((nano / r) * r, currency);
        }

        public static Amount fromUsd(double usd, AmountUtils.Currency currency) {
            if (currency == AmountUtils.Currency.TON) {
                return AmountUtils.Amount.fromDecimal(usd / MessagesController.getInstance(UserConfig.selectedAccount).config.tonUsdRate.get(), AmountUtils.Currency.TON).round(2);
            } else if (currency == AmountUtils.Currency.STARS) {
                return AmountUtils.Amount.fromDecimal(usd * 100000 / MessagesController.getInstance(UserConfig.selectedAccount).starsUsdSellRate1000, AmountUtils.Currency.STARS).round(0);
            }

            return AmountUtils.Amount.fromDecimal(0, currency);
        }

        public double convertToUsd() {
            if (this.currency == Currency.STARS) {
                return this.asDouble() / 1000 * MessagesController.getInstance(UserConfig.selectedAccount).starsUsdSellRate1000 / 100;
            } else if (this.currency == Currency.TON) {
                return this.asDouble() * MessagesController.getInstance(UserConfig.selectedAccount).config.tonUsdRate.get();
            }
            return 0;
        }

        public Amount convertTo(Currency currency) {
            if (this.currency == currency) {
                return this;
            }

            return fromUsd(convertToUsd(), currency);
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

        public static Amount fromDecimal(double decimal, Currency currency) {
            if (currency == null) {
                return null;
            }

            return new Amount(currency, (long) (decimal * getDecimals(currency)));
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


        private static int getTenPow(Currency ignoredCurrency) {
            return 9;
        }

        private static long getDecimals(Currency ignoredCurrency) {
            return 1_000_000_000;
        }
    }



    private static class AmountLimit {
        private final Amount min;
        private final Amount max;

        public AmountLimit(Amount min, Amount max) {
            this.min = min;
            this.max = max;
        }
    }

    public static class AmountLimits {
        public static final int OK = 0;
        public static final int TOO_SMALL = -1;
        public static final int TOO_BIG = 1;

        private final AmountLimit[] limits = new AmountLimit[Currency.values().length];

        public void set(Amount min, Amount max) {
            if (min.currency != max.currency) {
                if (BuildConfig.DEBUG_PRIVATE_VERSION) {
                    throw new IllegalArgumentException();
                }
                return;
            }

            limits[min.currency.ordinal()] = new AmountLimit(min, max);
        }

        public void setAsNano(Currency currency, long nanoMin, long nanoMax) {
            set(Amount.fromNano(nanoMin, currency), Amount.fromNano(nanoMax, currency));
        }

        public void setAsDecimal(Currency currency, long decimalMin, long decimalMax) {
            set(Amount.fromDecimal(decimalMin, currency), Amount.fromDecimal(decimalMax, currency));
        }

        public Amount getMin(Currency currency) {
            return limits[currency.ordinal()].min;
        }

        public Amount getMax(Currency currency) {
            return limits[currency.ordinal()].max;
        }

        public int check(Amount amount) {
            final AmountLimit limit = limits[amount.currency.ordinal()];
            if (limit == null) {
                return OK;
            }

            final long nanos = amount.asNano();
            if (nanos > limit.max.asNano()) {
                return TOO_BIG;
            }
            if (nanos < limit.min.asNano()) {
                return TOO_SMALL;
            }
            return OK;
        }
    }
}
