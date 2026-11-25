
const STORAGE_KEY = "morning-athkar-progress-v1";
const THEME_KEY = "morning-athkar-theme-v1";
const PRAYER_TIMES_KEY = "morning-athkar-city-times-v2";
const PRAYER_TIMES_API = "https://api.aladhan.com/v1/timingsByCity";
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

let routines = { morning: [], evening: [] };
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
let userSelectedView = false;
let keepScreenAwake = false;
let screenWakeLock = null;
let screenWakeLockRequest = null;


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
  
  elements.themeIcon.textContent =
      String.fromCodePoint(dark ? 0x2600 : 0x263e);
  
  document.querySelector(
      'meta[name="theme-color"]'
  ).content = dark ? "#080d0b" : "#101815";
  
  if (!save) return;
  
  try {
    localStorage.setItem(
        THEME_KEY,
        dark ? "dark" : "original"
    );
  } catch {
    // The selected theme stays active
    // until the page is closed.
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
      screenWakeLockRequest
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
// DATE
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
    
    return saved?.date === localDay() &&
    saved.fajr &&
    saved.dhuhr &&
    saved.asr &&
    saved.maghrib
        ? saved
        : null;
  } catch {
    return null;
  }
}


// ==========================================
// LOAD PRAYER TIMES
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
    
    const date =
        `${String(now.getDate()).padStart(2, "0")}-` +
        `${String(now.getMonth() + 1).padStart(2, "0")}-` +
        `${now.getFullYear()}`;
    
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
      date: localDay(),
      ...times,
      fetchedAt: Date.now(),
    };
    
    try {
      localStorage.setItem(
          PRAYER_TIMES_KEY,
          JSON.stringify(prayerTimes)
      );
    } catch {
      // Current-day countdown still works
      // without cached prayer times.
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
// PRAYER COUNTDOWN
// ==========================================

function renderPrayerCountdown() {
  if (
      !elements.windowCountdownLabel ||
      !elements.windowCountdownValue
  ) {
    return;
  }
  
  const windowTimes = routineWindow(activeRoutine);
  
  if (!windowTimes) {
    elements.windowCountdownLabel.textContent =
        "تعذر تحميل مواقيت الصلاة";
    
    elements.windowCountdownValue.textContent =
        "--";
    
    return;
  }
  
  const isMorning = activeRoutine === "morning";
  const { start, end } = windowTimes;
  
  const now = Date.now();
  
  if (
      now < start.getTime() ||
      now >= end.getTime()
  ) {
    elements.windowCountdownLabel.textContent =
        isMorning
            ? "ليس وقت أذكار الصباح"
            : "ليس وقت أذكار المساء";
    
    elements.windowCountdownValue.textContent =
        "--";
    
    return;
  }
  
  const remainingMinutes = Math.ceil(
      (end.getTime() - now) / 60_000
  );
  
  elements.windowCountdownLabel.textContent =
      isMorning
          ? "المتبقي حتى خروج وقت أذكار الصباح"
          : "المتبقي حتى خروج وقت أذكار المساء";
  
  elements.windowCountdownValue.textContent =
      formatDuration(remainingMinutes);
}

function routineWindow(routine, now = new Date()) {
  if (!prayerTimes || prayerTimes.date !== localDay(now)) return null;
  
  const startTime = routine === "morning" ? prayerTimes.fajr : prayerTimes.asr;
  const endTime = routine === "morning" ? prayerTimes.dhuhr : prayerTimes.maghrib;
  if (!startTime || !endTime) return null;
  
  const [startHours, startMinutes] = startTime.split(":").map(Number);
  const [endHours, endMinutes] = endTime.split(":").map(Number);
  const start = new Date(now);
  const end = new Date(now);
  start.setHours(startHours, startMinutes, 0, 0);
  end.setHours(endHours, endMinutes - 15, 0, 0);
  return { start, end };
}

function formatDuration(totalMinutes) {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${hours}h ${minutes}m`;
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
      // Carry forward progress saved by
      // the previous single-list version.
      
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
    // The app remains usable if browser
    // storage is disabled or full.
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
  
  // ========================================
  // NEU: iPhone-Haptik aktualisieren
  // Nur für den letzten Zählschritt.
  // ========================================
  updateIOSHapticOverlay();
  
  saveProgress();
}


// ==========================================
// ARABIC TEXT
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
// VIBRATION
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
    // Counting still works if sound
    // is unavailable or blocked.
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
// CLOCK
// ==========================================

function updateClocks() {
  const now = new Date();
  const today = localDay(now);
  
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
  
  const elapsedMinutes = Math.floor(
      (Date.now() - sessionStartedAt) / 60_000
  );
  
  elements.session.textContent =
      `Sitzung  ${formatDuration(elapsedMinutes)}`;
  
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
// COUNT BUTTON
// ==========================================

elements.countButton.addEventListener(
    "click",
    countOrAdvance
);


// ==========================================
// IPHONE HAPTIC FEEDBACK
// ==========================================

// Erkennen, ob die App auf iOS läuft.
const isIOS =
    /iPhone|iPad|iPod/.test(navigator.userAgent) ||
    (
        navigator.platform === "MacIntel" &&
        navigator.maxTouchPoints > 1
    );

// Unsichtbarer nativer iOS-Switch.
const iosHapticCount = document.querySelector(
    "#ios-haptic-count"
);

/**
 * Aktiviert den iPhone-Switch nur dann,
 * wenn der nächste Klick den aktuellen
 * Dhikr vollständig abschließt.
 *
 * Beispiel:
 *
 * 0/3 -> Switch aus
 * 1/3 -> Switch aus
 * 2/3 -> Switch an
 * 3/3 -> Switch aus
 *
 * Damit wird die native Haptik nur
 * beim letzten Zählschritt ausgelöst.
 */
function updateIOSHapticOverlay() {
  if (!iosHapticCount) return;
  
  const dhikr =
      routines[activeRoutine]?.[currentIndex];
  
  // Prüfen, ob die nächste Wiederholung
  // den aktuellen Dhikr abschließt.
  const isLastCount =
      dhikr &&
      count === Number(dhikr.targetCount) - 1;
  
  // Switch nur für die letzte
  // Wiederholung aktivieren.
  const shouldVibrate =
      isIOS &&
      isLastCount &&
      elements.shell.dataset.view !== "prayer" &&
      !elements.countButton.disabled;
  
  iosHapticCount.hidden = !shouldVibrate;
  
  // Switch zurücksetzen, damit er beim
  // nächsten Dhikr erneut aktiviert
  // werden kann.
  if (!shouldVibrate) {
    iosHapticCount.checked = false;
  }
}

// Haptik-Switch Klick verarbeiten.
if (isIOS && iosHapticCount) {
  iosHapticCount.addEventListener(
      "click",
      (event) => {
        // Doppeltes Zählen verhindern.
        event.stopPropagation();
        
        if (!elements.countButton.disabled) {
          countOrAdvance();
        }
      }
  );
}


// ==========================================
// TAB NAVIGATION
// ==========================================

function selectRoutine(routine) {
  if (
      (
          routine === activeRoutine &&
          elements.shell.dataset.view !== "prayer"
      ) ||
      !routines[routine]?.length
  ) {
    return;
  }
  
  rememberActiveProgress();
  
  activeRoutine = routine;
  
  elements.shell.dataset.view = "routine";
  elements.prayerPanel.hidden = true;
  
  restoreActiveProgress();
  
  render();
}

elements.morningTab.addEventListener(
    "click",
    () => {
      userSelectedView = true;
      selectRoutine("morning");
    }
);

elements.eveningTab.addEventListener(
    "click",
    () => {
      userSelectedView = true;
      selectRoutine("evening");
    }
);

function selectPrayerView() {
  elements.shell.dataset.view = "prayer";
  elements.prayerPanel.hidden = false;
  
  elements.routineEyebrow.textContent =
      "MAWAQIT · SIEGEN";
  
  elements.routineTitle.textContent =
      "Gebetszeiten";
  
  document.title =
      "Gebetszeiten · Siegen";
  
  elements.morningTab.setAttribute("aria-selected", "false");
  elements.eveningTab.setAttribute("aria-selected", "false");
  elements.prayerTab.setAttribute("aria-selected", "true");
  updateIOSHapticOverlay();
}

function selectDefaultView() {
  const now = new Date();
  const morning = routineWindow("morning", now);
  if (morning && now >= morning.start && now < morning.end) {
    selectRoutine("morning");
    return;
  }
  
  const evening = routineWindow("evening", now);
  if (evening && now >= evening.start && now < evening.end) {
    selectRoutine("evening");
    return;
  }
  
  selectPrayerView();
}

elements.prayerTab.addEventListener(
    "click",
    () => {
      userSelectedView = true;
      selectPrayerView();
    }
);


// ==========================================
// THEME EVENTS
// ==========================================

applyTheme(
    document.documentElement.dataset.theme
);

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


// ==========================================
// WAKE LOCK EVENTS
// ==========================================

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


// ==========================================
// READING AREA EVENTS
// ==========================================

elements.readingArea.addEventListener(
    "click",
    (event) => {
      if (
          elements.shell.dataset.view === "prayer"
      ) {
        return;
      }
      
      if (
          event.target.closest("button, a")
      ) {
        return;
      }
      
      countOrAdvance();
    }
);


// ==========================================
// NAVIGATION EVENTS
// ==========================================

elements.previous.addEventListener(
    "click",
    () => showDhikr(currentIndex - 1)
);

elements.next.addEventListener(
    "click",
    () => showDhikr(currentIndex + 1)
);

document.querySelector("#reset-button")
    .addEventListener(
        "click",
        () => showDhikr(0)
    );

window.addEventListener(
    "resize",
    fitArabicText
);

window.addEventListener(
    "pagehide",
    persistProgressForCurrentDay
);

document.addEventListener(
    "visibilitychange",
    () => {
      if (
          document.visibilityState === "hidden"
      ) {
        persistProgressForCurrentDay();
      } else {
        updateClocks();
        requestScreenWakeLock();
      }
    }
);


// ==========================================
// GITHUB DEPLOYMENT DATE
// ==========================================

async function loadDeploymentDate() {
  const element = document.querySelector(
      "#deployment-date"
  );
  
  if (!element) return;
  
  const url =
      "https://api.github.com/repos/mohammedali-anis/MorningAthkarApp/commits?sha=main&path=docs&per_page=1";
  
  const cacheKey =
      "morning-athkar-last-update";
  
  function formatDate(timestamp) {
    const parts = Object.fromEntries(
        new Intl.DateTimeFormat("de-DE", {
          timeZone: "Europe/Berlin",
          day: "2-digit",
          month: "2-digit",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
          hourCycle: "h23",
        })
            .formatToParts(new Date(timestamp))
            .map(({ type, value }) => [type, value])
    );
    
    return (
        `${parts.day}.${parts.month}.${parts.year}` +
        ` - ${parts.hour}:${parts.minute}`
    );
  }
  
  try {
    const response = await fetch(url, {
      cache: "no-store",
      headers: {
        Accept: "application/vnd.github+json",
      },
    });
    
    if (!response.ok) {
      throw new Error(
          `GitHub API: ${response.status}`
      );
    }
    
    const commits = await response.json();
    
    const timestamp =
        commits[0]?.commit?.committer?.date;
    
    if (!timestamp) {
      throw new Error(
          "Kein Commit-Datum gefunden"
      );
    }
    
    const formatted = formatDate(timestamp);
    
    element.textContent =
        `Letztes App-Update: ${formatted}`;
    
    try {
      localStorage.setItem(
          cacheKey,
          timestamp
      );
    } catch {
      // Speicherung optional.
    }
  } catch (error) {
    console.warn(
        "App-Update konnte nicht geladen werden:",
        error
    );
    
    let cached = null;
    
    try {
      cached = localStorage.getItem(cacheKey);
    } catch {
      // Kein Cache verfügbar.
    }
    
    element.textContent = cached
        ? `Letztes App-Update: ${formatDate(cached)}`
        : "Letztes App-Update: Nicht verfügbar";
  }
}


// ==========================================
// APP START
// ==========================================

async function start() {
  try {
    const response = await fetch(
        "./athkar.json"
    );
    
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
    
    loadProgress();
    
    prayerTimes = loadCachedPrayerTimes();
    selectDefaultView();
    render();
    
    updateClocks();
    
    loadDeploymentDate();
    
    loadPrayerTimes().then(() => {
      if (!userSelectedView) selectDefaultView();
    });
    
    window.setInterval(
        updateClocks,
        1000
    );
    
    window.setInterval(
        loadPrayerTimes,
        15 * 60 * 1000
    );
    
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker
          .register("./service-worker.js")
          .catch(() => {});
    }
  } catch {
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
