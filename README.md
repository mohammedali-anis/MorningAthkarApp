# Morning Athkar

Morning Athkar hilft dir dabei, die Bittgebete für den Morgen und den Abend zu lesen und zu zählen. Die Anwendung gibt es als WebApp und als Desktop-App für Windows.

## WebApp aufrufen

Die WebApp kann im Browser geöffnet werden. Auf dem Smartphone lässt sie sich auch zum Startbildschirm hinzufügen und wie eine App verwenden.

**WebApp:** [Morning Athkar öffnen](https://mohammedali-anis.github.io/MorningAthkarApp/)

Die WebApp enthält:

- Morgen- und Abend-Athkar mit arabischem Text und Zähler
- einen Fortschritt, der im Browser gespeichert wird und am nächsten Tag neu beginnt
- Gebetszeiten für Siegen sowie Hinweise zum Zeitraum der Morgen- und Abend-Athkar
- einen Hell- und einen Dunkelmodus
- eine Option, den Bildschirm beim Lesen eingeschaltet zu lassen

Der Fortschritt wird nur in dem Browser auf dem jeweiligen Gerät gespeichert. Er wird nicht zwischen Geräten übertragen.

### WebApp auf dem Smartphone installieren

1. Öffne den Link oben im Browser deines Smartphones.
2. Öffne das Browser-Menü.
3. Wähle **„Zum Startbildschirm hinzufügen“** oder **„App installieren“**.

### WebApp veröffentlichen

Die WebApp liegt im Ordner `docs/`. GitHub Pages kann diesen Ordner direkt veröffentlichen:

1. Öffne im Repository **Settings → Pages**.
2. Wähle bei **Build and deployment** die Option **Deploy from a branch**.
3. Wähle den Branch `main` und den Ordner `/docs`.
4. Klicke auf **Save**.

Nach dem Veröffentlichen zeigt GitHub Pages dort die Adresse der WebApp an. Änderungen im Ordner `docs/` werden nach dem Push automatisch veröffentlicht. Ein GitHub Release ist dafür nicht erforderlich.

## Desktop-App für Windows

Die Desktop-App basiert auf Java 21, JavaFX und Maven. Sie kann mit dem Skript `build-and-create-desktop-shortcut.ps1` gebaut werden. Das Skript erstellt die Windows-App und legt eine Verknüpfung auf dem Desktop an.

Benötigt werden:

- Windows
- ein JDK 21
- Maven

Öffne PowerShell im Projektordner und starte:

```powershell
./build-and-create-desktop-shortcut.ps1
```

Die erstellte App liegt anschließend im Ordner `dist/`. Der Build-Ordner und die fertige App werden nicht in Git eingecheckt. Wenn du die Desktop-App als Download anbieten möchtest, erstelle eine ZIP-Datei aus dem App-Ordner und hänge sie an ein GitHub Release an.

Für andere Systeme kann die Java-Anwendung mit Maven gebaut werden:

```bash
mvn clean package
```

## Projektordner

- `docs/` – Dateien der WebApp
- `src/` – Java-Quellcode der Desktop-App und Athkar-Inhalte
- `assets/` – App-Symbole
- `build-and-create-desktop-shortcut.ps1` – Windows-Build und Desktop-Verknüpfung
- `build.sh` – Maven-Build für Linux und macOS

## Hinweis

Gebetszeiten und Inhalte können sich ändern. Bitte prüfe wichtige Angaben bei Bedarf zusätzlich bei deiner Moschee.
