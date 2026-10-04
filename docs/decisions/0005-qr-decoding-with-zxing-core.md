# 5. QR codes are decoded with ZXing `core`, not ML Kit

Date: 2026-10-03

Status: Accepted

## Context

The TOTP feature (#241) added a QR scanner for `otpauth://` enrolment codes, built on CameraX and
`com.google.mlkit:barcode-scanning` — the **bundled** ML Kit variant, chosen so the scanner would
work without Google Play services. It did, but it shipped a neural-network detector as native
code plus three TFLite models. Play Console flagged `v2.2.5.0-rc1` as significantly larger than
`v2.1.4.0`.

The headline `.aab` growth (7.9 MB → 18.8 MB) overstates the problem — it includes four ABIs and
a 74 MB R8 mapping file nobody downloads — but the per-device download genuinely doubled:

| arm64-v8a device, compressed | `v2.1.4.0` | `v2.2.5.0-rc1` | after this ADR (`bundleQa`) |
|---|---|---|---|
| estimated download | 3.09 MB | 6.29 MB | **3.39 MB** |
| of which ML Kit `libbarhopper_v3.so` | — | 2.1 MB | — |
| of which `assets/mlkit_barcode_models/*.tflite` | — | 0.6 MB | — |
| of which dex | 2.57 MB | 3.06 MB | 2.86 MB |

TOTP itself contributed nothing measurable: `TotpGeneratorImpl` is `javax.crypto.Mac`. The whole
increase was the barcode library. After the swap R8 keeps 41 ZXing classes, all under `qrcode.*`,
`common` or the root package, and the GMS class count is back at the `v2.1.4.0` baseline
(869 vs 871). (Measurement method: release-and-ci skill, "Bundle size".)

## Decision

**Decode QR codes with `com.google.zxing:core`, driving ZXing's `QRCodeReader` directly from the
CameraX `ImageAnalysis` luminance plane.** The library sits behind a one-method `QrCodeDecoder`
interface (`ui/qrScanner/`), injected into the scanner ViewModel and handed to `QrCodeAnalyzer`.

Three details of the implementation are deliberate:

- **`QRCodeReader`, not `MultiFormatReader`.** `MultiFormatReader` references every format's reader,
  so R8 keeps all of them; `QRCodeReader` lets it drop everything but the QR package.
- **The Y plane's `rowStride` is passed as ZXing's data width**, with the real width as the crop
  width. Devices pad rows; assuming `rowStride == width` decodes nothing on them. ZXing never reads
  past the final row's last pixel, so a buffer that ends there (which Camera2 allows) is safe.
  Both cases are pinned by `ZxingQrCodeDecoderTest`.
- **Frame rotation is ignored.** QR finder patterns make the code orientation-independent; the
  rotated-frame test proves no `rotationDegrees` plumbing is needed.

Decoding is synchronous on the camera executor, whereas ML Kit's `Task` listeners delivered on the
main thread. The analyzer's callbacks are therefore posted to the main executor by the composable,
because the success callback navigates.

## Alternatives rejected

| Option | Why not |
|---|---|
| `play-services-mlkit-barcode-scanning` (unbundled) | Removes the ~2.7 MB, but requires Google Play services on the device — a real exclusion for de-Googled phones, which a password manager disproportionately attracts — and breaks scanner UI tests on the `aosp-atd` managed device image. |
| `play-services-code-scanner` (Google Code Scanner) | Same Play services dependency, and it owns the UI, discarding the Compose viewfinder, torch control and rationale dialogs already built. |
| On-demand dynamic feature module for the scanner | Heavy machinery for ~3 MB, and `SplitInstall` does not work for the GitHub-distributed `SafeBox-qa.apk`. |
| Strip `x86`/`x86_64` ABIs | Shrinks the `.aab` file but not any real device's download; Play already serves one ABI. |
| Accept the size | Defensible in absolute terms (~6 MB), but the cost was being paid for capability the feature does not need. |

## Consequences

- Scanner robustness is classical-CV rather than ML: ZXing is less tolerant of blur, steep angles
  and damaged codes than ML Kit. For `otpauth` codes displayed crisp on a screen at enrolment it is
  adequate — Aegis, FreeOTP and andOTP use it for exactly this. If field reports contradict that,
  `QrCodeDecoder` is the seam to revisit; nothing above it changes.
- ZXing is in maintenance mode (bug fixes only; 3.5.4 released 2025-11). Acceptable for a format
  as frozen as QR, but note it when reviewing dependency updates.
- `QRCodeReader` returns one code per frame; ML Kit returned all. Irrelevant for single-code
  enrolment.
- The no-Play-services invariant and `aosp-atd` compatibility are preserved.
- The `QrCodeDecoder` seam makes the scan flow drivable from instrumentation tests via a Hilt test
  module that returns a canned URI, which was not possible with ML Kit's final `BarcodeScanner`.
