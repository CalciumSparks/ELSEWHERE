# Screenshot evidence

All screenshots are from the Android 15 emulator (`elsewhere_api35`, 1080 × 2400, 420 dpi), captured in the QA pass of 2026-10-28. The debug build shows `1.0.0-dev` in version labels; the signed release is `1.0.0`.

| File | Screen / evidence |
| --- | --- |
| `00-onboarding.png` | First-run intro, final layout with all four capability cards visible |
| `01-onboarding-shizuku.png` | Shizuku channel READY state, skip link hidden when ready |
| `02-onboarding-permissions.png` | Permission calibration with the final cache limitation wording |
| `03-map.png` | Map at People's Square, idle crosshair, bottom instrument deck |
| `07-settings.png` | Shizuku channel and capability checks |
| `07b-settings-diagnostics.png` | Full diagnostics, Hook / signal log, anti-mock boundary |
| `08-apps.png` | Installed app scan with SDK detection and backup badge |
| `09-app-detail.png` | Chrome app detail: cache size, manual cleanup, external snapshot |
| `13-motion.png` | Motion screen, speed gauge, rhythm preview |
| `14-motion-lower.png` | App-only sensor tiles and saved route section |
| `16-search.png` | English address search against Photon |
| `17-real-gps.png` | Real GPS card explicitly marked as cached |
| `21-environment.png` | Drift, preset selection, Wi-Fi source enabled |
| `22-environment-sources.png` | Radio fields and Location extras injection preview |
| `26-route-overlay.png` | Auto route cruise overlay with ring progress |
| `29-update-check.png` | GitHub Release check against CalciumSparks/ELSEWHERE; v1.0.0 published, app reports latest |

Screenshots are not retouched; only the device status bar is part of the capture. The route-cruise screenshot contains live injected coordinates produced by the app.
