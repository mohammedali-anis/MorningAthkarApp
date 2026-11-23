import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/** Approximate offline times for Siegen using the same method (4) as the online request. */
final class LocalPrayerTimes {
    private static final double LATITUDE = 50.86666667;
    private static final double LONGITUDE = 8.033333333;
    private static final double FAJR_ANGLE = 18.5;
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    static Map<String, LocalTime> calculate(LocalDate date) {
        SolarPosition sun = solarPosition(date);
        SolarPosition tomorrow = solarPosition(date.plusDays(1));
        double offset = date.atTime(12, 0).atZone(ZONE).getOffset().getTotalSeconds() / 60.0;
        double tomorrowOffset = date.plusDays(1).atTime(12, 0).atZone(ZONE)
                .getOffset().getTotalSeconds() / 60.0;

        double noon = 720 - 4 * LONGITUDE - sun.equationOfTime + offset;
        double nextNoon = 720 - 4 * LONGITUDE - tomorrow.equationOfTime + tomorrowOffset;
        double sunrise = event(noon, sun.declination, -0.833, false);
        double nextSunrise = event(nextNoon, tomorrow.declination, -0.833, false);
        double maghrib = event(noon, sun.declination, -0.833, true);
        double nightMinutes = nextSunrise + 1440 - maghrib;

        double fajr = event(noon, sun.declination, -FAJR_ANGLE, false);
        double latestSafeFajr = sunrise - nightMinutes * FAJR_ANGLE / 60.0;
        if (!Double.isFinite(fajr) || fajr < latestSafeFajr) fajr = latestSafeFajr;

        // Standard Asr (school=0): the shadow grows by one object height.
        double asrAltitude = Math.toDegrees(Math.atan(1.0 /
                (1.0 + Math.tan(Math.abs(Math.toRadians(LATITUDE) - sun.declination)))));
        double asr = event(noon, sun.declination, asrAltitude, true);

        Map<String, LocalTime> result = new LinkedHashMap<>();
        result.put("Fajr", clock(fajr));
        result.put("Sunrise", clock(sunrise));
        result.put("Dhuhr", clock(noon));
        result.put("Asr", clock(asr));
        result.put("Maghrib", clock(maghrib));
        result.put("Isha", clock(maghrib + 90));
        return Map.copyOf(result);
    }

    private static LocalTime clock(double minutes) {
        return LocalTime.MIDNIGHT.plusMinutes((long) Math.ceil(minutes));
    }

    private static double event(double noon, double declination, double altitude, boolean afternoon) {
        double latitude = Math.toRadians(LATITUDE);
        double numerator = Math.sin(Math.toRadians(altitude))
                - Math.sin(latitude) * Math.sin(declination);
        double denominator = Math.cos(latitude) * Math.cos(declination);
        double cosine = numerator / denominator;
        if (cosine < -1 || cosine > 1) return Double.NaN;
        double distance = 4 * Math.toDegrees(Math.acos(cosine));
        return noon + (afternoon ? distance : -distance);
    }

    private static SolarPosition solarPosition(LocalDate date) {
        // NOAA fractional-year approximation for declination and equation of time.
        double days = date.isLeapYear() ? 366 : 365;
        double gamma = 2 * Math.PI / days * (date.getDayOfYear() - 1);
        double equationOfTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma) - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));
        double declination = 0.006918 - 0.399912 * Math.cos(gamma)
                + 0.070257 * Math.sin(gamma) - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma) - 0.002697 * Math.cos(3 * gamma)
                + 0.00148 * Math.sin(3 * gamma);
        return new SolarPosition(equationOfTime, declination);
    }

    private record SolarPosition(double equationOfTime, double declination) { }

    private LocalPrayerTimes() { }
}
