
const STORAGE_KEY = "morning-athkar-progress-v1";
const THEME_KEY = "morning-athkar-theme-v1";
const PRAYER_TIMES_KEY = "morning-athkar-city-times-v2";
const PRAYER_TIMES_API = "https://api.aladhan.com/v1/timingsByCity";
const PRAYER_TIME_ZONE = "Europe/Berlin";

const sessionStartedAt = Date.now();

const elements = {
  page: document.querySelector("#page-label"),
  date: document.querySelector("#date-label"),
  progress: document.querySelector("#progress-value"),
  progressTrack: document.querySelector(".progress-track"),
  windowCountdownLabel: document.querySelector("#window-countdown-label"),
  windowCountdownValue: document.querySelector("#window-countdown-value"),
  arabic: document.querySelector("#arabic-text"),
  count: document.querySelector("#count-label"),
  hint: document.querySelector("#hint-label"),
  countButton: document.querySelector("#count-button"),
  themeButton: document.querySelector("#theme-button"),
  themeIcon: document.querySelector("#theme-icon"),
  screenWakeButton: document.querySelector("#screen-wake-button"),
  morningTab: document.querySelector("#morning-tab"),
  eveningTab: document.querySelector("#evening-tab"),
  prayerTab: document.querySelector("#prayer-tab"),
  prayerPanel: document.querySelector("#prayer-panel"),
  shell: document.querySelector(".app-shell"),
  routineEyebrow: document.querySelector("#routine-eyebrow"),
  routineTitle: document.querySelector("#routine-title"),
  readingArea: document.body,
  readingViewport: document.querySelector(".reading-scroll"),
  previous: document.querySelector("#previous-button"),
  next: document.querySelector("#next-button"),
  session: document.querySelector("#session-label"),
  clock: document.querySelector("#clock-label"),
};

let routines = {
  morning: [],
  evening: [],
};

let activeRoutine = "morning";

let progressByRoutine = {
  morning: { currentIndex: 0, count: 0 },
  evening: { currentIndex: 0, count: 0 },
};

let currentIndex = 0;
let count = 0;
let sessionDate = localDay();

let audioContext;

let prayerTimes = null;
let prayerTimesLoading = false;

let keepScreenAwake = false;
let screenWakeLock = null;
let screenWakeLockRequest = null;

// Automatische Tab-Auswahl
let tabManuallySelected = false;
let lastPrayerDay = prayerDay();

