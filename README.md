# Theme Applier (Xiaomi / Redmi / POCO)

Applies the bundled iPhone 18 style .mtz (or any .mtz you pick) through the system Themes app.

## Build without installing anything (GitHub)
1. Create a new GitHub repo and upload everything in this folder (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab -> "Build APK" -> **Run workflow**.
3. When it finishes, download the artifact **ThemeApplier-debug-apk**, unzip, install `app-debug.apk`.

## Build with Android Studio
Open this folder, wait for Gradle sync, press Run (or Build > Build APK).

## Use
Open the app -> "Apply iPhone 18 theme". If HyperOS reverts the theme, install Zyper.
