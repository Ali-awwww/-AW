# Implementation Plan - Video & Music Downloader Feature

This plan outlines the integration of a multi-platform media downloader into **مشغل الAW**, featuring background execution, progress tracking, and a geometric UI.

## 1. Foundation & Dependencies
- **Permissions:** 
    - `INTERNET`: For fetching media.
    - `POST_NOTIFICATIONS`: For background progress updates (Android 13+).
    - `WRITE_EXTERNAL_STORAGE`: For saving to public Downloads (with Scoped Storage handling).
- **Libraries:**
    - `androidx.work:work-runtime-ktx`: Background task management.
    - `com.squareup.okhttp3:okhttp`: Reliable file downloading.
    - `androidx.datastore:datastore-preferences`: Persistence for download history.

## 2. Media Extraction & Download Logic
- **Architecture:** Use `WorkManager` for reliable background downloading that survives app closure.
- **Worker Implementation (`DownloadWorker`):**
    - Perform GET requests with `OkHttp`.
    - Stream bytes to local storage to avoid memory issues.
    - Report progress back to `WorkManager` and update system notifications.
- **Platform Handling:**
    - **Direct Links:** Simple file download based on extension.
    - **Platform Links (YT, FB, IG, TikTok):** Implement a modular `LinkParser` that identifies the platform and provides format options. *Note: Actual scraping logic will use high-level pattern matching and generic extraction strategies to maintain stability.*

## 3. UI Implementation (Geometric Theme)
- **Downloader Tab:**
    - **URL Bar:** Rectangular input field with "Paste" action.
    - **Action Button:** Sharp-edged button with mathematical grid background.
    - **Format Selector:** Isometric-style bottom sheet for choosing quality (720p, 1080p, MP3, etc.).
- **Progress List:**
    - Isometric cards showing active downloads with "Sharp" progress bars.
    - High-contrast labels (Cyan/Violet).
- **Navigation:**
    - Add a "Cloud Download" icon to the main navigation bar.

## 4. File Persistence & Integration
- **Public Storage:** Save files directly to `Environment.DIRECTORY_DOWNLOADS` via `MediaStore` API.
- **Library Sync:** Automatically refresh the app's internal media list (Videos/Music tabs) once a download completes.

## 5. Proposed Workflow
1.  **Step 1:** Configure permissions and dependencies in `AndroidManifest.xml` and `build.gradle.kts`.
2.  **Step 2:** Implement `DownloadWorker` and `DownloadManager` for core logic.
3.  **Step 3:** Update `MediaPlayerViewModel` and `AppTab` to support the new state.
4.  **Step 4:** Build `DownloaderScreen` with the geometric design.
5.  **Step 5:** Integrate with `MainActivity` navigation.
6.  **Step 6:** Final verification and styling adjustments.

---
**Geometric Theme Constraint:** 
All new UI components will use `RoundedCornerShape(0.dp)` and `DarkSurfaceBorder` with 1.dp thickness to match the architectural/geometric aesthetic.