// Zeitzone Siegen
const prayerDateFormatter = new Intl.DateTimeFormat("en-GB", {
  timeZone: PRAYER_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

// ==========================================
// THEME
// ==========================================

function applyTheme(theme, save = false) {
  const dark = theme === "dark";

  document.documentElement.dataset.theme =
    dark ? "dark" : "original";

  elements.themeButton.setAttribute(
    "aria-pressed",
    String(dark)
  );

  elements.themeButton.setAttribute(
    "aria-label",
    dark
      ? "Originalmodus aktivieren"
      : "Darkmodus aktivieren"
  );

  elements.themeButton.title = dark
    ? "Originalmodus aktivieren"
    : "Darkmodus aktivieren";

  elements.themeIcon.textContent = String.fromCodePoint(
    dark ? 0x2600 : 0x263e
  );

  const themeMeta = document.querySelector(
    'meta[name="theme-color"]'
  );

  if (themeMeta) {
    themeMeta.content = dark ? "#080d0b" : "#101815";
  }

  if (!save) return;

  try {
    localStorage.setItem(
      THEME_KEY,
      dark ? "dark" : "original"
    );
  } catch {
    // Theme bleibt bis zum Schließen aktiv.
  }
}

// ==========================================
// SCREEN WAKE LOCK
// ==========================================

function updateScreenWakeButton() {
  const label = keepScreenAwake
    ? "Bildschirm wach halten ausschalten"
    : "Bildschirm wach halten aktivieren";

  elements.screenWakeButton.setAttribute(
    "aria-pressed",
    String(keepScreenAwake)
  );

  elements.screenWakeButton.setAttribute(
    "aria-label",
    label
  );

  elements.screenWakeButton.title = label;
}

async function requestScreenWakeLock() {
  if (
    !keepScreenAwake ||
    document.visibilityState !== "visible" ||
    screenWakeLock ||
    screenWakeLockRequest ||
    !navigator.wakeLock?.request
  ) {
    return;
  }

  screenWakeLockRequest =
    navigator.wakeLock.request("screen");

  try {
    const lock = await screenWakeLockRequest;

    if (
      !keepScreenAwake ||
      document.visibilityState !== "visible"
    ) {
      await lock.release();
      return;
    }

    screenWakeLock = lock;

    lock.addEventListener("release", () => {
      if (screenWakeLock === lock) {
        screenWakeLock = null;
      }

      if (
        keepScreenAwake &&
        document.visibilityState === "visible"
      ) {
        requestScreenWakeLock();
      }
    });
  } catch {
    keepScreenAwake = false;
    updateScreenWakeButton();
  } finally {
    screenWakeLockRequest = null;
  }
}

async function toggleScreenWakeLock() {
  keepScreenAwake = !keepScreenAwake;

  updateScreenWakeButton();

  if (keepScreenAwake) {
    await requestScreenWakeLock();
  } else if (screenWakeLock) {
    const lock = screenWakeLock;
    screenWakeLock = null;
    await lock.release();
  }
}

// ==========================================
// DATE & TIME
// ==========================================

function localDay(date = new Date()) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    })
      .formatToParts(date)
      .map(({ type, value }) => [type, value])
  );

  return `${parts.year}-${parts.month}-${parts.day}`;
}

function getPrayerDateParts(date = new Date()) {
  return Object.fromEntries(
    prayerDateFormatter
      .formatToParts(date)
      .filter(({ type }) => type !== "literal")
      .map(({ type, value }) => [type, value])
  );
}

function prayerDay(date = new Date()) {
  const parts = getPrayerDateParts(date);

  return `${parts.year}-${parts.month}-${parts.day}`;
}

function currentPrayerMinutes(date = new Date()) {
  const parts = getPrayerDateParts(date);

  return Number(parts.hour) * 60 +
    Number(parts.minute);
}

function timeToMinutes(time) {
  const [hours, minutes] = time.split(":").map(Number);

  return hours * 60 + minutes;
}

function parsePrayerClock(value) {
  const match = value?.match(
    /(?:^|\D)([01]?\d|2[0-3]):([0-5]\d)(?:\D|$)/
  );

  return match
    ? `${match[1].padStart(2, "0")}:${match[2]}`
    : null;
}

// ==========================================
// PRAYER TIMES CACHE
// ==========================================

function loadCachedPrayerTimes() {
  try {
    const saved = JSON.parse(
      localStorage.getItem(PRAYER_TIMES_KEY) || "null"
    );

    return (
      saved?.date === prayerDay() &&
      saved.fajr &&
      saved.dhuhr &&
      saved.asr &&
      saved.maghrib
    )
      ? saved
      : null;
  } catch {
    return null;
  }
}

// ==========================================
// PRAYER TIMES API
// ==========================================

