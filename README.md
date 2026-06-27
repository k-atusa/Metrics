# Metrics v1.1.0

project USAG: Metrics Vault android version

> Metrics is Password Manager disguised as a Unit Converter

## Convertable Units

| Types | US Units | Metric |
| :-- | :-- | :-- |
| Length | in, ft, yd, mi | cm, m, km |
| Weight | oz, lb | g, kg |
| Temperature | 'F | 'C |
| Volume | gal | L |
| Currency | USD($) | KRW(₩) |

## Preset numbers

- Default password: `0000`
- Disable vault: set `exchange rate` as odd integer
- ScreenPW is saved as plaintext. You must set RealPW differently.

## Build Executable

```bash
gradlew.bat clean
gradlew.bat [assembleRelease|assembleDebug]
cd android/app/build/outputs/apk/debug
```
