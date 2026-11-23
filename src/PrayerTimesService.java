import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PrayerTimesService {
    static final String[] PRAYERS = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final Pattern CLOCK = Pattern.compile("([01]?\\d|2[0-3]):([0-5]\\d)");
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    record Times(LocalDate date, Map<String, LocalTime> values, boolean locallyCalculated) {
        LocalTime get(String prayer) {
            return values.get(prayer);
        }
    }

    private final Preferences cache = Preferences.userRoot().node("MorningAthkarApp/prayerTimes");
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    Times cachedFor(LocalDate date) {
        if (!date.toString().equals(cache.get("date", ""))) return null;
        try {
            Map<String, LocalTime> values = new LinkedHashMap<>();
            for (String prayer : PRAYERS) {
                values.put(prayer, LocalTime.parse(cache.get(prayer, "")));
            }
            return new Times(date, Map.copyOf(values), false);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    Times localFor(LocalDate date) {
        return new Times(date, LocalPrayerTimes.calculate(date), true);
    }

    CompletableFuture<Times> fetch(LocalDate date) {
        String query = "city=" + URLEncoder.encode("Siegen", StandardCharsets.UTF_8)
                + "&country=" + URLEncoder.encode("Germany", StandardCharsets.UTF_8)
                + "&method=4&school=0";
        URI url = URI.create("https://api.aladhan.com/v1/timingsByCity/"
                + date.format(API_DATE) + "?" + query);
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("Gebetszeiten-API: " + response.statusCode());
                    }
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (root.get("code").getAsInt() != 200) {
                        throw new IllegalStateException("Gebetszeiten wurden nicht geliefert.");
                    }
                    JsonObject timings = root.getAsJsonObject("data").getAsJsonObject("timings");
                    Map<String, LocalTime> values = new LinkedHashMap<>();
                    for (String prayer : PRAYERS) {
                        Matcher match = CLOCK.matcher(timings.get(prayer).getAsString());
                        if (!match.find()) {
                            throw new IllegalStateException("Ungültige Gebetszeit: " + prayer);
                        }
                        values.put(prayer, LocalTime.of(
                                Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2))));
                    }
                    Times times = new Times(date, Map.copyOf(values), false);
                    try {
                        cache.put("date", date.toString());
                        values.forEach((name, time) -> cache.put(name, time.toString()));
                    } catch (RuntimeException ignored) {
                        // Current times remain usable if local caching is blocked.
                    }
                    return times;
                });
    }
}
