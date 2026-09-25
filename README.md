# Bill’s Progress Importer v2.1

This personal Android app reads the Health Connect categories the user allows and prepares a local JSON file for the owner-private production Bill’s Progress dashboard. It does not ask for a password, passkey, site bypass token, or API key. The user signs in to the Site in Chrome with their existing passkey and uploads the JSON file in Connections → Import readings JSON. No health data is transmitted by the app; the user controls where the file is saved and when it is uploaded.

The v2.1 application ID is `com.billlicht.progress.v21`, separate from the original importer, v2, and TEST. Both Sites remain isolated. On the phone, grant Health Connect permission; tap Prepare 30-day file or Prepare 90-day history; save the file through Android’s document picker; then select that file on the production dashboard in Chrome. Only 29 or 90 days can be read according to Health Connect history permission. Matching record IDs replace matching readings on repeated uploads. Keep the saved health JSON private and remove unneeded copies.

This browser upload flow requires a manual upload each time; it does not offer background syncing. Keep the original importer running if its established sign-in still works. Stop automatic sync in the v2 app if it is installed and cannot sign in. No passkey is entered inside Android WebView.

The file contains weight, body composition, blood pressure, exercise, daily activity and sleep data in the dashboard’s normalized schema. Sleep keeps `sleep` duration and adds light, deep, REM, awake, time in bed, efficiency, bedtime and wake time when Health Connect supplies them. No skeletal muscle value is inferred from lean mass.

Build: JDK 17, Android SDK platform 36, build-tools 35, Gradle 8.13; run `gradle :app:assembleDebug`. CI builds a debug-signed APK from `main` or `passkey-browser-upload`. Because signing is not persisted, v2.1 uses a distinct application ID. This is an owner-use test build, not a release-signed app.
