# CIVOPS

A passive radio-environment observer for Android. It logs Wi-Fi and Bluetooth broadcasts
over time, names the manufacturer behind an address where it can, and warns when a
Bluetooth beacon appears to be following you between places.

Everything runs on-device. The manifest declares no `INTERNET` permission, so the offline
claim is enforced by the platform rather than by good intentions.

## Build

```bash
export JAVA_HOME=/usr/local/android-studio/jbr   # any JDK 17+
./gradlew assembleDebug                          # app/build/outputs/apk/debug/
./gradlew assembleRelease                        # app/build/outputs/apk/release/
./gradlew test                                   # 62 JVM unit tests
./gradlew lint
```

Install:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -s CivOps:V
```

The release build is signed with `app/civops-release.jks` (store and key password
`civops-beta`). That keystore is a throwaway for beta distribution — generate your own
before shipping anything you intend to update later, because an APK can only be upgraded
by a build signed with the same key.

**The emulator has no radios.** Every scan path returns empty there. All meaningful testing
happens on a physical device; set up wireless debugging so you can walk a building with the
phone untethered while it logs.

## Screens

- **OPS** — radar, live Wi-Fi readout, BLE controls, one-tap exports. One vertical scroll,
  nothing behind a tab, because on a dashboard mount a tab is a thing you cannot reach.
- **ALERTS** — tracker-correlation verdicts and the method behind them.
- **EXPORT** — every export shape, plus a debrief preview.
- **SETTINGS** — scan interval, alert cues, known places, retention.
- **Device detail** — pushed from any row: identity, RSSI trace, sighting history.

## Four constraints that shaped this

**Scan throttling.** `WifiManager.startScan()` is capped at four calls per two minutes on
Android 9+, and foreground apps get no exemption. The app refreshes slowly and honestly,
shows a real scan age, and detects the frozen-result-set signature of throttling so it can
point at the Developer Options toggle instead of leaving you wondering why numbers stopped
moving. It never re-emits cached results with fresh timestamps to fake a faster rate.

**MAC randomization.** Most BLE devices rotate their address every ~15 minutes. Vendor
lookup therefore fails for the majority of BLE hits — that is expected, not a bug, and the
UI says "randomized address" rather than inventing an unknown manufacturer. Identity for
BLE lives in `TrackerSignature`, never in the address.

**The radar is decorative.** One antenna, no phased array, no bearing information. Radius
comes from log-distance path loss and is worth "near / mid / far"; the angle is a hash of
the address and means nothing. It is hashed rather than random so devices hold position
between frames instead of skittering, which would read as movement. No detection logic
reads a screen position. If the radar and the list disagree, the list is right.

**Foreground service typing.** Android 14+ requires declared types matching granted runtime
permissions, and kills the process at `startForeground` rather than at first use. The app
declares `location` (geotagged observations are the point) plus `connectedDevice`, and
gates its start buttons on the grant rather than starting hopefully.

## Tracker correlation

Because addresses rotate, correlation runs on the advertisement payload. Each BLE
advertisement is reduced to a signature — company ID, service UUIDs, payload length, and
the payload bytes that stay fixed across a key rotation — and observations are bucketed by
it.

A signature is flagged only when it appears at **three or more spatially separated GPS
fixes** with a meaningful time gap between them, outside any place marked as known.

That threshold is deliberate and should not be lowered to make a demo look better. Two
locations is usually coincidence. An alert that cries wolf is worse than no alert, because
people stop reading them and then miss the real one. `TrackerCorrelatorTest` asserts the
threshold directly for exactly this reason.

Recognised families: Apple Find My, Samsung SmartTag, Tile, Google Find My Device, Chipolo.
These formats change between firmware generations — verify against current specifications
before trusting a family label. An unrecognised beacon is still correlated; it just gets
reported without a name.

## What this is not

- It cannot find a camera recording to an SD card. Only things actively broadcasting.
- It cannot identify people or vehicles.
- It does not intercept traffic. Advertisement and beacon metadata only.
- It cannot give a bearing.
- Vendor labels describe a manufacturer, not a device's owner or purpose. A "Fleet"
  cellular router might be in a food truck.

## Notes on the design

`BLUETOOTH_SCAN` is declared **without** `usesPermissionFlags="neverForLocation"`. That flag
reads better, but it strips location out of BLE scan results, and location is exactly what
the correlator consumes — taking it would quietly break the app's one distinguishing
feature. The onboarding screen explains the requirement in plain words instead.

Location is not a data grab: Android gates *all* scan results behind it, and without the
grant `getScanResults()` returns an empty list.

Vendor names come from a bundled copy of the public IEEE MA-L registry (~40,000 assignments,
`app/src/main/assets/oui.tsv`). Ship it as plain text, not gzipped — the build strips a `.gz`
extension and stores such files expanded, which silently breaks a name-based `open()` and
leaves every lookup returning null. `OuiLookupTest` guards this.

Room uses no destructive migration fallback. People keep months of observations.
