# Give or Take

An Android quiz app for practising numerical estimation and checking whether confidence ranges are as reliable as they claim to be.

[![CI](https://github.com/chrkorn/give-or-take/actions/workflows/ci.yml/badge.svg)](https://github.com/chrkorn/give-or-take/actions/workflows/ci.yml)

## What it does

Give or Take asks questions whose answers are quantities, such as “How long is the River Thames?”. In standard mode, the player submits one number. The score reflects how close that estimate is to the true value. It uses log-relative error, `|log10(guess / truth)|`, so an estimate that is too large by a given factor is treated like one that is too small by the same factor.

Interval mode asks for a lower and upper bound intended to contain the true answer with 90% confidence. Across multiple sessions, the app compares that stated confidence with the proportion of ranges that actually contained the truth. Unlike a normal quiz app, it does not reduce every answer to correct or incorrect: it measures estimation error continuously and makes systematic overconfidence or underconfidence visible.

## Screenshots

Screenshots will be added as the user interface is completed.

<!-- Add labelled screenshots here. -->

## Building and running

The project is under development. To build the current version you need:

- Android Studio Quail 4 (2026.1.4) or later
- Android SDK Platform 37, with the SDK location made available to Gradle (see below)
- An emulator or Android device running API level 26 or later

You do not need to install a JDK yourself. The build pins its own toolchain
(Adoptium 17, declared in `gradle/gradle-daemon-jvm.properties`) and Gradle
downloads it on first run. The application source is compiled against Java 11.
The Gradle wrapper is committed, so no separate Gradle installation is needed
either.

Clone the repository and enter its directory:

```shell
git clone https://github.com/chrkorn/give-or-take.git
cd give-or-take
```

### Pointing the build at your Android SDK

Gradle needs to know where your Android SDK lives. The file that normally
carries this, `local.properties`, is machine-specific and deliberately not
committed, so a fresh clone does not have one. Choose either option:

- **Open the project in Android Studio first.** Select *Open*, choose the
  cloned `give-or-take` directory, and let the Gradle sync finish. Android
  Studio writes `local.properties` for you, after which the commands below
  work in a terminal.
- **Or set the location yourself**, from the repository root:

  ```shell
  export ANDROID_HOME=/path/to/your/Android/sdk
  ```

  On macOS the default path is `~/Library/Android/sdk`; on Linux it is
  `~/Android/Sdk`. Alternatively, create a `local.properties` file in the
  repository root containing `sdk.dir=/path/to/your/Android/sdk`.

Without one of these, the build stops with `SDK location not found`.

### Building

Build a debug APK and run the local unit tests:

```shell
./gradlew assembleDebug
./gradlew test
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To run the app in Android Studio, open the cloned `give-or-take` directory and wait for the Gradle sync to finish. Open **Tools > Device Manager**, create and start a virtual device with API level 26 or newer, select the `app` run configuration, and click **Run**. Android Studio will build, install, and launch the app on the selected emulator.

## Running the tests

Local unit tests use JUnit 4 and cover plain Java logic without starting Android. Run all of them from the repository root:

```shell
./gradlew test
```

Instrumented tests use Espresso and run inside Android. Start an emulator or connect a device, confirm that it appears in Android Studio, then run:

```shell
./gradlew connectedDebugAndroidTest
```

In Android Studio, individual tests can also be run from the gutter beside a test class or method. Unit tests live under `app/src/test/`; instrumented tests live under `app/src/androidTest/`.

## Project structure

Application code lives below the package root in `app/src/main/java/`. The
packages below are the target layout; the repository currently holds the
project skeleton, and they are created as the implementation proceeds.

```text
de/christiankorn/giveortake/
├── core/   scoring, calibration, questions, and training rules
├── ui/     activities, adapters, and custom views
└── data/   SQLite access, data-access objects, and preferences
```

The `core` package is plain Java and must not import `android.*`. Keeping the main rules independent of the Android framework makes them fast to run and straightforward to test with ordinary JUnit tests. Android-specific screen and persistence code stays in `ui` and `data`.

## Design decisions

- [ADR 0001: Use Java with XML layouts](docs/adr/0001-language-and-ui-toolkit.md) records the choice of Java, XML layouts, multiple activities, and a framework-independent core.

Further decisions are recorded in [`docs/adr/`](docs/adr/) as the implementation develops.

## Question data

The question source, author or publisher, retrieval date, permitted use, and licence will be documented here before a question set is distributed with the app.

## Licence

Released under the MIT Licence. You may use, copy, modify, merge, publish,
distribute, sublicense, and sell copies of this software, for any purpose,
including commercially, provided the copyright notice and permission notice
are retained. The software comes with no warranty. See [LICENSE](LICENSE) for
the full text.

This covers the source code in this repository. Any question data shipped with
the app carries its own licence, recorded in the section above.
