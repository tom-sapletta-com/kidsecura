# 📋 Naprawa LogViewerActivity

## Problem:
"w menu podgld logw nie wida log jak w ostatnbich wydarzeniahc"

LogViewerActivity pokazywał tylko logi z `monitoring_log_*`, a pomijał `system_log_*` (błędy parowania, Telegram, przyciski, etc.)

## Rozwiązanie:

Rozszerzyłem **LogFileReader** aby czytał z OBUŹ źródeł, tak jak MainActivity:

### PRZED:
```kotlin
// Tylko monitoring_log_*
private const val LOG_FILE_PREFIX = "monitoring_log_"

private fun getLogDirectory(): File? {
    // Tylko Downloads/KidSecura
}
```

### PO:
```kotlin
// Oba źródła
private const val MONITORING_LOG_PREFIX = "monitoring_log_"
private const val SYSTEM_LOG_PREFIX = "system_log_"

private fun getMonitoringLogDirectory(): File? {
    // Downloads/KidSecura/monitoring_log_*
}

private fun getSystemLogDirectory(): File? {
    // getExternalFilesDir/KidSecura/system_log_*
}

suspend fun getLogFiles(): List<File> {
    // Łączy oba źródła
    // Monitoring + System logs
    // Sortuje chronologicznie
}
```

## Rezultat:

LogViewerActivity teraz pokazuje **WSZYSTKIE** logi:
- ✅ Wykrywanie słów (monitoring)
- ✅ Błędy parowania (system)
- ✅ Telegram/WhatsApp (system)
- ✅ Kliknięcia przycisków (system)
- ✅ Lifecycle aktywności (system)
- ✅ Wszystkie incydenty (oba)

**Zsynchronizowane z MainActivity** - te same logi w obu miejscach!

## Status:
```
✅ BUILD SUCCESSFUL
✅ Zainstalowano na 2 urządzeniach
✅ LogViewerActivity = MainActivity (te same logi)
```
