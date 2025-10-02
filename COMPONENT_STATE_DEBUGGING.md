# Component State Debugging Guide

## Problem Overview

**Symptom**: `Activity class {com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity} does not exist`

**Root Cause**: `MainActivity` component was disabled by `StealthManager.activateAntiTamperingProtection()` which treated DEBUG builds as tampering attempts.

## Quick Fix (On Device/Emulator)

### Method 1: ADB Command (Fastest)
```bash
# Re-enable MainActivity
adb shell pm enable com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity

# Verify it's enabled
adb shell pm list packages -e | grep parentalcontrol
```

### Method 2: Complete Reset
```bash
# Uninstall the app completely
adb uninstall com.parentalcontrol.mvp

# Rebuild and reinstall
./gradlew installDebug
```

### Method 3: Check and Fix All Components
```bash
# Check all component states
adb shell dumpsys package com.parentalcontrol.mvp | grep -A 20 "Activity Resolver"

# Re-enable MainActivity
adb shell pm enable com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity

# Disable all stealth aliases (if accidentally enabled)
adb shell pm disable com.parentalcontrol.mvp/.SystemUpdateAlias
adb shell pm disable com.parentalcontrol.mvp/.AndroidServiceAlias
adb shell pm disable com.parentalcontrol.mvp/.CalculatorAlias
adb shell pm disable com.parentalcontrol.mvp/.NotesAlias
```

## Code Fix (Permanent Solution)

### Fix Applied in StealthManager.kt

```kotlin
fun activateAntiTamperingProtection() {
    try {
        // Skip anti-tampering in DEBUG builds to prevent disabling MainActivity
        if (BuildConfig.DEBUG) {
            systemLogger.d(TAG, "🛡️ Anti-tampering protection SKIPPED in DEBUG build")
            return  // ✅ This prevents the bug
        }
        
        // ... rest of anti-tampering logic only runs in RELEASE builds
    }
}
```

**Why This Works**:
- In DEBUG builds (development), anti-tampering is completely skipped
- MainActivity is never disabled during development/testing
- In RELEASE builds, anti-tampering still works normally

## Automated Tests

### Unit Tests (Run Fast)
```bash
# Run StealthManager unit tests
./gradlew testDebugUnitTest --tests StealthManagerTest

# These verify anti-tampering doesn't call PackageManager in DEBUG
```

### Instrumented Tests (Run on Device)
```bash
# Run component state verification tests
./gradlew connectedDebugAndroidTest --tests ComponentStateInstrumentedTest

# Run anti-tampering regression tests
./gradlew connectedDebugAndroidTest --tests AntiTamperingRegressionTest
```

### Critical Tests Created

1. **ComponentStateInstrumentedTest**
   - `mainActivity_shouldBeEnabled_onDevice()` - Detects disabled MainActivity
   - `mainActivity_shouldBeLaunchable()` - Catches launch failures
   - `diagnostic_printAllComponentStates()` - Logs all component states
   - `recovery_enableMainActivityIfDisabled()` - Self-healing test

2. **AntiTamperingRegressionTest**
   - `regression_mainActivityLaunch_withAntiTampering_shouldNotDisableActivity()` - Main regression test
   - `regression_multipleLaunches_shouldNotDisableActivity()` - Stress test
   - `stressTest_rapidLaunches_shouldNotCorruptComponentState()` - Stability test

3. **StealthManagerTest** (Unit)
   - `activateAntiTamperingProtection_inDebugBuild_shouldNotDisableMainActivity()` - Core test
   - Various stealth mode tests

## Manual Diagnostic Steps

### 1. Check Component State on Device
```bash
# Get detailed package info
adb shell dumpsys package com.parentalcontrol.mvp | grep -A 50 "Activity Resolver"

# Check specific component
adb shell pm list packages -d | grep parentalcontrol  # Lists DISABLED
adb shell pm list packages -e | grep parentalcontrol  # Lists ENABLED
```

### 2. Check Logcat for Anti-Tampering
```bash
# Watch for anti-tampering logs
adb logcat | grep -E "StealthManager|Anti-tampering|TAMPERING"

# Look for:
# ✅ "Anti-tampering protection SKIPPED in DEBUG build" - Good!
# ❌ "Tampering detected! Activating emergency stealth mode" - Bad in DEBUG!
```

