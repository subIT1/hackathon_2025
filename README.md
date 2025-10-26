# Hackathon — Offline Disaster Mesh (Android)

An Android app that uses peer‑to‑peer, offline communication for disaster situations using Bluetooth
Low Energy (BLE). Users can quickly broadcast standardized incident codes to nearby devices and
visualize received reports on an OpenStreetMap‑based map.

> Built with Jetpack Compose, Material 3, osmdroid, and the Android BLE stack. No internet
> connection required while operating.

---

## Features

- Offline P2P messaging over Bluetooth Low Energy (BLE)
- Quick incident reporting using a predefined catalog of disaster categories
- Map with markers for received messages (OpenStreetMap via osmdroid)
- Live peer discovery and simple connection log
- Minimal UI built with Jetpack Compose (Material 3)
- Privacy‑friendly device ID shown only as a short prefix in the UI

---

## Screens

- Map
    - Shows markers for received messages that include GPS coordinates
    - Each marker displays the incident label resolved from the code, sender short ID, and timestamp
    - Floating Action Button opens a sheet to choose and send a disaster category
- Connections
    - Lists currently discovered peers and records simple connection events/logs

---

## High‑level Architecture

- BLE Layer
    - `ble/BleManager.kt` — orchestrates scanning/advertising and message sending
    - `ble/adv/BleAdvertiser.kt` — encapsulates BLE advertising details
    - `ble/util/MessageCodec.kt` — formats/parses compact BLE payloads
    - `ble/DeviceId.kt`, `ble/Peer.kt` — identity and peer representation
- Data Layer
    - `data/MessageStore.kt` — in‑memory store of sent/received messages
    - `data/ConnectionLogStore.kt` — simple log of connection events
- Domain/Model
    - `model/DisasterCatalog.kt` — list of standardized categories and helpers
    - `model/Models.kt` — `Message` and related data models
- UI (Jetpack Compose)
    - `ui/screens/AppScaffold.kt` — top‑level navigation/scaffold
    - `ui/screens/MapView.kt` — osmdroid map + message markers + send sheet
    - `ui/screens/ConnectionsView.kt` — peers and connection log
    - `ui/theme/*` — Material 3 theme setup

Map rendering uses `org.osmdroid:osmdroid-android` with OpenStreetMap data. Message markers are
derived from `Message` objects; labels are resolved via `DisasterCatalog` based on the transmitted
numeric code.

---

## How messaging works (short version)

- When you press the “Add” button on the map, a bottom sheet lets you pick a disaster category
- The app sends the selected numeric code as a compact BLE message to all discovered peers
- If the sender has a location fix, latitude/longitude are attached
- Receivers:
    - Decode the message, map the code to a human‑readable label, and display it
    - If coordinates are present, a marker is added to the map centered for recent messages
- Marker details show: label, sender short ID (first characters), timestamp, and optional `#code`

---

## Requirements

- Android Studio (Koala or newer recommended)
- Android SDK + Build Tools installed via SDK Manager
- JDK 17 (bundled with recent Android Studio versions)
- Physical Android devices for meaningful testing (emulators lack BLE advertising)

---

## Build & Run

1. Clone this repository
2. Open the project in Android Studio
3. Let Gradle sync finish
4. Connect a physical Android device and enable Bluetooth + Location
5. Run the `app` configuration from Android Studio
6. Grant runtime permissions when prompted
7. For best results, run the app on two or more devices nearby to see peer‑to‑peer messaging in
   action

> Tip: Keep the app in the foreground on multiple devices to observe discovery, logs, and message
> exchange.

---

## Permissions

Depending on your Android version, the app will request the following:

- Bluetooth
    - `BLUETOOTH`, `BLUETOOTH_ADMIN` (legacy; pre‑Android 12)
    - `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` (Android 12+)
- Location
    - `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` for scanning and for attaching GPS to
      messages

Background location is not strictly required for the core demo; the app primarily operates in the
foreground.

---

## Limitations & Notes

- BLE payload size is limited; messages are intentionally compact (numeric codes)
- Map data requires the osmdroid tile provider; cached tiles help offline usage once cached
- Performance and reliability depend on device BLE chipsets and OS power policies
- Designed for hackathon/demo purposes; not a production emergency system

---

## Key Files

- `app/src/main/java/com/example/hackathon/MainActivity.kt`
- `app/src/main/java/com/example/hackathon/ui/screens/AppScaffold.kt`
- `app/src/main/java/com/example/hackathon/ui/screens/MapView.kt`
- `app/src/main/java/com/example/hackathon/ui/screens/ConnectionsView.kt`
- `app/src/main/java/com/example/hackathon/model/DisasterCatalog.kt`
- `app/src/main/java/com/example/hackathon/ble/BleManager.kt`
- `app/src/main/java/com/example/hackathon/ble/util/MessageCodec.kt`
- `app/src/main/java/com/example/hackathon/data/MessageStore.kt`

---

## Acknowledgements

- OpenStreetMap data © OpenStreetMap contributors
- osmdroid — OpenStreetMap Android library
- Android Jetpack (Compose, Material 3)

---

## License

This project is provided for hackathon and educational use. If you plan to use or distribute it,
please add an appropriate open‑source license (e.g., MIT, Apache‑2.0) or contact the authors.

---

## Kurzüberblick (Deutsch)

Eine Android‑App für Offline‑Kommunikation in Katastrophenlagen per Bluetooth Low Energy. Nutzer
können standardisierte Ereigniscodes an nahe Geräte senden; empfangene Meldungen erscheinen als
Marker auf einer OpenStreetMap‑Karte.

- Offline‑Nachrichten über BLE, keine Internetverbindung nötig
- Meldungsauswahl über einen Katalog (`DisasterCatalog`)
- Karte (osmdroid) mit Markern inkl. Absender‑Kurz‑ID und Zeitstempel
- UI mit Jetpack Compose

Zum Testen die App auf mindestens zwei physischen Geräten starten, Berechtigungen erteilen,
Bluetooth + Standort aktivieren und eine Meldung über die Karte senden.