async function loadPrayerTimes() {
  if (prayerTimesLoading) return;

  prayerTimesLoading = true;

  const controller = new AbortController();

  const timeout = window.setTimeout(
    () => controller.abort(),
    8000
  );

  try {
    const now = new Date();
    const parts = getPrayerDateParts(now);

    const date =
      `${parts.day}-${parts.month}-${parts.year}`;

    const url = new URL(
      `${PRAYER_TIMES_API}/${date}`
    );

    url.search = new URLSearchParams({
      city: "Siegen",
      country: "Germany",
      method: "4",
      school: "0",
    });

    const response = await fetch(url, {
      cache: "no-store",
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(
        "Gebetszeiten sind nicht erreichbar."
      );
    }

    const result = await response.json();

    const timings =
      result?.code === 200
        ? result.data?.timings
        : null;

    const times = {
      fajr: parsePrayerClock(timings?.Fajr),
      dhuhr: parsePrayerClock(timings?.Dhuhr),
      asr: parsePrayerClock(timings?.Asr),
      maghrib: parsePrayerClock(timings?.Maghrib),
    };

    if (
      Object.values(times).some(
        (time) => !time
      )
    ) {
      throw new Error(
        "Gebetszeiten wurden nicht gefunden."
      );
    }

    prayerTimes = {
      date: prayerDay(now),
      ...times,
      fetchedAt: Date.now(),
    };

    try {
      localStorage.setItem(
        PRAYER_TIMES_KEY,
        JSON.stringify(prayerTimes)
      );
    } catch {
      // Gebetszeiten bleiben im Speicher.
    }
  } catch {
    prayerTimes = loadCachedPrayerTimes();
  } finally {
    window.clearTimeout(timeout);

    prayerTimesLoading = false;

    renderPrayerCountdown();
  }
}

// ==========================================
// PRAYER WINDOWS
// ==========================================

function getPrayerWindow(routine) {
  if (
    !prayerTimes ||
    prayerTimes.date !== prayerDay()
  ) {
    return null;
  }

  if (routine === "morning") {
    return {
      start: timeToMinutes(prayerTimes.fajr),
      end: timeToMinutes(prayerTimes.dhuhr) - 15,
    };
  }

  if (routine === "evening") {
    return {
      start: timeToMinutes(prayerTimes.asr),
      end: timeToMinutes(prayerTimes.maghrib) - 15,
    };
  }

  return null;
}

function isWithinPrayerWindow(routine) {
  const window = getPrayerWindow(routine);

  if (!window) return false;

  const now = currentPrayerMinutes();

  return (
    now >= window.start &&
    now < window.end
  );
}

// ==========================================
// AUTOMATIC DEFAULT TAB
// ==========================================

function getDefaultTab() {
  if (
    !prayerTimes ||
    prayerTimes.date !== prayerDay()
  ) {
    return "prayer";
  }

  if (isWithinPrayerWindow("morning")) {
    return "morning";
  }

  if (isWithinPrayerWindow("evening")) {
    return "evening";
  }

  return "prayer";
}

function selectDefaultTab() {
  if (tabManuallySelected) return;

  const defaultTab = getDefaultTab();

  if (defaultTab === "morning") {
    selectRoutine("morning");
  } else if (defaultTab === "evening") {
    selectRoutine("evening");
  } else {
    selectPrayerTimes();
  }
}

// ==========================================
// PRAYER COUNTDOWN
// ==========================================

function renderPrayerCountdown() {
  if (
    !elements.windowCountdownLabel ||
    !elements.windowCountdownValue
  ) {
    return;
  }

  if (
    !prayerTimes ||
    prayerTimes.date !== prayerDay()
  ) {
    elements.windowCountdownLabel.textContent =
      "تعذر تحميل مواقيت الصلاة";

    elements.windowCountdownValue.textContent =
      "--:--";

    return;
  }

  const isMorning = activeRoutine === "morning";

  const prayerWindow = getPrayerWindow(
    activeRoutine
  );

  if (!prayerWindow) return;

  const now = currentPrayerMinutes();

  if (
    now < prayerWindow.start ||
    now >= prayerWindow.end
  ) {
    elements.windowCountdownLabel.textContent =
      isMorning
        ? "ليس وقت أذكار الصباح"
        : "ليس وقت أذكار المساء";

    elements.windowCountdownValue.textContent =
      "--:--";

    return;
  }

  const remainingMinutes =
    prayerWindow.end - now;

  const hoursLeft = String(
    Math.floor(remainingMinutes / 60)
  ).padStart(2, "0");

  const minutesLeft = String(
    remainingMinutes % 60
  ).padStart(2, "0");

  elements.windowCountdownLabel.textContent =
    isMorning
      ? "المتبقي حتى خروج وقت أذكار الصباح"
      : "المتبقي حتى خروج وقت أذكار المساء";

  elements.windowCountdownValue.textContent =
    `${hoursLeft}:${minutesLeft}`;
}

// ==========================================
// PROGRESS
// ==========================================

function loadProgress() {
  try {
    const saved = JSON.parse(
      localStorage.getItem(STORAGE_KEY) || "null"
    );

    if (!saved || saved.date !== sessionDate) {
      restoreActiveProgress();
      return;
    }

    if (saved.sessions) {
      for (const routine of [
        "morning",
        "evening",
      ]) {
        const list = routines[routine];
        const state = saved.sessions[routine];

        if (!state || !list.length) continue;

        const index = Math.max(
          0,
          Math.min(
            Number(state.currentIndex) || 0,
            list.length - 1
          )
        );

        progressByRoutine[routine] = {
          currentIndex: index,
          count: Math.max(
            0,
            Math.min(
              Number(state.count) || 0,
              list[index].targetCount
            )
          ),
        };
      }
    } else if (routines.morning.length) {
      // Kompatibilität mit alter Version.
      const index = Math.max(
        0,
        Math.min(
          Number(saved.currentIndex) || 0,
          routines.morning.length - 1
        )
      );

      progressByRoutine.morning = {
        currentIndex: index,
        count: Math.max(
          0,
          Math.min(
            Number(saved.count) || 0,
            routines.morning[index].targetCount
          )
        ),
      };
    }
  } catch {
    progressByRoutine = {
      morning: { currentIndex: 0, count: 0 },
      evening: { currentIndex: 0, count: 0 },
    };
  }

  restoreActiveProgress();
}

function rememberActiveProgress() {
  progressByRoutine[activeRoutine] = {
    currentIndex,
    count,
  };
}

function restoreActiveProgress() {
  const state = progressByRoutine[activeRoutine];

  currentIndex = state.currentIndex;
  count = state.count;
}

function saveProgress() {
  rememberActiveProgress();

  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        date: sessionDate,
        sessions: progressByRoutine,
      })
    );
  } catch {
    // App bleibt auch ohne Storage nutzbar.
  }
}