### 3. Verify BuildConfig in Logs
```bash
# Check if app thinks it's a DEBUG build
adb logcat | grep "BuildConfig.DEBUG"

# Should see: BuildConfig.DEBUG: true (in debug builds)
```

### 4. Test Launch Manually
```bash
# Try to launch MainActivity directly
adb shell am start -n com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity

# If you get "Activity class ... does not exist":
# 1. Component is disabled
# 2. Run: adb shell pm enable com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity
```

## Prevention Checklist

- [x] ✅ `BuildConfig` imported in `StealthManager.kt`
- [x] ✅ DEBUG guard added to `activateAntiTamperingProtection()`
- [x] ✅ Unit tests verify no PackageManager calls in DEBUG
- [x] ✅ Instrumented tests verify MainActivity stays enabled
- [x] ✅ Regression tests prevent this bug from returning

## Common Scenarios

### Scenario 1: Fresh Clone/Pull
```bash
# After pulling latest code
./gradlew clean
./gradlew assembleDebug
./gradlew installDebug

# MainActivity should work immediately
```

### Scenario 2: Existing Installation with Disabled MainActivity
```bash
# Re-enable first
adb shell pm enable com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity

# Then reinstall
./gradlew installDebug
```

### Scenario 3: Testing Stealth Mode
```bash
# To test stealth mode in DEBUG:
# 1. Temporarily comment out the DEBUG guard in activateAntiTamperingProtection()
# 2. Build and test
# 3. REMEMBER to uncomment it before committing!

# Or better: Test stealth mode with release build
./gradlew assembleRelease
```

## For CI/CD

Add this check to your CI pipeline:

```yaml
# Example GitHub Actions
- name: Run Component State Tests
  run: |
    ./gradlew connectedDebugAndroidTest --tests ComponentStateInstrumentedTest
    
- name: Verify MainActivity Launch
  run: |
    adb shell pm enable com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity || true
    adb shell am start -n com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity
```

## Troubleshooting

### Problem: Tests fail with "MainActivity does not exist"

**Solution**:
```bash
# 1. Enable MainActivity on test device
adb shell pm enable com.parentalcontrol.mvp/com.parentalcontrol.mvp.MainActivity

# 2. Run recovery test first
./gradlew connectedDebugAndroidTest --tests ComponentStateInstrumentedTest.recovery_enableMainActivityIfDisabled

# 3. Run other tests
./gradlew connectedDebugAndroidTest
```

### Problem: Anti-tampering still triggers in DEBUG

**Check**:
1. Verify `BuildConfig` is imported: `import com.parentalcontrol.mvp.BuildConfig`
2. Verify guard is first line in method: `if (BuildConfig.DEBUG) { return }`
3. Check Logcat for "SKIPPED in DEBUG build" message
4. Rebuild: `./gradlew clean assembleDebug`

### Problem: Can't reproduce on emulator but fails on real device

**Possible causes**:
1. Different APK installed (release vs debug)
2. Old app data persisted
3. Device-specific security features

**Solution**:
```bash
# Complete reset on device
adb uninstall com.parentalcontrol.mvp
adb shell pm clear com.parentalcontrol.mvp || true
./gradlew installDebug

# Verify build type
adb shell dumpsys package com.parentalcontrol.mvp | grep -A 5 "versionName"
```

## Additional Resources

- **MainActivity.kt**: Lines 104-106 call `activateAntiTamperingProtection()`
- **StealthManager.kt**: Lines 360-382 contain anti-tampering logic
- **ComponentStateInstrumentedTest.kt**: Automated detection tests
- **AntiTamperingRegressionTest.kt**: Regression prevention tests

## Summary

This issue was caused by anti-tampering protection treating DEBUG builds as tampering attempts and disabling MainActivity. The fix:

1. ✅ Added DEBUG guard in `StealthManager.activateAntiTamperingProtection()`
2. ✅ Created comprehensive tests to detect this issue
3. ✅ Documented recovery procedures
4. ✅ Added prevention checklist

**Result**: MainActivity will never be disabled in DEBUG builds, preventing the "Activity does not exist" error during development.
