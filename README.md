# Bill’s Progress Importer v2

Personal Android companion for the owner-private Bill’s Progress Site. Reads approved Health Connect data and uploads normalized measurements over HTTPS using the dashboard session established inside the app. No Health Connect writes, analytics or advertising.

This v0.2.0 build uses application ID `com.billlicht.progress.v2` and the production URL `https://bill-health-progress.blicht74.chatgpt.site`. It can install beside the v0.1.3 production importer and the separate TEST importer. Before enabling v2 automatic sync, open v0.1.3 and tap **Stop auto sync**. Then allow Health Connect access and sign in inside v2. Both importers use stable Health Connect record IDs, so rerunning history updates matching production readings. Never enter the TEST API key in this app.

## Use

Install the APK from the dashboard’s Connections section on the Pixel. Android will ask to allow installation from that browser; this is a personal development build, not a Play Store app. Open the importer, allow the desired Health Connect categories and background access, sign in with the same ChatGPT account, then tap Sync now. Verify a fresh Fitdays weight and OMRON BP reading on the dashboard before relying on automatic syncing. The embedded sign-in and background session have not been tested on a physical Pixel; if sign-in fails, stop setup and report the screen rather than sharing passwords or session cookies.

WorkManager schedules an hourly sync when network connectivity is available. Android can delay execution. If the session expires or access is revoked, open the app to reconnect. Stop auto sync cancels scheduled work. Revoking Health Connect access also prevents reads. Uninstalling removes the local session; it does not erase already imported dashboard records.

Imports up to 29 complete Eastern calendar days initially, or 90 days when historical-read access is granted and Import 90-day history is selected. Replays the preceding seven complete calendar dates plus the current partial date on subsequent syncs to pick up changed records. It never replaces a full historical daily total with a partial-day slice. Uploads use stable Health Connect IDs; updates replace matching IDs. Deletions in Health Connect are not propagated in this initial version. Manual import uses the same normalized schema as the dashboard’s JSON export.

## Data semantics

Weight, lean body mass and body water mass: pounds. Body fat: percentage. BP: systolic and diastolic mmHg. Exercise duration: elapsed minutes. Steps, distance, sleep duration and mean heart rate: Health Connect aggregates assigned to America/New_York calendar dates, respecting available platform deduplication. Sleep represents sleep grouped by its Eastern wake date; mean heart rate is not resting heart rate. Aggregate records identify HealthConnect.aggregate as the source because they can combine apps. No skeletal muscle metric is inferred from lean body mass.

Sleep uploads the existing `sleep` duration metric plus light, deep, REM, awake, time in bed and efficiency when source stages exist. Bedtime and wake time are attached to the `sleep` reading. Missing stages remain absent rather than being estimated.

## Build

JDK 17, Android SDK platform 36 and build-tools 35, Gradle 8.13. Run `gradle :app:assembleDebug` from this directory with ANDROID_HOME set. Production distribution would require release signing and a tested sign-in flow. This repository intentionally excludes local signing keys, Gradle cache, SDK files, build output and session cookies.

Pushes to `main` or `sleep-v2` run the **Build Android APK** workflow. Download the `bill-progress-importer-production-v0.2.0` artifact from its successful run.
