import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MorningAthkarFxApp extends Application {
    private static final Preferences SETTINGS = Preferences.userRoot().node("MorningAthkarApp");
    private static final URI MAWAQIT = URI.create(
            "https://mawaqit.net/ar/m/msjd-bll-siegen-57076-germany");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN);
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] PRAYER_LABELS =
            {"Fajr", "Sonnenaufgang", "Dhuhr", "Asr", "Maghrib", "Isha"};

    private final EnumMap<AthkarData.Routine, ProgressState> progressByRoutine =
            new EnumMap<>(AthkarData.Routine.class);
    private final PrayerTimesService prayerService = new PrayerTimesService();
    private final Map<String, Label> prayerTimeLabels = new LinkedHashMap<>();
    private final long startedAt = System.nanoTime();

    private Map<AthkarData.Routine, List<AthkarData.Dhikr>> routines;
    private AthkarData.Routine activeRoutine = AthkarData.Routine.MORNING;
    private LocalDate sessionDate = LocalDate.now();
    private PrayerTimesService.Times prayerTimes;
    private boolean prayerTimesLoading;
    private boolean showingPrayerTimes;
    private boolean darkTheme;
    private boolean keepScreenAwake;
    private boolean altDown;
    private boolean spaceDown;
    private boolean comboUsed;

    private Stage stage;
    private VBox root;
    private VBox athkarView;
    private VBox prayerView;
    private VBox readingCard;
    private StackPane readingArea;
    private Text prayerText;
    private TextFlow prayerFlow;
    private Label eyebrowLabel;
    private Label titleLabel;
    private Label pageLabel;
    private Label dateLabel;
    private Label countLabel;
    private Label hintLabel;
    private Label sessionLabel;
    private Label clockLabel;
    private Label countdownCaption;
    private Label countdownValue;
    private Label prayerStatus;
    private ProgressBar progressBar;
    private Button themeButton;
    private Button wakeButton;
    private Button resetButton;
    private Button morningTab;
    private Button eveningTab;
    private Button prayerTab;
    private Button previousButton;
    private Button countButton;
    private Button nextButton;
    private Timeline clockTimeline;
    private Timeline prayerRefreshTimeline;

    private static final class ProgressState {
        int index;
        int count;
    }

    private interface WindowsWake extends StdCallLibrary {
        WindowsWake INSTANCE = Native.load("kernel32", WindowsWake.class);
        int SetThreadExecutionState(int flags);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        try {
            routines = AthkarData.load();
        } catch (IOException exception) {
            new Alert(Alert.AlertType.ERROR, exception.getMessage()).showAndWait();
            Platform.exit();
            return;
        }

        for (AthkarData.Routine routine : AthkarData.Routine.values()) {
            progressByRoutine.put(routine, new ProgressState());
        }
        loadProgress();
        buildUi();
        darkTheme = "dark".equals(SETTINGS.get("theme", "original"));
        applyTheme();
        render();
        updateClocks();
        loadPrayerTimes();
        startTimers();
        installGlobalHotkey();
        stage.show();
        Platform.runLater(this::fitArabicText);
    }

    private void buildUi() {
        stage.setTitle("Athkar & Gebetszeiten");
        stage.setAlwaysOnTop(true);
        stage.setMinWidth(530);
        stage.setMinHeight(510);
        var iconUrl = getClass().getResource("/morning-athkar.png");
        if (iconUrl != null) stage.getIcons().add(new Image(iconUrl.toExternalForm()));

        ImageView appIcon = new ImageView();
        if (iconUrl != null) appIcon.setImage(new Image(iconUrl.toExternalForm()));
        appIcon.setFitWidth(34);
        appIcon.setFitHeight(34);
        appIcon.setPreserveRatio(true);

        eyebrowLabel = styledLabel("MORGENROUTINE", "eyebrow");
        titleLabel = styledLabel("Deine Morgen-Athkar", "app-title");
        VBox branding = new VBox(2, eyebrowLabel, titleLabel);
        HBox.setHgrow(branding, Priority.ALWAYS);

        themeButton = button("", "icon-button", "Darkmodus umschalten", this::toggleTheme);
        wakeButton = button("", "icon-button", "Bildschirm wach halten", this::toggleWakeLock);
        resetButton = button("", "icon-button", "Zum Anfang", () -> showDhikr(0));
        wakeButton.setGraphic(toolbarIcon("M3 4 H21 V17 H3 Z M9 21 H15 M12 17 V21"));
        resetButton.setGraphic(toolbarIcon("M4 11 A8 8 0 1 1 6 18 M4 5 V11 H10"));
        HBox topbar = new HBox(9, appIcon, branding, themeButton, wakeButton, resetButton);
        topbar.setAlignment(Pos.CENTER_LEFT);

        morningTab = button("أذكار الصباح", "tab-button", "Morgen-Athkar", () -> selectRoutine(AthkarData.Routine.MORNING));
        eveningTab = button("أذكار المساء", "tab-button", "Abend-Athkar", () -> selectRoutine(AthkarData.Routine.EVENING));
        prayerTab = button("أوقات الصلاة", "tab-button", "Gebetszeiten", this::selectPrayerTimes);
        HBox tabs = new HBox(8, morningTab, eveningTab, prayerTab);
        for (Button tab : List.of(morningTab, eveningTab, prayerTab)) {
            tab.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(tab, Priority.ALWAYS);
        }

        buildAthkarView();
        buildPrayerView();

        sessionLabel = styledLabel("Sitzung  0h 0m", "footer-label");
        clockLabel = styledLabel("--:--", "footer-label");
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(sessionLabel, footerSpacer, clockLabel);
        footer.setAlignment(Pos.CENTER_LEFT);

        root = new VBox(12, topbar, tabs, athkarView, prayerView, footer);
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(17, 19, 14, 19));
        VBox.setVgrow(athkarView, Priority.ALWAYS);
        VBox.setVgrow(prayerView, Priority.ALWAYS);

        Scene scene = new Scene(root, 620, 580);
        var stylesheet = getClass().getResource("/desktop.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (showingPrayerTimes || event.getButton() != MouseButton.PRIMARY) return;
            if (event.getTarget() instanceof Node node && !isInteractive(node)) {
                countOrAdvance();
            }
        });
        stage.iconifiedProperty().addListener((ignored, oldValue, newValue) -> updateWakeLock());
        stage.setScene(scene);
    }

    private void buildAthkarView() {
        pageLabel = styledLabel("1 / 23", "small-label");
        dateLabel = styledLabel("", "small-label");
        Region metaSpacer = new Region();
        HBox.setHgrow(metaSpacer, Priority.ALWAYS);
        HBox meta = new HBox(pageLabel, metaSpacer, dateLabel);

        progressBar = new ProgressBar();
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("progress-bar");

        countdownCaption = styledLabel("", "countdown-caption");
        countdownCaption.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        countdownValue = styledLabel("--", "countdown-value");
        Region countdownSpacer = new Region();
        HBox.setHgrow(countdownSpacer, Priority.ALWAYS);
        HBox countdown = new HBox(8, countdownCaption, countdownSpacer, countdownValue);
        countdown.setAlignment(Pos.CENTER_LEFT);
        VBox session = new VBox(7, meta, progressBar, countdown);

        prayerText = new Text();
        prayerText.getStyleClass().add("arabic-text");
        prayerText.setTextAlignment(TextAlignment.CENTER);
        prayerText.setFont(Font.font("Noto Naskh Arabic", 24));
        prayerFlow = new TextFlow(prayerText);
        prayerFlow.setTextAlignment(TextAlignment.CENTER);
        prayerFlow.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        prayerFlow.setMinSize(0, 0);
        prayerFlow.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        readingArea = new StackPane(prayerFlow);
        readingArea.setMinSize(0, 0);
        readingArea.setPadding(new Insets(4));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(readingArea.widthProperty());
        clip.heightProperty().bind(readingArea.heightProperty());
        readingArea.setClip(clip);
        readingArea.widthProperty().addListener((ignored, oldValue, newValue) -> fitArabicText());
        readingArea.heightProperty().addListener((ignored, oldValue, newValue) -> fitArabicText());

        Label countCaption = styledLabel("Wiederholungen", "count-caption");
        countLabel = styledLabel("0 / 1", "count-value");
        HBox countRow = new HBox(13, countCaption, countLabel);
        countRow.setAlignment(Pos.CENTER);
        readingCard = new VBox(8, readingArea, countRow);
        readingCard.getStyleClass().add("reading-card");
        readingCard.setAlignment(Pos.CENTER);
        readingCard.setPadding(new Insets(15, 20, 17, 20));
        readingCard.setMinHeight(0);
        VBox.setVgrow(readingArea, Priority.ALWAYS);

        hintLabel = styledLabel("In Ruhe lesen und anschließend zählen", "hint-label");
        hintLabel.setMaxWidth(Double.MAX_VALUE);
        hintLabel.setAlignment(Pos.CENTER);

        previousButton = button("← Zurück", "secondary-button", "Vorherige Erinnerung", () -> showDhikr(state().index - 1));
        countButton = button("+ Zählen", "count-button", "Wiederholung zählen", this::countOrAdvance);
        nextButton = button("Weiter →", "secondary-button", "Nächste Erinnerung", () -> showDhikr(state().index + 1));
        HBox controls = new HBox(9, previousButton, countButton, nextButton);
        for (Button control : List.of(previousButton, countButton, nextButton)) {
            control.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(control, Priority.ALWAYS);
        }

        athkarView = new VBox(11, session, readingCard, hintLabel, controls);
        athkarView.setMinHeight(0);
        VBox.setVgrow(readingCard, Priority.ALWAYS);
    }

    private void buildPrayerView() {
        Label source = styledLabel("Gebetszeiten für Siegen · berechnete Zeiten", "prayer-source");
        prayerStatus = styledLabel("Gebetszeiten werden geladen …", "prayer-status");
        GridPane grid = new GridPane();
        grid.getStyleClass().add("prayer-grid");
        grid.setHgap(25);
        grid.setVgap(11);
        for (int i = 0; i < PrayerTimesService.PRAYERS.length; i++) {
            String key = PrayerTimesService.PRAYERS[i];
            grid.add(styledLabel(PRAYER_LABELS[i], "prayer-name"), 0, i);
            Label time = styledLabel("--:--", "prayer-time");
            prayerTimeLabels.put(key, time);
            grid.add(time, 1, i);
        }
        Button mawaqitButton = button("MAWAQIT im Browser öffnen ↗", "count-button",
                "Gebetszeiten der Bilal-Moschee auf MAWAQIT", () -> openExternal(MAWAQIT));
        mawaqitButton.setMaxWidth(Double.MAX_VALUE);
        prayerView = new VBox(14, source, prayerStatus, grid, mawaqitButton);
        prayerView.getStyleClass().add("prayer-view");
        prayerView.setAlignment(Pos.TOP_CENTER);
        prayerView.setPadding(new Insets(20));
        prayerView.setMinHeight(0);
    }

    private Label styledLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private Button button(String text, String styleClass, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(ignored -> action.run());
        return button;
    }

    private SVGPath toolbarIcon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setFill(Color.TRANSPARENT);
        icon.setStroke(Color.web("#e8efe9"));
        icon.setStrokeWidth(1.8);
        icon.setStrokeLineCap(StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(StrokeLineJoin.ROUND);
        icon.setScaleX(0.82);
        icon.setScaleY(0.82);
        return icon;
    }

    private boolean isInteractive(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof Button || current instanceof Hyperlink) return true;
        }
        return false;
    }

    private ProgressState state() {
        return progressByRoutine.get(activeRoutine);
    }

    private void selectRoutine(AthkarData.Routine routine) {
        activeRoutine = routine;
        showingPrayerTimes = false;
        render();
    }

    private void selectPrayerTimes() {
        showingPrayerTimes = true;
        render();
    }

    private void showDhikr(int index) {
        List<AthkarData.Dhikr> entries = routines.get(activeRoutine);
        if (index < 0 || index >= entries.size()) return;
        state().index = index;
        state().count = 0;
        render();
        saveProgress();
    }

    private void countOrAdvance() {
        if (showingPrayerTimes) return;
        ProgressState state = state();
        List<AthkarData.Dhikr> entries = routines.get(activeRoutine);
        int target = entries.get(state.index).targetCount();
        if (state.count < target) {
            state.count++;
            if (state.count == target) giveCompleteFeedback();
        } else if (state.index < entries.size() - 1) {
            state.index++;
            state.count = 0;
        }
        render();
        saveProgress();
    }

    private void giveCompleteFeedback() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (RuntimeException ignored) {
            // Counting remains usable when desktop sound is unavailable.
        }
    }

    private void render() {
        boolean morning = activeRoutine == AthkarData.Routine.MORNING;
        eyebrowLabel.setText(showingPrayerTimes ? "SIEGEN" : morning ? "MORGENROUTINE" : "ABENDROUTINE");
        titleLabel.setText(showingPrayerTimes ? "Gebetszeiten" : morning ? "Deine Morgen-Athkar" : "Deine Abend-Athkar");
        stage.setTitle(showingPrayerTimes ? "Gebetszeiten · Siegen" : morning ? "Morgen-Athkar" : "Abend-Athkar");
        setVisibleManaged(athkarView, !showingPrayerTimes);
        setVisibleManaged(prayerView, showingPrayerTimes);
        setVisibleManaged(resetButton, !showingPrayerTimes);
        setVisibleManaged(wakeButton, !showingPrayerTimes);
        setTabSelected(morningTab, !showingPrayerTimes && morning);
        setTabSelected(eveningTab, !showingPrayerTimes && !morning);
        setTabSelected(prayerTab, showingPrayerTimes);

        List<AthkarData.Dhikr> entries = routines.get(activeRoutine);
        ProgressState state = state();
        AthkarData.Dhikr dhikr = entries.get(state.index);
        pageLabel.setText((state.index + 1) + " / " + entries.size());
        dateLabel.setText(LocalDate.now().format(DATE_FORMAT));
        progressBar.setProgress((state.index + 1.0) / entries.size());
        prayerText.setText(dhikr.arabicText());
        countLabel.setText(state.count + " / " + dhikr.targetCount());
        previousButton.setDisable(state.index == 0);
        nextButton.setDisable(state.index == entries.size() - 1);
        boolean complete = state.count >= dhikr.targetCount();
        countButton.setText(complete ? state.index < entries.size() - 1 ? "Weiter →" : "Fertig ✓" : "+ Zählen");
        hintLabel.setText(complete ? "Abgeschlossen · Weiter zur nächsten Erinnerung"
                : "In Ruhe lesen und anschließend zählen · Alt + Leertaste zählt mit");
        updateCountdown();
        Platform.runLater(this::fitArabicText);
    }

    private void setTabSelected(Button tab, boolean selected) {
        tab.getStyleClass().remove("selected");
        if (selected) tab.getStyleClass().add("selected");
    }

    private void setVisibleManaged(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void fitArabicText() {
        if (readingArea.getHeight() <= 0 || !readingArea.isVisible()) return;
        double width = Math.max(1, readingArea.getWidth() - 10);
        double height = Math.max(1, readingArea.getHeight() - 10);
        prayerFlow.setPrefWidth(width);
        for (double size = 25; size >= 8; size -= 0.5) {
            prayerText.setFont(Font.font("Noto Naskh Arabic", size));
            prayerFlow.applyCss();
            if (prayerFlow.prefHeight(width) <= height) break;
        }
    }

    private void loadProgress() {
        if (!sessionDate.toString().equals(SETTINGS.get("date", ""))) return;
        for (AthkarData.Routine routine : AthkarData.Routine.values()) {
            ProgressState state = progressByRoutine.get(routine);
            List<AthkarData.Dhikr> entries = routines.get(routine);
            String prefix = routine.jsonKey + ".";
            int legacyIndex = routine == AthkarData.Routine.MORNING ? SETTINGS.getInt("currentIndex", 0) : 0;
            int legacyCount = routine == AthkarData.Routine.MORNING ? SETTINGS.getInt("count", 0) : 0;
            state.index = Math.clamp(SETTINGS.getInt(prefix + "index", legacyIndex), 0, entries.size() - 1);
            state.count = Math.clamp(SETTINGS.getInt(prefix + "count", legacyCount),
                    0, entries.get(state.index).targetCount());
        }
    }

    private void saveProgress() {
        SETTINGS.put("date", sessionDate.toString());
        for (AthkarData.Routine routine : AthkarData.Routine.values()) {
            ProgressState state = progressByRoutine.get(routine);
            SETTINGS.putInt(routine.jsonKey + ".index", state.index);
            SETTINGS.putInt(routine.jsonKey + ".count", state.count);
        }
    }

    private void startTimers() {
        clockTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), ignored -> updateClocks()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
        prayerRefreshTimeline = new Timeline(new KeyFrame(javafx.util.Duration.minutes(15), ignored -> loadPrayerTimes()));
        prayerRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        prayerRefreshTimeline.play();
    }

    private void updateClocks() {
        LocalDate today = LocalDate.now();
        if (!today.equals(sessionDate)) {
            sessionDate = today;
            for (ProgressState state : progressByRoutine.values()) {
                state.index = 0;
                state.count = 0;
            }
            prayerTimes = null;
            saveProgress();
            render();
            loadPrayerTimes();
        }
        long minutes = Duration.ofNanos(System.nanoTime() - startedAt).toMinutes();
        sessionLabel.setText("Sitzung  " + formatDuration(minutes));
        clockLabel.setText(LocalTime.now().format(CLOCK_FORMAT));
        updateCountdown();
    }

    private void loadPrayerTimes() {
        if (prayerTimesLoading) return;
        LocalDate requestedDate = sessionDate;
        PrayerTimesService.Times cached = prayerService.cachedFor(requestedDate);
        prayerTimes = cached != null ? cached : prayerService.localFor(requestedDate);
        renderPrayerTimes();
        updateCountdown();
        prayerTimesLoading = true;
        prayerService.fetch(requestedDate).whenComplete((times, error) -> Platform.runLater(() -> {
            prayerTimesLoading = false;
            if (!requestedDate.equals(sessionDate)) {
                loadPrayerTimes();
                return;
            }
            if (error == null) prayerTimes = times;
            renderPrayerTimes();
            updateCountdown();
        }));
    }

    private void renderPrayerTimes() {
        if (prayerTimes == null || !sessionDate.equals(prayerTimes.date())) {
            prayerStatus.setText("Gebetszeiten konnten nicht geladen werden. MAWAQIT im Browser öffnen.");
            prayerTimeLabels.values().forEach(label -> label.setText("--:--"));
            return;
        }
        prayerStatus.setText("Heute, " + sessionDate.format(DATE_FORMAT)
                + (prayerTimes.locallyCalculated() ? " · lokal berechnet (Näherung)" : " · Aladhan"));
        prayerTimeLabels.forEach((name, label) -> label.setText(prayerTimes.get(name).format(CLOCK_FORMAT)));
    }

    private void updateCountdown() {
        if (countdownCaption == null) return;
        boolean morning = activeRoutine == AthkarData.Routine.MORNING;
        if (prayerTimes == null || !sessionDate.equals(prayerTimes.date())) {
            countdownCaption.setText("Gebetszeiten nicht verfügbar");
            countdownValue.setText("--");
            return;
        }

        LocalTime start = prayerTimes.get(morning ? "Fajr" : "Asr");
        LocalTime end = prayerTimes.get(morning ? "Dhuhr" : "Maghrib").minusMinutes(15);
        LocalTime now = LocalTime.now();
        if (now.isBefore(start) || !now.isBefore(end)) {
            countdownCaption.setText(morning ? "ليس وقت أذكار الصباح" : "ليس وقت أذكار المساء");
            countdownValue.setText("--");
            return;
        }

        countdownCaption.setText(morning
                ? "المتبقي حتى خروج وقت أذكار الصباح"
                : "المتبقي حتى خروج وقت أذكار المساء");
        long seconds = Duration.between(now, end).getSeconds();
        long totalMinutes = (seconds + 59) / 60;
        countdownValue.setText(formatDuration(totalMinutes));
    }

    private String formatDuration(long totalMinutes) {
        return totalMinutes / 60 + "h " + totalMinutes % 60 + "m";
    }

    private void toggleTheme() {
        darkTheme = !darkTheme;
        SETTINGS.put("theme", darkTheme ? "dark" : "original");
        applyTheme();
    }

    private void applyTheme() {
        root.getStyleClass().removeAll("theme-original", "theme-dark");
        root.getStyleClass().add(darkTheme ? "theme-dark" : "theme-original");
        themeButton.setGraphic(toolbarIcon(darkTheme
                ? "M12 7 A5 5 0 1 0 12 17 A5 5 0 1 0 12 7 M12 1 V3 M12 21 V23 "
                    + "M4.22 4.22 L5.64 5.64 M18.36 18.36 L19.78 19.78 "
                    + "M1 12 H3 M21 12 H23 M4.22 19.78 L5.64 18.36 M18.36 5.64 L19.78 4.22"
                : "M20.6 15.5 A9 9 0 1 1 8.5 3.4 A7 7 0 0 0 20.6 15.5 Z"));
        themeButton.setTooltip(new Tooltip(darkTheme ? "Originalmodus aktivieren" : "Darkmodus aktivieren"));
    }

    private void toggleWakeLock() {
        keepScreenAwake = !keepScreenAwake;
        updateWakeLock();
    }

    private void updateWakeLock() {
        try {
            int flags = keepScreenAwake && !stage.isIconified() ? 0x80000002 : 0x80000000;
            if (WindowsWake.INSTANCE.SetThreadExecutionState(flags) == 0) {
                keepScreenAwake = false;
            }
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError ignored) {
            keepScreenAwake = false;
            wakeButton.setDisable(true);
        }
        wakeButton.getStyleClass().remove("selected");
        if (keepScreenAwake) wakeButton.getStyleClass().add("selected");
        wakeButton.setTooltip(new Tooltip(keepScreenAwake
                ? "Bildschirm wach halten ausschalten" : "Bildschirm wach halten aktivieren"));
    }

    private void openExternal(URI uri) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(uri);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            prayerStatus.setText("Browser konnte nicht geöffnet werden: " + uri);
        }
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
        } catch (NativeHookException ignored) {
            hintLabel.setText("Tastenkürzel nicht verfügbar · Über die Schaltfläche zählen");
        }
    }

    @Override
    public void stop() {
        if (clockTimeline != null) clockTimeline.stop();
        if (prayerRefreshTimeline != null) prayerRefreshTimeline.stop();
        if (keepScreenAwake) {
            keepScreenAwake = false;
            updateWakeLock();
        }
        if (routines != null) saveProgress();
        try {
            SETTINGS.flush();
        } catch (BackingStoreException ignored) {
            // The most recent in-memory progress remains available for this session.
        }
        if (GlobalScreen.isNativeHookRegistered()) {
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (NativeHookException ignored) { }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
