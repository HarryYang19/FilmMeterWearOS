# Film Meter
### A Vintage-Inspired Light Meter for Wear OS
### 为 Wear OS 打造的复古胶片摄影测光表
Film Meter is a film photography-oriented light meter designed for Wear OS smartwatches. By utilizing the watch's built-in ambient light sensor, it estimates the surrounding light level and calculates suitable exposure combinations based on the principles of traditional film photography.

Inspired by classic handheld light meters and mechanical camera dials, Film Meter combines practical exposure metering with a vintage-inspired circular interface.

Film Meter 是一款专为 Wear OS 智能手表设计的胶片摄影测光应用。它利用手表内置的环境光传感器估算周围光照，并依据传统胶片摄影的曝光规律计算合适的曝光组合。

应用界面借鉴了经典手持测光表与机械相机的设计语言，将实用的曝光测量功能与复古同心圆仪表盘相结合。

Please fine tune the Exposure Compensation before use !!!
请务必在拍摄前微调曝光补偿，以适配实际环境。应用提供的曝光参数属于实用估算值，并不等同于经过专业校准的独立测光仪器。
---

## Features | 功能介绍

### 1. Ambient Light Metering | 环境光测光

- Uses the smartwatch's built-in ambient light sensor to estimate ambient illumination.
- Provides real-time light readings and corresponding exposure calculations.
- Supports manual exposure-value input as an alternative when automatic sensor readings are unavailable.

- 利用智能手表内置的环境光传感器估算环境亮度。
- 实时显示光照读数，并根据测光结果计算曝光组合。
- 当自动传感器测光不可用时，可使用手动曝光值输入作为替代方案。

### 2. Aperture and Shutter Priority | 光圈与快门优先

Choose either aperture or shutter speed as the fixed exposure parameter. The application calculates the corresponding value according to the measured light level and preset ISO/EI.

- **Aperture Priority:** Lock the aperture and calculate the recommended shutter speed.
- **Shutter Priority:** Lock the shutter speed and calculate the recommended aperture.

选择光圈或快门速度作为固定参数，应用根据当前测光结果及预设 ISO/EI 计算另一项曝光参数。

- **光圈优先：** 锁定光圈，自动计算推荐快门速度。
- **快门优先：** 锁定快门，自动计算推荐光圈。

### 3. ISO / EI Configuration | ISO / EI 设置

Set the film sensitivity (ISO/EI) in the menu. Exposure calculations automatically take the selected sensitivity into account.

在菜单中设置胶片感光度（ISO/EI），所有曝光计算都会根据所选感光度进行调整。

### 4. Vintage-Inspired Circular Interface | 复古同心圆界面

- Concentric aperture and shutter-speed scales.
- A fixed index pointer for reading exposure values.
- Large, high-contrast calculated exposure values.
- A dedicated ISO/EI display area.
- Five selectable colour schemes.

- 同心圆光圈与快门速度刻度盘。
- 固定顶部指针，用于读取曝光参数。
- 大字号、高对比度的计算结果显示。
- 独立的 ISO/EI 显示区域。
- 五种可切换的配色方案。

---

## Controls | 操作指南

### 1. Main Dial | 主界面拨盘

The main interface contains two concentric control rings:

- Outer ring: Aperture.
- Inner ring: Shutter speed.

主界面由两个同心控制环组成：

- 外圈：光圈值。
- 内圈：快门速度。

### 2. Locking an Exposure Parameter | 锁定曝光参数

1. Long-press the aperture or shutter-speed dial to enter editing mode.
2. The selected parameter is highlighted.
3. Drag around the dial to adjust its value.
4. Long-press again to confirm and lock the parameter.
5. The parameter is also locked automatically after five seconds of inactivity.

1. 长按光圈或快门拨盘，进入编辑模式。
2. 被选中的参数会出现高亮效果。
3. 沿拨盘区域滑动手指，调整参数。
4. 再次长按即可确认并锁定。
5. 如果五秒内没有继续操作，系统也会自动锁定当前参数。

**Important:** Once a parameter is locked, the dial interaction area can be used to adjust that same parameter without accidentally switching to the other exposure priority.

**注意：** 参数锁定后，拨盘的较大触控区域都会用于调整当前参数，不会因为手指移动到另一条刻度环而意外切换优先模式。

### 3. Menu | 菜单

Tap the central circular area to open the menu.

The menu provides access to:

- ISO/EI sensitivity settings.
- Exposure and metering configuration.
- Colour theme selection.
- Other application settings.

点击主界面中央圆形区域即可进入菜单。

菜单提供以下设置：

- ISO/EI 感光度设置。
- 曝光及测光相关设置。
- 配色主题选择。
- 其他应用选项。

---

## Design Philosophy | 设计理念

Film Meter is intended to bring the experience of using a traditional photographic instrument to a modern smartwatch.

Rather than presenting exposure information as a conventional digital interface, it uses rotating scales, mechanical-inspired visual elements and clear exposure readouts to recreate the feel of a dedicated photographic light meter.

Film Meter 希望将传统摄影仪器的使用体验带到现代智能手表上。

它没有采用常见的数字化菜单式界面，而是通过旋转刻度、机械仪表风格的视觉元素，以及醒目的曝光组合显示，尽可能还原独立摄影测光表的操作感受。

---

## Compatibility | 兼容性

- Designed and primarily tested for Samsung Galaxy Watch5 Pro.
- Built for Wear OS.
- Other Wear OS watches may be supported depending on their ambient light sensor hardware and software compatibility.

- 主要面向三星 Galaxy Watch5 Pro 设计和测试。
- 基于 Wear OS 开发。
- 其他 Wear OS 手表能否正常使用，取决于其环境光传感器硬件及系统兼容性。

## Disclaimer | 使用说明

Film Meter estimates light levels using the smartwatch's ambient light sensor. Sensor placement, wrist orientation, clothing, shadows and surrounding objects may affect readings.

For best results, hold the watch in a position representative of the light falling on the subject. The displayed exposure values should be treated as practical estimates rather than laboratory-calibrated measurements.

Film Meter 使用智能手表的环境光传感器估算亮度。传感器位置、手腕朝向、衣物遮挡、阴影以及周围物体均可能影响测量结果。

---
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
