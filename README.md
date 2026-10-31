# Film Meter — Wear OS

A first-version incident-light film exposure meter for Samsung Galaxy Watch5 Pro. The app is designed for the round 450 × 450-class display and uses a custom-drawn, dark mechanical-meter dial.

## What it does

- Reads ambient illuminance through Android `Sensor.TYPE_LIGHT` (lux).
- Smooths incoming readings with a short median window and an exponential filter in EV space.
- Converts illuminance to an approximate incident-meter EV100, applies the selected film EI and a user calibration bias, then searches a practical aperture/shutter table for the nearest exposure pair.
- Rotates the aperture and shutter scales so the chosen values meet the fixed top index.
- Shows the selected EI in the small plate at 3 o'clock. Tap the EI plate to step through common film speeds; the watch's rotary input can also change EI.
- The centre of the dial opens settings: EI, calibration bias, AUTO/MANUAL source, manual EV100 fine adjustment, quick scene presets (dim room through sunny daylight), and sensor-profile information.
- Uses `AmbientLifecycleObserver` and target SDK 36 for a low-power monochrome ambient layout. Ambient refresh is system-scheduled and can be as infrequent as about once per minute; the last good light reading is held while in ambient mode to avoid depending on a non-wake-up sensor while the processor is suspended.

## Galaxy Watch5 Pro sensor notes

Samsung lists one light sensor in the Watch5 Pro sensor set. Its user manual marks a single light-sensor position in the front/side device-layout illustration, beside the touchscreen area. This v1 accesses the Android standard `TYPE_LIGHT` interface rather than hard-coding Samsung's vendor sensor name. The internal profile is `WATCH5_PRO_FRONT_SIDE`; future device profiles can represent a top display, top bezel, display centre, or side-frame placement. The sensor-position selector is intentionally not exposed in this first build.

The sensor plane is not an ideal incident-meter dome. Wrist angle, sleeve/hand shadow, sensor window orientation, and watch-case geometry can bias the reading. Use the CALIBRATION page to compare against a known handheld meter under the same orientation and enter an EV correction. A positive correction raises the measured EV (thus recommends a faster exposure); this correction applies only to the light sensor, not to a manual EV value. For a practical reading, raise the wrist with the watch face directed toward the scene/incident light and keep the sensor window unobstructed.

## If the light sensor cannot be read

The app tries the default `TYPE_LIGHT` sensor, then scans the device sensor list for another sensor with the same standard type. It retries registration at a normal sampling rate if the UI rate fails. If there is no such sensor, registration fails, or no initial event arrives after the 8-second startup timeout, it switches to the manual EV100 source. Zero lux is accepted as a valid dark-scene reading and floored only for the logarithmic EV calculation.

There is no trustworthy software substitute for ambient lux if the watch exposes no light sensor data. Screen brightness is an output controlled by the watch and is not a measurement of surrounding illumination; proximity, heart-rate, and motion sensors do not measure lux. Therefore the fallback is an explicit manual EV100 input, not a fabricated lux estimate. You can adjust EV100 in 0.3-stop increments, or choose a rough scene preset (dim room, room light, bright room, overcast, open shade, sunny) and fine-tune it. A scene preset is only a starting estimate; a known meter is preferable.

## Metering model

The approximation used here is the standard incident-illuminance form:

`EV100 = log2(lux × 100 / 250) + calibrationBias`

which is equivalent to `log2(lux / 2.5) + calibrationBias`. The working exposure value is then:

`EV(EI) = EV100 + log2(EI / 100)`

A candidate pair is evaluated with:

`EV = log2(aperture² / exposureTimeSeconds)`

The nearest pair is selected from the displayed aperture and shutter scales. The table is deliberately conventional rather than exhaustive; actual film-camera metering, lens transmission, reciprocity failure at long exposures, and shutter/aperture calibration can differ. Treat the watch as a compact estimation tool, not a laboratory-calibrated handheld meter. For reciprocity-sensitive film and exposures longer than about one second, consult the film manufacturer's reciprocity data.

## Build on GitHub

1. Create an empty GitHub repository and upload the contents of this ZIP to its root (the `.github` directory must remain present).
2. Open **Actions → Build Film Meter APK → Run workflow**, or push to the `main` branch.
3. Wait for the `FilmMeter-APKs` artifact on the completed workflow.
4. Download the artifact and install `app-debug.apk` for testing. The release output is unsigned (`app-release-unsigned.apk`) and is intended for a later signing step if you plan to distribute it.

The workflow uses `android-actions/setup-android@v4` (not v3), installs only `platform-tools` through the action, then installs `platforms;android-36` and `build-tools;36.0.0` explicitly with the current command-line `sdkmanager`. It also installs JDK 17 and Gradle 8.11.1, runs the JVM unit tests, then builds debug and release APKs. The removed SDK package named `tools` is deliberately never requested.

### Build workflow note

The previous `setup-android@v3` workflow failed before Gradle because that action requested the retired SDK package `tools`. This revision moves to `setup-android@v4`, explicitly avoids that package, and installs the Android platform/build-tools packages separately.

## Local build

Open the project in Android Studio with an installed Android 36 SDK and JDK 17, or run:

```bash
gradle clean testDebugUnitTest assembleDebug assembleRelease
```

## Version 0.1.1 limitations

- First target is Watch5 Pro. Generic Wear OS sensor profiles are represented in code but have not been validated on Pixel Watch or other Samsung models.
- No multi-sensor fusion or sensor-orientation compensation is inferred automatically. Use the calibration offset for the current wrist posture.
- The dial is a single-activity app, not a watch face or complication provider. AOD applies while the app is foregrounded; the system can still return to the watch face after its configured timeout.
- Ambient display refresh is controlled by Wear OS. The app redraws on the ambient callback, but cannot force high-frequency AOD refresh.
- No camera-based fallback is attempted. The Watch5 Pro has no built-in camera suitable for this measurement path.