// ==========================================
// RENDER
// ==========================================

function render() {
  const athkar = routines[activeRoutine];

  if (!athkar.length) return;

  const dhikr = athkar[currentIndex];

  const complete =
    count >= dhikr.targetCount;

  const isMorning =
    activeRoutine === "morning";

  const showingPrayerTimes =
    elements.shell.dataset.view === "prayer";

  if (!showingPrayerTimes) {
    elements.routineEyebrow.textContent =
      isMorning
        ? "MORGENROUTINE"
        : "ABENDROUTINE";

    elements.routineTitle.textContent =
      isMorning
        ? "Deine Morgen-Athkar"
        : "Deine Abend-Athkar";

    document.title =
      isMorning
        ? "Morgen-Athkar"
        : "Abend-Athkar";
  }

  elements.morningTab.setAttribute(
    "aria-selected",
    String(!showingPrayerTimes && isMorning)
  );

  elements.eveningTab.setAttribute(
    "aria-selected",
    String(!showingPrayerTimes && !isMorning)
  );

  elements.prayerTab.setAttribute(
    "aria-selected",
    String(showingPrayerTimes)
  );

  renderPrayerCountdown();

  elements.page.textContent =
    `${currentIndex + 1} / ${athkar.length}`;

  elements.progress.style.width =
    `${((currentIndex + 1) / athkar.length) * 100}%`;

  elements.progressTrack.setAttribute(
    "aria-valuemax",
    String(athkar.length)
  );

  elements.progressTrack.setAttribute(
    "aria-valuenow",
    String(currentIndex + 1)
  );

  elements.arabic.textContent =
    dhikr.arabicText;

  elements.count.innerHTML =
    `${count} <span>/ ${dhikr.targetCount}</span>`;

  elements.previous.disabled =
    currentIndex === 0;

  elements.next.disabled =
    currentIndex === athkar.length - 1;

  elements.countButton.textContent =
    complete
      ? (
          currentIndex < athkar.length - 1
            ? "Weiter"
            : "Fertig ✓"
        )
      : "+ Zählen";

  elements.hint.textContent =
    complete
      ? "Abgeschlossen · Weiter zur nächsten Erinnerung"
      : "In Ruhe lesen und anschließend zählen";

  elements.date.textContent =
    new Intl.DateTimeFormat("de-DE", {
      weekday: "short",
      day: "numeric",
      month: "short",
    }).format(new Date());

  window.requestAnimationFrame(fitArabicText);

  saveProgress();
}

