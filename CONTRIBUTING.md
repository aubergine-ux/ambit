# Contributing to Ambit

Thanks for helping. Ambit is used by people who may be in a dangerous situation, so the bar is
correctness and honesty over features.

## Ground rules

- **No network, ever.** No `INTERNET` permission, no analytics, no crash reporters, no
  libraries that phone home. If a change needs the network, open an issue first.
- **No Google Play Services.** Ambit has to build and run on F-Droid.
- **Don't overclaim.** The app finds devices that broadcast. It can't find switched-off
  trackers, cameras that record to a memory card, or spyware, and the UI says so.
- **The three-place threshold stays.** Sensitivity levels may change distances and times,
  never go below three separated places.

## Building

```bash
./gradlew test assembleDebug lint
```

JDK 17 or newer and the Android SDK (API 36). The emulator has no radios, so anything that
touches scanning has to be tried on a real phone. Say in your pull request what you tested on
hardware and what you didn't.

## Changing detection

Tracker detection lives in `analysis/`. If you add a tracker family or change how one is read:

1. Add the advertisement to `app/src/test/resources/fixtures/adverts.json`, ideally a real
   capture (the packet log under Settings › Expert shows raw bytes).
2. Add correlator tests that feed a synthetic trail of sightings, both the case that should
   alert and the near miss that shouldn't.
3. Cite the specification or source for the format in a code comment.

## Database changes

Every schema change ships a `Migration` and an exported schema in `app/schemas/`, plus a
case in `MigrationTest`. Never use destructive migration; people keep months of logs.

## Translations

All user-facing text is in `app/src/main/res/values/strings.xml`. Comments above each group
explain the context. Keep placeholders (`%1$d`), escapes (`\'`, `\n`) and the number of
`\n`-separated lines exactly as in English. Plurals use `<plurals>`.

## Commits and pull requests

Small commits with short conventional messages: `feat:`, `fix:`, `refactor:`, `docs:`,
`test:`, `build:`, `ci:`. One logical change per commit.

## Screenshots

README screenshots are rendered from the real screens:

```bash
./gradlew testDebugUnitTest --tests '*ReadmeScreenshots*' -Pambit.screenshots
```
