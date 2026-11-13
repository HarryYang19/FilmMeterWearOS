# Film Meter — Wear OS 0.2.4

A touch-first incident-light film exposure meter for Samsung Galaxy Watch. The interface is drawn specifically for a circular watch display and takes visual cues from classic mechanical exposure meters and camera shutter dials.

## New in 0.2.2

- The calculated value on each dial is now markedly larger than the ordinary scale values and uses bright white text on a dark, white-outlined highlight plate.
- The central exposure pair is displayed as two large white values (aperture and shutter) so it is the dominant information in the hub.
- Enlarged the dial footprint to reduce unused edge space and increased the normal scale-label size.
- Expanded touch bands to match the larger dial geometry.

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

- Long-press the **outer ring** to enter aperture edit mode; release, then drag around the dial to step through the scale.
- Long-press the **inner ring** to enter shutter edit mode; release, then drag around the dial to step through the scale.
- Once a parameter is selected, the broad annular area between the centre hub and the outer edge adjusts that same parameter. Crossing over the other printed ring will not switch the lock.
- Long-press the currently edited dial again to confirm and lock immediately. Otherwise the edit automatically locks after five seconds without interaction.
- The touch bands are wider than the visible labels so they are easier to reach on a small circular display. Tap the EI plate or centre hub for the menu.
- The opposite dial updates automatically as lux/EV changes.
- Tap the central hub or the EI plate to open settings.

### Settings

- Tap the left/right round buttons to change EI/ISO or calibration.
- Tap the measurement-source row to switch between the light sensor and manual EV.
- In manual mode, use the −/+ buttons for 0.3 EV steps and open Scene Presets for a starting value.
- Open **Colors** to choose Brass / Amber, Arctic / Cyan, Garnet / Red, Forest / Lime or Graphite / Silver. The selected scheme is remembered after relaunch.
- Tap the close symbol or Done button to return to the meter.

The app deliberately ignores the watch's rotary/virtual bezel input. All controls are on-screen touch interactions. The left-edge swipe-to-dismiss gesture is excluded within a narrow strip; other navigation gestures and the hardware navigation key remain available. Android treats gesture exclusions as a request and device policy may still affect the result.

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


## Version 0.2.2 display update

- Increased the round-dial drawing radius to use more of the available display area.
- Enlarged ordinary aperture and shutter labels for better legibility.
- The calculated dial value is now substantially larger than neighbouring values, drawn in bright white with a high-contrast dark plate and white outline; the result is rendered last to keep it visually dominant.
- The calculated centre exposure pair (`f-number` and shutter speed) is split into two large white values, with EV100 retained below it.
- Expanded touch bands to match the enlarged dial layout.



## Version 0.2.3 — touch, navigation and color themes

- **Faster dial adjustment:** the angular detent is reduced to 60% of the previous span with half-detent rounding (about 1.67× the former sensitivity). Historical `MotionEvent` samples are consumed to avoid skipping steps, and the active/complementary dial animations are shortened to 42/72 ms.
- **Expanded locked-dial touch area:** while editing, almost the entire annular area outside the centre hub and EI window controls the already-selected parameter. Crossing the other ring cannot change the lock. Parent interception is disabled during the gesture.
- **Selective swipe-to-dismiss exclusion:** a 30 dp strip at the left edge is reserved for the app, matching Wear OS's left-to-right dismiss gesture. The right edge, bottom/home area and hardware navigation remain untouched. Android gesture exclusions are best-effort and can still be affected by device policy.
- **Five persistent color themes:** Brass / Amber, Arctic / Cyan, Garnet / Red, Forest / Lime and Graphite / Silver. Select one under Settings → Colors; the choice is saved across relaunches.
- **Menu typography:** headings, field labels, current values, step buttons, scene presets and palette names use a coordinated size hierarchy.

## Version 0.2.4 — touch feel, navigation and palettes

- Rotation detents are more responsive, historical touch samples are consumed, and dial animations are shortened while adjusting.
- While a parameter is locked for editing, the enlarged annular touch area follows that same parameter across both printed rings. This prevents accidental priority switching.
- The app reserves only a 30 dp strip along the left edge for Wear OS's left-to-right swipe-to-dismiss gesture. The opposite edge, bottom/home gestures and hardware navigation are left to the system. Gesture exclusion is best-effort and should be verified on the target watch.
- Five persistent palettes are available under Settings → Colors: Brass / Amber, Arctic / Cyan, Garnet / Red, Forest / Lime and Graphite / Silver.
- Menu typography has been rebalanced: larger page headings and row labels, consistent large values, and wider back buttons for scene/theme pages.

Version `0.2.4`, `versionCode = 7`. The existing GitHub Actions workflow is retained.
