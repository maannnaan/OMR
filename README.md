# OMR Scanner — Android app

A native Android app for teachers. Design and print OMR (bubble) answer sheets, scan a stack of filled sheets with the phone camera, grade them against set-wise answer keys, review the few unclear sheets, and export results. It also includes a CamScanner-style document scanner for papers and ID cards with PDF export.

Kotlin · Jetpack Compose (Material 3) · CameraX · OpenCV 4.10 · Google ML Kit Document Scanner · Room · Retrofit

---

## Build and run

**Requirements:** Android Studio Koala (2024.1) or newer, JDK 17 (bundled with Android Studio), and a phone running Android 8.0 (API 26) or newer, with Google Play services. The document scanner needs Play services.

1. Open Android Studio → **File → Open** → choose this `OMRScanner` folder.
2. Let Gradle sync. The first sync downloads Gradle 8.9 and all libraries, including OpenCV (~30 MB) and ML Kit.
3. Connect a phone with USB debugging on, then press **Run ▶**.

From the command line:

```bash
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on a connected phone
./gradlew test                   # unit tests
```

**No Android Studio? Let GitHub build it.** Push this folder to a GitHub repository (as the repository root). The included workflow `.github/workflows/build-apk.yml` builds the APK on GitHub's servers: open the **Actions** tab → the latest **Build APK** run → download **OMRScanner-apk** under *Artifacts*. Unzip it to get `app-debug.apk` and install it on your phone.

For a release build, create a signing key (**Build → Generate Signed App Bundle / APK**) and run `./gradlew bundleRelease`.

> This project was written without access to Google's Maven servers, so it has **not been compiled yet**. All sources pass a Kotlin syntax check, the grading and geometry logic is unit-tested, and the OMR algorithm was tested on synthetic photos (see `tools/`). The first Android Studio sync may still flag small API mismatches. They are usually one-line fixes that the IDE suggests.

---

## Features

### OMR grading
| Screen | What it does |
|---|---|
| **Exams** | Lists exams with progress, a "needs your attention" list (missing answer keys, sheets to review), and **New exam**. |
| **Exam hub** | Setup checklist (Students → Answer sheet → Answer key → Grading rules), scan button, results, and a To review / All scanned / Not yet list. Export CSV. |
| **Answer sheet** | Questions (5–100), options (2–6), labels (A B C / ক খ গ / 1 2 3), English or Bangla, roll grid (0–8 digits), set-code grid, name fields. Live preview. **Print N copies** builds an A4 PDF to print or share. Structural settings lock once sheets are scanned. |
| **Answer key** | Set-wise. **Enter manually** (tap the correct bubble; tap again to clear; add set codes) or **From database**: search the question bank and fetch every set at once (hundreds of sets supported). Checks that the paper fits the printed sheet. |
| **Scanner** | CameraX preview. OpenCV finds the 4 corner squares, outlines the sheet live, estimates tilt, and refuses photos steeper than the limit (default 25°). It uses the orientation mark to read turned or upside-down sheets correctly, and captures automatically when steady (or with the shutter in manual mode). It waits for each sheet to leave the frame before reading the next, so you can flip through a stack. |
| **Review** | Per-question marks with the correct answer ringed. Tap to override a mark. Fix a wrong, repeated or double-filled roll (likely students suggested), or an unreadable set code (suggestions ranked by the digits read, then by score). View the straightened scan, then approve. |
| **Grading rules** | Two bubbles on one question: **count as wrong** (board rule) or **send to review**. Erased marks (much lighter than the darker one) are ignored. Blanks score 0. |
| **Insights** | Mean, median, high, low, score distribution, and question analysis (hardest first, most common wrong option). |

### Document scanner (Docs tab)
- **Scan document** opens Google's ML Kit scanner: live edge detection, auto-capture, crop handles, perspective correction and background removal, on-device.
- **Scan ID card** captures the front and then the back, and puts both on **one A4 page at true card size** (85.6 × 54 mm), so a printout works as a photocopy.
- **Document editor:** rename, add pages, add an ID card, reorder pages with ‹ ›, reverse the order, delete.
- **Page editor:** Original / Enhanced / Grayscale / B & W (OpenCV adaptive threshold) filters, rotate, delete.
- **Export PDF:** page size (A4, Letter, Fit to scan), quality, page numbers. Then **Share**, or **Save** to `Downloads/OMR Scanner`.

### Settings
Capture mode, maximum tilt, erased-mark threshold, question-bank server URL, and the school name printed on new sheets.

---

## How OMR reading works

