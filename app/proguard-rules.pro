# OwnTV release keep rules (R8). Each rule guards a runtime lookup that R8 can't see
# statically — trim only with a full on-device regression (sync, both engines, backup).

# --- Crash reports: keep readable stack traces (mapping.txt still needed for names) ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- libmpv JNI: native code resolves MPVLib and its event callbacks by name ---
-keep class dev.jdtech.mpv.** { *; }

# --- WorkManager: workers are re-instantiated by FQCN string (KoinWorkerFactory matches
#     workerClassName against ::class.java.name, and WorkManager persists the name in its DB
#     across app updates) ---
-keep class * extends androidx.work.ListenableWorker

# --- Room: Room.getGeneratedImplementation() derives this exact class name and loads it
#     reflectively. Keep the generated database entry point in optimized release builds. ---
-keep class tv.own.owntv.core.database.OwnTVDatabase_Impl { *; }

# --- Persisted enum names: DataStore prefs, backup JSON ("t" = MediaType.name), and
#     per-setting modes (ZoomMode, ThemeMode, StartupMode, AnimationLevel, ResumeMode,
#     EpgAutoRefresh, PlaylistAutoRefresh, ...) round-trip through Enum.name/valueOf.
#     Renaming a constant would silently reset settings and break old backups. ---
-keep enum tv.own.owntv.** { *; }

# glcn: R8 emitted unverifiable bytecode for PlayerHud (VerifyError "register has type … but expected
# Integer" in its ~90-parameter composable, on Android 11 — the app closed at start). Keep the file's
# functions as written: names may still be shortened, but their signatures are not rewritten or merged.
-keep,allowobfuscation class tv.own.owntv.player.PlayerHudKt { *; }
