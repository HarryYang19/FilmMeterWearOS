# Film Meter — Wear OS 0.2.1

A touch-first incident-light film exposure meter for Samsung Galaxy Watch5 Pro. The interface is drawn specifically for a circular watch display and takes visual cues from classic mechanical exposure meters and camera shutter dials.

## New in 0.2.1

- **Stronger dial hierarchy.** The selected fixed aperture/shutter is larger than ordinary scale labels; the complementary value calculated from the live meter is larger still. All scale labels are readable around the rings, with the right-side EI display kept clear.
- **Rebalanced centre display.** The central hub is slightly smaller. The exposure pair and EV100 line have exchanged vertical positions, giving the live exposure combination the more prominent position.
- **Larger menu controls.** Settings and scene controls are narrower and taller, with larger labels, values and plus/minus touch targets. Only the visible minus/plus areas change step values.
- **Persistent dial-edit gesture.** Long-press a dial to enter edit mode, then release and make circular drags to change the setting. Long-press the same dial again to confirm, or leave it untouched for five seconds to auto-lock. The invisible touch bands extend beyond the printed ring for easier operation.

## New in 0.2.0

- **True aperture-priority and shutter-priority metering.** Long-press the outer aperture ring or inner shutter ring, rotate your finger around that dial to select a value, then lift your finger to lock it. The other exposure parameter is calculated from the live EV and the selected EI/ISO. Long-pressing the other ring transfers the lock to that parameter.
- **Retroactive edit highlight.** While editing, the selected scale gets a warm brass highlight, soft glow and a subtle pulse; the dial returns to its restrained locked appearance when released.
- **Touch-only operation.** No virtual-bezel or rotary-crown controls are used on the meter screen or in settings. Tap the centre hub (or EI plate) for the menu. Menu values use visible touch targets and +/− controls.
- **More legible dial.** Larger aperture/shutter numerals, stronger contrast, a clearer central reading and an independent EI plate are used while preserving the round-watch layout.
- **Non-linear dial motion.** Scale changes use a cubic-bezier ease-out curve; edits also have soft tactile feedback.
- **Persistent priority.** EI, calibration, meter source, manual EV, selected priority and the locked index are saved and restored after relaunch.
- **No AOD mode.** Ambient lifecycle rendering and the Wear ambient dependency have been removed. `FLAG_KEEP_SCREEN_ON` keeps the display awake while the app remains foregrounded. Normal OS behavior resumes when the app is backgrounded or closed; sustained screen-on use increases battery consumption.

## Controls

### Main meter

- Long-press the **outer ring** to enter aperture edit mode; release, then drag around the outer touch band to step through the scale.
- Long-press the **inner ring** to enter shutter edit mode; release, then drag around the inner touch band to step through the scale.
- Long-press the currently edited dial again to confirm and lock immediately. Otherwise the edit automatically locks after five seconds without interaction.
- The touch bands are wider than the visible labels so they are easier to reach on a small circular display. Tap the EI plate or centre hub for the menu.
- The opposite dial updates automatically as lux/EV changes.
- Tap the central hub or the EI plate to open settings.

### Settings

- Tap the left/right round buttons to change EI/ISO or calibration.
- Tap the measurement-source row to switch between the light sensor and manual EV.
- In manual mode, use the −/+ buttons for 0.3 EV steps and open Scene Presets for a starting value.
- Tap the close symbol or Done button to return to the meter.

The app deliberately ignores the watch's rotary/virtual bezel input. All controls are on-screen touch interactions.

## Metering model

The approximation used here is the incident-illuminance form:

`EV100 = log2(lux × 100 / 250) + calibrationBias`

which is equivalent to `log2(lux / 2.5) + calibrationBias`. The working exposure value is:

`EV(EI) = EV100 + log2(EI / 100)`

A camera combination is evaluated with:

`EV = log2(aperture² / exposureTimeSeconds)`

With no fixed parameter, the meter recommends a balanced pair. In aperture-priority mode the selected f-number remains fixed and the nearest shutter speed is calculated. In shutter-priority mode the selected time remains fixed and the nearest aperture is calculated. If the required value is beyond the displayed scale, the complementary parameter stops at the nearest available end of the scale; the displayed EV difference indicates the residual exposure error.

The displayed aperture and shutter tables are conventional rather than exhaustive. Actual film-camera metering, lens transmission, shutter/aperture calibration, reciprocity failure at long exposures and film latitude can differ. For reciprocity-sensitive film and exposures longer than about one second, consult the film manufacturer's reciprocity data.

## Sensor and fallback

- Reads ambient illuminance through Android `Sensor.TYPE_LIGHT` (lux).
- Uses a short median window and EV-space smoothing to reduce hand movement and sensor noise.
- If the sensor is absent, registration fails, or no initial event arrives after the startup timeout, the app switches to manual EV100.
- Screen brightness is not used as a substitute for ambient light; it is an output setting, not an environment measurement.
- The internal sensor placement profile remains `WATCH5_PRO_FRONT_SIDE`, with future profiles reserved for display-top, top-bezel, display-centre and side-frame layouts. The first version still uses the standard Android light-sensor interface.

Use the calibration control to compare the watch against a known handheld meter with the watch held in the same orientation. A positive bias raises the measured EV and therefore recommends a faster exposure. Calibration applies only to sensor-derived EV, not to manually entered EV.

## Build on GitHub

1. Upload the contents of this project ZIP to the root of a GitHub repository; keep the `.github` directory.
2. Open **Actions → Build Film Meter APK → Run workflow**, or push to `main`.
3. Download the `FilmMeter-APKs` artifact after the workflow completes.
4. Install `app-debug.apk` for testing. The release APK is unsigned and needs signing before distribution.

The workflow uses JDK 17, Android SDK 36, Build Tools 36.0.0, Gradle 8.11.1, Android Gradle Plugin 8.10.1 and Kotlin 2.2.21. It uses `android-actions/setup-android@v4` and does not request the retired SDK package named `tools`.

## Version 0.2.1 notes

- Edit mode stays active after lifting the finger. A subsequent circular drag adjusts the selected dial; another long press confirms, or a five-second inactivity timer confirms automatically.
- Selected and computed values are highlighted at two distinct text sizes; the computed complementary value is the larger of the two.
- The centre exposure pair and EV100 lines have swapped positions. The hub is slightly reduced to maintain visual breathing room.
- The settings cards are narrower and taller with larger touch targets.

## Version 0.2.0 notes

- First device target remains Galaxy Watch5 Pro. Other Wear OS watch sensor positions are represented in code but have not been validated on Pixel Watch or other Samsung models.
- Long-press detection and dial rotation are optimized for a circular touch display. Test finger direction and sensitivity on the actual watch; the watch's bezel is intentionally not involved.
- Keeping the screen awake while the application is foregrounded can noticeably increase battery use. The app no longer implements AOD or ambient-mode updates.
