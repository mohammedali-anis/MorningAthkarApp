# Morning Athkar

Morning Athkar is a JavaFX desktop app for reading and counting the morning adhkar. A mobile-friendly, installable web app lives in [`docs/`](docs/). The desktop app and PWA use the same Arabic text and repetition counts.

## Mobile app

The web app works in a mobile browser and can be added to the phone's home screen. It keeps the current day's position and count in that browser's local storage. Progress is not synchronized between devices.

To publish it with GitHub Pages, open the repository's **Settings → Pages**, select **Deploy from a branch**, choose the default branch and the `/docs` folder, then save. GitHub will show the published address on that page. Pages serves static files, so the mobile app does not need the Oracle VM.

## Desktop app

The desktop app is a Java 21 / JavaFX project built with Maven. Run `./build.sh` or `mvn clean package` to build it. On Windows, `build-and-create-desktop-shortcut.ps1` also creates a packaged app image and a desktop shortcut.

Athkar content is kept in `src/MorningAthkarData.java`. Generated installers, build output, and packaged runtimes are ignored by Git; local copies are left in the workspace.