// ==========================================
// FIT ARABIC TEXT
// ==========================================

function fitArabicText() {
  const text = elements.arabic;
  const viewport = elements.readingViewport;

  if (!viewport.clientHeight) return;

  text.style.fontSize = "";

  let size = Number.parseFloat(
    getComputedStyle(text).fontSize
  );

  while (
    text.scrollHeight > viewport.clientHeight &&
    size > 11
  ) {
    size -= 1;
    text.style.fontSize = `${size}px`;
  }
}

// ==========================================
// HAPTIC FEEDBACK
// ==========================================

function vibrateOnPageChange() {
  if (
    typeof navigator.vibrate === "function"
  ) {
    navigator.vibrate(45);
  }
}

function giveCountCompleteFeedback() {
  if (
    typeof navigator.vibrate === "function"
  ) {
    navigator.vibrate(45);
  }

  const AudioContextClass =
    window.AudioContext ||
    window.webkitAudioContext;

  if (!AudioContextClass) return;

  try {
    audioContext ??= new AudioContextClass();

    if (audioContext.state === "suspended") {
      audioContext.resume().catch(() => {});
    }

    const startAt = audioContext.currentTime;

    const oscillator =
      audioContext.createOscillator();

    const volume =
      audioContext.createGain();

    oscillator.type = "sine";

    oscillator.frequency.setValueAtTime(
      660,
      startAt
    );

    volume.gain.setValueAtTime(
      0.0001,
      startAt
    );

    volume.gain.exponentialRampToValueAtTime(
      0.035,
      startAt + 0.01
    );

    volume.gain.exponentialRampToValueAtTime(
      0.0001,
      startAt + 0.1
    );

    oscillator.connect(volume);
    volume.connect(audioContext.destination);

    oscillator.start(startAt);
    oscillator.stop(startAt + 0.11);
  } catch {
    // Zählen funktioniert auch ohne Ton.
  }
}

// ==========================================
// ATHKAR NAVIGATION
// ==========================================

function showDhikr(index) {
  const athkar = routines[activeRoutine];

  if (
    index < 0 ||
    index >= athkar.length
  ) {
    return;
  }

  const pageChanged =
    index !== currentIndex;

  currentIndex = index;
  count = 0;

  if (pageChanged) {
    vibrateOnPageChange();
  }

  render();
}

function countOrAdvance() {
  const athkar = routines[activeRoutine];

  if (!athkar.length) return;

  const dhikr = athkar[currentIndex];

  if (count < dhikr.targetCount) {
    count += 1;

    if (count === dhikr.targetCount) {
      giveCountCompleteFeedback();
    }
  } else if (
    currentIndex < athkar.length - 1
  ) {
    currentIndex += 1;
    count = 0;
  }

  render();
}

// ==========================================
// TAB NAVIGATION
// ==========================================

function selectRoutine(routine) {
  if (!routines[routine]?.length) return;

  if (
    routine === activeRoutine &&
    elements.shell.dataset.view !== "prayer"
  ) {
    render();
    return;
  }

  rememberActiveProgress();

  activeRoutine = routine;

  elements.shell.dataset.view = "routine";
  elements.prayerPanel.hidden = true;

  restoreActiveProgress();

  render();
}

