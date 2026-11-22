import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MorningAthkarFxApp extends Application {
    private static final Preferences PROGRESS = Preferences.userRoot().node("MorningAthkarApp");
    private static final String INK = "#e8efe9";
    private static final String MUTED = "#91a39a";
    private static final String GREEN = "#60c49b";
    private static final String CARD = "#17231f";

    private final List<MorningAthkarData.Dhikr> athkar = MorningAthkarData.createAthkar();
    private final long startedAt = System.nanoTime();
    private int currentIndex;
    private int count;
    private LocalDate sessionDate = LocalDate.now();
    private boolean altDown;
    private boolean spaceDown;
    private boolean comboUsed;

    private Label pageLabel;
    private Label countLabel;
    private Label hintLabel;
    private Label sessionLabel;
    private Label clockLabel;
    private Button countButton;
    private Button resetButton;
    private Button previousButton;
    private Button nextButton;
    private ProgressBar progress;
    private Text prayerText;
    private TextFlow prayerFlow;
    private StackPane readingArea;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Morgen-Athkar | Deine Morgenroutine");
        stage.setAlwaysOnTop(true);
        stage.setMinWidth(500);
        stage.setMinHeight(400);
        var iconResource = MorningAthkarFxApp.class.getResourceAsStream("/morning-athkar.png");
        if (iconResource != null) {
            stage.getIcons().add(new Image(iconResource));
        }
        stage.setOnCloseRequest(event -> saveProgress());

        Label eyebrow = new Label("MORGENROUTINE");
        eyebrow.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 11px; -fx-font-weight: bold; -fx-letter-spacing: 2px;");
        Label title = new Label("Deine Morgen-Athkar");
        title.setStyle("-fx-text-fill: " + INK + "; -fx-font-size: 21px; -fx-font-weight: bold;");
        pageLabel = new Label();
        pageLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 13px;");
        VBox heading = new VBox(4, eyebrow, title, pageLabel);
        heading.setAlignment(Pos.CENTER);

        progress = new ProgressBar(0);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefHeight(5);
        progress.setStyle("-fx-accent: " + GREEN + ";");

        prayerText = new Text();
        prayerText.setFill(javafx.scene.paint.Color.web(INK));
        prayerText.setTextAlignment(TextAlignment.CENTER);
        prayerText.setFont(Font.font("Noto Naskh Arabic", 24));
        prayerFlow = new TextFlow(prayerText);
        prayerFlow.setTextAlignment(TextAlignment.CENTER);
        prayerFlow.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        prayerFlow.setMinSize(0, 0);
        prayerFlow.setPrefWidth(Region.USE_COMPUTED_SIZE);
        prayerFlow.setPrefHeight(Region.USE_COMPUTED_SIZE);
        prayerFlow.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        readingArea = new StackPane(prayerFlow);
        readingArea.setMinSize(0, 0);
        readingArea.setPadding(new Insets(2));
        Rectangle readingClip = new Rectangle();
        readingClip.widthProperty().bind(readingArea.widthProperty());
        readingClip.heightProperty().bind(readingArea.heightProperty());
        readingArea.setClip(readingClip);
        readingArea.heightProperty().addListener((obs, oldValue, newValue) -> fitPrayerText());
        readingArea.widthProperty().addListener((obs, oldValue, newValue) -> fitPrayerText());

        countLabel = new Label();
        countLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 16px; -fx-font-weight: bold;");
        VBox card = new VBox(8, readingArea, countLabel);
        card.setMinHeight(0);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setStyle("-fx-background-color: " + CARD + "; -fx-background-radius: 22px; -fx-border-color: #263831; -fx-border-radius: 22px;");
        VBox.setVgrow(readingArea, Priority.ALWAYS);

        resetButton = makeButton("↻  Anfang", false);
        previousButton = makeButton("←  Zurück", false);
        countButton = makeButton("+  Zählen", true);
        nextButton = makeButton("Weiter  →", false);
        resetButton.setOnAction(event -> showDhikr(0));
        previousButton.setOnAction(event -> showDhikr(currentIndex - 1));
        nextButton.setOnAction(event -> showDhikr(currentIndex + 1));
        countButton.setOnAction(event -> countOrAdvance());
        HBox controls = new HBox(8, resetButton, previousButton, countButton, nextButton);
        controls.setAlignment(Pos.CENTER);
        resetButton.setMaxWidth(Double.MAX_VALUE);
        previousButton.setMaxWidth(Double.MAX_VALUE);
        countButton.setMaxWidth(Double.MAX_VALUE);
        nextButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(resetButton, Priority.ALWAYS);
        HBox.setHgrow(previousButton, Priority.ALWAYS);
        HBox.setHgrow(countButton, Priority.ALWAYS);
        HBox.setHgrow(nextButton, Priority.ALWAYS);

        hintLabel = new Label();
        hintLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
        sessionLabel = new Label();
        sessionLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
        clockLabel = new Label();
        clockLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
        HBox footer = new HBox(sessionLabel, new javafx.scene.layout.Region(), clockLabel);
        HBox.setHgrow(footer.getChildren().get(1), Priority.ALWAYS);

        VBox content = new VBox(9, heading, progress, card, hintLabel, controls, footer);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(10, 16, 10, 16));
        VBox.setVgrow(card, Priority.ALWAYS);
        BorderPane root = new BorderPane(content);
        root.setStyle("-fx-background-color: #101815;");
        Scene scene = new Scene(root, 560, 460);
        stage.setScene(scene);
        stage.show();

        LocalDate today = LocalDate.now();
        boolean sameDay = today.toString().equals(PROGRESS.get("date", ""));
        sessionDate = today;
        int savedIndex = sameDay
                ? Math.max(0, Math.min(PROGRESS.getInt("currentIndex", 0), athkar.size() - 1)) : 0;
        int savedCount = sameDay
                ? Math.max(0, Math.min(PROGRESS.getInt("count", 0), athkar.get(savedIndex).targetCount)) : 0;
        showDhikr(savedIndex);
        count = savedCount;
        updateCountDisplay();
        saveProgress();
        startClocks();
        installGlobalHotkey();
    }

    private Button makeButton(String text, boolean primary) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(38);
        String base = primary ? "#27845f" : "#202d27";
        String hover = primary ? "#329b73" : "#2a3a32";
        button.setStyle("-fx-background-color: " + base + "; -fx-text-fill: " + (primary ? "white" : INK)
                + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand;");
        button.setOnMouseEntered(event -> button.setStyle("-fx-background-color: " + hover + "; -fx-text-fill: "
                + (primary ? "white" : INK) + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand;"));
        button.setOnMouseExited(event -> button.setStyle("-fx-background-color: " + base + "; -fx-text-fill: "
                + (primary ? "white" : INK) + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand;"));
        return button;
    }

    private void showDhikr(int index) {
        if (index < 0 || index >= athkar.size()) return;
        currentIndex = index;
        count = 0;
        MorningAthkarData.Dhikr dhikr = athkar.get(currentIndex);
        pageLabel.setText((currentIndex + 1) + "  /  " + athkar.size());
        countLabel.setText("Wiederholungen     " + count + "  /  " + dhikr.targetCount);
        progress.setProgress((currentIndex + 1.0) / athkar.size());
        previousButton.setDisable(currentIndex == 0);
        nextButton.setDisable(currentIndex == athkar.size() - 1);
        countButton.setText("+  Zählen");
        hintLabel.setText("In Ruhe lesen und anschließend zählen · Alt + Leertaste zählt mit");
        setPrayerText(dhikr.arabicText);
        saveProgress();
    }

    private void setPrayerText(String value) {
        prayerText.setText(value);
        Platform.runLater(this::fitPrayerText);
    }

    private void fitPrayerText() {
        if (prayerFlow == null || readingArea == null || readingArea.getHeight() <= 0) return;
        double availableWidth = Math.max(1, readingArea.getWidth() - 8);
        double availableHeight = Math.max(1, readingArea.getHeight() - 8);
        prayerFlow.setPrefWidth(availableWidth);
        for (double size = 19; size >= 5; size -= 0.5) {
            prayerText.setFont(Font.font("Noto Naskh Arabic", size));
            prayerFlow.applyCss();
            double wrappedHeight = prayerFlow.prefHeight(availableWidth);
            if (wrappedHeight <= availableHeight) break;
        }
    }

    private void countOrAdvance() {
        MorningAthkarData.Dhikr dhikr = athkar.get(currentIndex);
        if (count < dhikr.targetCount) {
            count++;
            updateCountDisplay();
        } else if (currentIndex < athkar.size() - 1) {
            showDhikr(currentIndex + 1);
        }
        boolean complete = count >= dhikr.targetCount;
        countButton.setText(complete
                ? (currentIndex < athkar.size() - 1 ? "Weiter  →" : "Fertig  ✓")
                : "+  Zählen");
        hintLabel.setText(complete ? "Abgeschlossen · Weiter zur nächsten Erinnerung"
                : "In Ruhe lesen und anschließend zählen · Alt + Leertaste zählt mit");
        saveProgress();
    }

    private void updateCountDisplay() {
        MorningAthkarData.Dhikr dhikr = athkar.get(currentIndex);
        countLabel.setText("Wiederholungen     " + count + "  /  " + dhikr.targetCount);
        boolean complete = count >= dhikr.targetCount;
        countButton.setText(complete
                ? (currentIndex < athkar.size() - 1 ? "Weiter  →" : "Fertig  ✓")
                : "+  Zählen");
        hintLabel.setText(complete
                ? "Abgeschlossen · Weiter zur nächsten Erinnerung"
                : "In Ruhe lesen und anschließend zählen · Alt + Leertaste zählt mit");
    }

    private void saveProgress() {
        PROGRESS.putInt("currentIndex", currentIndex);
        PROGRESS.putInt("count", count);
        PROGRESS.put("date", sessionDate.toString());
    }

    private void startClocks() {
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), event -> {
                    LocalDate today = LocalDate.now();
                    if (!today.equals(sessionDate)) {
                        sessionDate = today;
                        showDhikr(0);
                    }
                    long seconds = Duration.ofNanos(System.nanoTime() - startedAt).toSeconds();
                    sessionLabel.setText("Sitzung  " + String.format("%02d:%02d", seconds / 3600, (seconds % 3600) / 60));
                    clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
                }));
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.play();
    }

    private void installGlobalHotkey() {
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                @Override public void nativeKeyPressed(NativeKeyEvent event) {
                    if (event.getKeyCode() == NativeKeyEvent.VC_ALT) altDown = true;
                    if (event.getKeyCode() == NativeKeyEvent.VC_SPACE) spaceDown = true;
                    if (altDown && spaceDown && !comboUsed) {
                        comboUsed = true;
                        Platform.runLater(MorningAthkarFxApp.this::countOrAdvance);
                    }
                }
                @Override public void nativeKeyReleased(NativeKeyEvent event) {
                    if (event.getKeyCode() == NativeKeyEvent.VC_ALT) altDown = false;
                    if (event.getKeyCode() == NativeKeyEvent.VC_SPACE) spaceDown = false;
                    if (!altDown || !spaceDown) comboUsed = false;
                }
                @Override public void nativeKeyTyped(NativeKeyEvent event) { }
            });
        } catch (NativeHookException exception) {
            hintLabel.setText("Tastenkürzel nicht verfügbar · Du kannst über die Schaltfläche zählen");
        }
    }

    @Override
    public void stop() {
        saveProgress();
        try {
            PROGRESS.flush();
        } catch (BackingStoreException ignored) {
            // The next interaction will retry saving the current progress.
        }
        if (GlobalScreen.isNativeHookRegistered()) {
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (NativeHookException ignored) {
                // The application is already closing.
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
