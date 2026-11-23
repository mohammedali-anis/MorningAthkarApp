import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

final class AthkarData {
    enum Routine {
        MORNING("morning"), EVENING("evening");

        final String jsonKey;

        Routine(String jsonKey) {
            this.jsonKey = jsonKey;
        }
    }

    record Dhikr(String arabicText, int targetCount) { }

    static Map<Routine, List<Dhikr>> load() throws IOException {
        InputStream stream = AthkarData.class.getResourceAsStream("/athkar.json");
        if (stream == null) {
            throw new IOException("Die Athkar-Daten fehlen in der App.");
        }

        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<Routine, List<Dhikr>> result = new EnumMap<>(Routine.class);
            for (Routine routine : Routine.values()) {
                JsonArray entries = root.getAsJsonArray(routine.jsonKey);
                if (entries == null || entries.isEmpty()) {
                    throw new IOException("Athkar fehlen: " + routine.jsonKey);
                }

                List<Dhikr> items = new ArrayList<>();
                for (JsonElement entry : entries) {
                    JsonObject item = entry.getAsJsonObject();
                    String arabicText = item.get("arabicText").getAsString();
                    int targetCount = item.get("targetCount").getAsInt();
                    if (arabicText.isBlank() || targetCount < 1) {
                        throw new IOException("Ungültiger Athkar-Eintrag: " + routine.jsonKey);
                    }
                    items.add(new Dhikr(arabicText, targetCount));
                }
                result.put(routine, List.copyOf(items));
            }
            return result;
        } catch (IllegalStateException | NullPointerException | JsonParseException exception) {
            throw new IOException("Die Athkar-Daten sind beschädigt.", exception);
        }
    }

    private AthkarData() { }
}