function selectPrayerTimes() {
  elements.shell.dataset.view = "prayer";
  elements.prayerPanel.hidden = false;

  elements.routineEyebrow.textContent =
    "MAWAQIT · SIEGEN";

  elements.routineTitle.textContent =
    "Gebetszeiten";

  document.title =
    "Gebetszeiten · Siegen";

  elements.morningTab.setAttribute(
    "aria-selected",
    "false"
  );

  elements.eveningTab.setAttribute(
    "aria-selected",
    "false"
  );

  elements.prayerTab.setAttribute(
    "aria-selected",
    "true"
  );
}

// ==========================================
// CLOCKS
// ==========================================

function updateClocks() {
  const now = new Date();

  const today = localDay(now);

  // Fortschritt täglich zurücksetzen.
  if (today !== sessionDate) {
    sessionDate = today;

    progressByRoutine = {
      morning: {
        currentIndex: 0,
        count: 0,
      },
      evening: {
        currentIndex: 0,
        count: 0,
      },
    };

    currentIndex = 0;
    count = 0;

    render();
  }

  // Neue Gebetszeiten bei Datumswechsel.
  const todayInSiegen = prayerDay(now);

  if (todayInSiegen !== lastPrayerDay) {
    lastPrayerDay = todayInSiegen;

    prayerTimes = loadCachedPrayerTimes();

    loadPrayerTimes();
  }

  const elapsedMinutes = Math.floor(
    (Date.now() - sessionStartedAt) / 60_000
  );

  const hours = String(
    Math.floor(elapsedMinutes / 60)
  ).padStart(2, "0");

  const minutes = String(
    elapsedMinutes % 60
  ).padStart(2, "0");

  elements.session.textContent =
    `Sitzung  ${hours}:${minutes}`;

  elements.clock.textContent =
    new Intl.DateTimeFormat("de-DE", {
      hour: "2-digit",
      minute: "2-digit",
    }).format(now);

  renderPrayerCountdown();
}

// ==========================================
// PERSIST PROGRESS
// ==========================================

function persistProgressForCurrentDay() {
  const today = localDay();

  if (today !== sessionDate) {
    sessionDate = today;

    progressByRoutine = {
      morning: {
        currentIndex: 0,
        count: 0,
      },
      evening: {
        currentIndex: 0,
        count: 0,
      },
    };

    currentIndex = 0;
    count = 0;

    render();
    return;
  }

  saveProgress();
}

// ==========================================
// EVENT LISTENERS
// ==========================================

elements.countButton.addEventListener(
  "click",
  countOrAdvance
);

// Morgen-Athkar manuell auswählen.
elements.morningTab.addEventListener(
  "click",
  () => {
    tabManuallySelected = true;

    selectRoutine("morning");
  }
);

// Abend-Athkar manuell auswählen.
elements.eveningTab.addEventListener(
  "click",
  () => {
    tabManuallySelected = true;

    selectRoutine("evening");
  }
);

// Gebetszeiten manuell auswählen.
elements.prayerTab.addEventListener(
  "click",
  () => {
    tabManuallySelected = true;

    selectPrayerTimes();
  }
);

// Theme initialisieren.
applyTheme(
  document.documentElement.dataset.theme
);

// Theme wechseln.
elements.themeButton.addEventListener(
  "click",
  () => {
    const nextTheme =
      document.documentElement.dataset.theme === "dark"
        ? "original"
        : "dark";

    applyTheme(nextTheme, true);
  }
);

// Screen Wake Lock.
elements.screenWakeButton.disabled =
  !window.isSecureContext ||
  !navigator.wakeLock?.request;

if (elements.screenWakeButton.disabled) {
  elements.screenWakeButton.title =
    "Bildschirm wach halten wird von diesem Browser nicht unterstützt";
}