```
camera frame ─▶ grayscale ─▶ adaptive threshold ─▶ all contours
            ─▶ keep solid squares (fill of rotated box ≥ 0.86 — bubbles are circles at ≈0.79)
            ─▶ the 4 similar squares forming the largest quad = corner markers
            ─▶ small square nearest a marker = top-left (handles 90°/180°/270°)
            ─▶ perspective transform to the 1000 × 1414 sheet space
            ─▶ divide by blurred copy (removes shadows)
            ─▶ sample each bubble at its printed position
            ─▶ darkness relative to the row's emptiest bubble:
                 none ≥ 0.28 → blank · one → answer
                 two → lighter < erase ratio × darker ? erased (ignored) : double
```

`omr/SheetLayout.kt` is the single source of truth for where every bubble is. **The same object prints the sheet (`SheetPdf`) and reads it (`OmrReader`)**, so they cannot drift apart.

### Tuning
`tools/omr_simulation.py` is a line-by-line Python port of the detector and reader. It renders a filled sheet, photographs it synthetically (tilt, turn, upside down, shadow, noise) and checks every mark:

```bash
pip install opencv-python-headless numpy
python3 tools/omr_simulation.py
```

All 7 scenarios read perfectly with the shipped thresholds. Change `FILLED`, `PRESENT` or the detector filters there first, then copy the change to `OmrDetector.kt` / `OmrReader.kt`.

### Printing tips
- Print at **100 % / actual size** on A4 with a laser printer. Don't use "fit to page".
- Keep the four black corner squares and the small square next to the top-left one clear of writing.
- Students should fill bubbles fully with a black or dark-blue pen.

---

## Question bank API

With no server URL set, the app uses a built-in **sample bank**, so everything works offline. To use your own database, set **Settings → Question bank → Server URL** and implement:

```
GET {url}/papers?q=physics
→ [ { "id": "qb1", "title": "Physics · Unit 3", "code": "QB-PHY-0311", "subject": "Physics",
      "questions": 30, "options": 4, "setCount": 320, "firstSet": "101", "lastSet": "420",
      "author": "Md. Rahman", "updated": "Nov 5, 10:42 AM" } ]

GET {url}/papers/{id}
→ { "id": "qb1", "title": "…", "code": "QB-PHY-0311", "questions": 30, "options": 4,
    "sets": { "101": [2,0,3,1,…], "102": [1,1,0,3,…], … } }      // option index per question, 0 = A
```

The client lives in `data/bank/QuestionBank.kt` (`RemoteQuestionBank`).

---

## Project structure

```
app/src/main/java/com/omrscanner/app/
├── OmrApp.kt                 Application: OpenCV init, dependency container, first-run demo exam
├── MainActivity.kt
├── omr/                      ── the OMR engine ──
│   ├── Model.kt              Layout, Mark, MarkCodec, KeyCodec, Grading
│   ├── SheetLayout.kt        geometry of the printed sheet (shared by printing and reading)
│   ├── SheetPdf.kt           printable A4 sheets + preview
│   ├── OmrDetector.kt        corner markers, orientation, tilt
│   ├── OmrReader.kt          perspective correction + bubble reading
│   └── FrameUtils.kt         CameraX frame → OpenCV Mat
├── docs/                     ── document scanner ──
│   ├── ImageOps.kt           filters, rotation, ID card page
│   └── PdfExporter.kt        multi-page PDF
├── data/
│   ├── db/                   Room entities, DAOs, database
│   ├── bank/QuestionBank.kt  remote API + sample bank
│   ├── ExamBundle.kt         grading questions the screens ask
│   ├── Repositories.kt       exams, sheets, keys, documents, CSV export
│   ├── FileStore.kt          storage, share, save to Downloads
│   └── AppSettings.kt
└── ui/
    ├── AppRoot.kt            navigation + bottom bar (Exams · Insights · Scan · Docs · Settings)
    ├── AppViewModel.kt
    ├── components/Common.kt  shared UI pieces
    ├── theme/Theme.kt        warm paper + ink palette
    ├── exams/                exams, hub, students, rules, sheet builder, answer key, review, insights
    ├── scan/OmrScanScreen.kt camera + live detection
    ├── docs/                 documents, ML Kit launcher, editor, page editor, export
    └── settings/             settings, question bank browser
tools/omr_simulation.py       algorithm test bench (Python + OpenCV)
```

## Data and privacy
Everything is stored on the phone: a Room database plus image files under the app's private storage. Nothing leaves the device unless you share or export a file, or connect a question-bank server.
