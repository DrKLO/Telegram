/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.time;

import java.util.Calendar;
import java.util.TimeZone;

public class SunDate {

    private static final double DEGRAD = Math.PI / 180.0;
    private static final double RADEG = 180.0 / Math.PI;
    private static final double INV360 = 1.0 / 360.0;

    private static long days_since_2000_Jan_0(int y, int m, int d) {
        return 367L * y - ((7 * (y + ((m + 9) / 12))) / 4) + ((275 * m) / 9) + d - 730530L;
    }

    private static double revolution(double x) {
        return x - 360.0 * Math.floor(x * INV360);
    }

    private static double rev180(double x) {
        return x - 360.0 * Math.floor(x * INV360 + 0.5);
    }

    private static double GMST0(double d) {
        return revolution((180.0 + 356.0470 + 282.9404) + (0.9856002585 + 4.70935E-5) * d);
    }

    private static double sind(double x) {
        return Math.sin(x * DEGRAD);
    }

    private static double cosd(double x) {
        return Math.cos(x * DEGRAD);
    }

    private static double tand(double x) {
        return Math.tan(x * DEGRAD);
    }

    private static double acosd(double x) {
        return RADEG * Math.acos(x);
    }

    private static double atan2d(double y, double x) {
        return RADEG * Math.atan2(y, x);
    }

    private static void sunposAtDay(double p, double[] ot, double[] d) {
        double S, a, V, l, k, i;

        S = revolution(356.0470 + 0.9856002585 * p);
        l = 282.9404 + 4.70935E-5 * p;
        a = 0.016709 - 1.151E-9 * p;

        V = a * RADEG * sind(S) * (1.0 + a * cosd(S)) + S;
        k = cosd(V) - a;

        i = Math.sqrt(1.0 - a * a) * sind(V);
        d[0] = Math.sqrt(k * k + i * i);
        i = atan2d(i, k);
        ot[0] = i + l;
        if (ot[0] >= 360.0) {
            ot[0] -= 360.0;
        }
    }

    private static void sun_RA_decAtDay(double d, double[] RA, double[] dec, double[] r) {
        double[] lon = new double[1];
        double obl_ecl;
        double xs, ys;
        double xe, ye, ze;

        sunposAtDay(d, lon, r);

        xs = r[0] * cosd(lon[0]);
        ys = r[0] * sind(lon[0]);

        obl_ecl = 23.4393 - 3.563E-7 * d;

        xe = xs;
        ye = ys * cosd(obl_ecl);
        ze = ys * sind(obl_ecl);

        RA[0] = atan2d(ye, xe);
        dec[0] = atan2d(ze, Math.sqrt(xe * xe + ye * ye));
    }

    private static int sunRiseSetHelperForYear(int year, int month, int day, double lon, double lat, double altit, int upper_limb, double[] sun) {
        double[] sRA = new double[1];
        double[] sdec = new double[1];
        double[] sr = new double[1];

        double d, sradius, t, tsouth, sidtime;
        int rc = 0;
        d = days_since_2000_Jan_0(year, month, day) + 0.5 - lon / 360.0;
        sidtime = revolution(GMST0(d) + 180.0 + lon);
        sun_RA_decAtDay(d, sRA, sdec, sr);
        tsouth = 12.0 - rev180(sidtime - sRA[0]) / 15.0;
        sradius = 0.2666 / sr[0];
        if (upper_limb != 0) {
            altit -= sradius;
        }

        double cost;
        cost = (sind(altit) - sind(lat) * sind(sdec[0])) / (cosd(lat) * cosd(sdec[0]));
        if (cost >= 1.0) {
            rc = -1;
            t = 0.0;
        } else if (cost <= -1.0) {
            rc = +1;
            t = 12.0;
        } else {
            t = acosd(cost) / 15.0;
        }
        sun[0] = tsouth - t;
        sun[1] = tsouth + t;

        return rc;
    }

    private static int sunRiseSetForYear(int year, int month, int day, double lon, double lat, double[] sun) {
        return sunRiseSetHelperForYear(year, month, day, lon, lat, (-35.0 / 60.0), 1, sun);
    }

    public static int[] calculateSunriseSunset(double lat, double lon) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        double[] sun = new double[2];
        sunRiseSetForYear(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH), lon, lat, sun);
        int timeZoneOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000 / 60;
        int sunrise = (int) (sun[0] * 60) + timeZoneOffset;
        int sunset = (int) (sun[1] * 60) + timeZoneOffset;
        if (sunrise < 0) {
            sunrise += 60 * 24;
        } else if (sunrise > 60 * 24) {
            sunrise -= 60 * 24;
        }
        if (sunset < 0) {
            sunset += 60 * 24;
        } else if (sunset > 60 * 24) {
            sunset += 60 * 24;
        }
        return new int[] {sunrise, sunset};
    }
}