elements.screenWakeButton.addEventListener(
  "click",
  toggleScreenWakeLock
);

// Auf Bildschirm tippen zum Zählen.
elements.readingArea.addEventListener(
  "click",
  (event) => {
    if (
      elements.shell.dataset.view === "prayer"
    ) {
      return;
    }

    if (event.target.closest("button, a")) {
      return;
    }

    countOrAdvance();
  }
);

// Vorheriger Dhikr.
elements.previous.addEventListener(
  "click",
  () => showDhikr(currentIndex - 1)
);

// Nächster Dhikr.
elements.next.addEventListener(
  "click",
  () => showDhikr(currentIndex + 1)
);

// Reset.
document.querySelector("#reset-button")
  .addEventListener(
    "click",
    () => showDhikr(0)
  );

// Resize.
window.addEventListener(
  "resize",
  fitArabicText
);

// Fortschritt sichern.
window.addEventListener(
  "pagehide",
  persistProgressForCurrentDay
);

// ==========================================
// APP VISIBILITY / RESUME
// ==========================================

document.addEventListener(
  "visibilitychange",
  () => {
    if (
      document.visibilityState === "hidden"
    ) {
      persistProgressForCurrentDay();
      return;
    }

    // App wurde erneut geöffnet.
    // Automatische Auswahl wieder aktivieren.
    tabManuallySelected = false;

    updateClocks();

    // Vorhandene Gebetszeiten verwenden.
    if (
      !prayerTimes ||
      prayerTimes.date !== prayerDay()
    ) {
      prayerTimes = loadCachedPrayerTimes();
    }

    selectDefaultTab();

    // Gebetszeiten aktualisieren.
    loadPrayerTimes().then(() => {
      if (
        !tabManuallySelected &&
        document.visibilityState === "visible"
      ) {
        selectDefaultTab();
      }
    });

    requestScreenWakeLock();
  }
);

// ==========================================
// APP START
// ==========================================

async function start() {
  try {
    const response = await fetch("./athkar.json");

    if (!response.ok) {
      throw new Error(
        "Athkar konnten nicht geladen werden."
      );
    }

    const data = await response.json();

    routines = Array.isArray(data)
      ? {
          morning: data,
          evening: data,
        }
      : data;

    if (
      !Array.isArray(routines.morning) ||
      !Array.isArray(routines.evening)
    ) {
      throw new Error(
        "Morgen- oder Abend-Athkar fehlen."
      );
    }

    // Gespeicherten Fortschritt laden.
    loadProgress();

    // Gespeicherte Gebetszeiten laden.
    prayerTimes = loadCachedPrayerTimes();

    // Passenden Tab automatisch auswählen.
    selectDefaultTab();

    // Oberfläche rendern.
    render();

    // Uhren aktualisieren.
    updateClocks();

    // Aktuelle Gebetszeiten abrufen.
    loadPrayerTimes().then(() => {
      if (
        !tabManuallySelected &&
        document.visibilityState === "visible"
      ) {
        selectDefaultTab();
      }
    });

    // Uhr jede Sekunde aktualisieren.
    window.setInterval(
      updateClocks,
      1000
    );

    // Gebetszeiten alle 15 Minuten aktualisieren.
    window.setInterval(
      () => {
        if (
          document.visibilityState === "visible"
        ) {
          loadPrayerTimes();
        }
      },
      15 * 60 * 1000
    );

    // Service Worker registrieren.
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker
        .register("./service-worker.js")
        .catch(() => {});
    }
  } catch (error) {
    console.error(
      "Fehler beim Starten der App:",
      error
    );

    elements.arabic.textContent =
      "Die Erinnerungen konnten nicht geladen werden. Bitte lade die Seite erneut.";

    elements.countButton.disabled = true;
    elements.previous.disabled = true;
    elements.next.disabled = true;
  }
}

// ==========================================
// INITIALIZE
// ==========================================

start();
