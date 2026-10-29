# Independent Playlist Resume & Batch Import Feature

An architectural extension to VibePlayer that enables users to import full media folders or select multiple media files from their phone into named playlists, with isolated, persistent playback resume points for every playlist.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following specific decisions were confirmed in Phase 1 clarification:
> - **Phone Import Method**: Selecting an entire folder via the Android Storage Access Framework (`ActivityResultContracts.OpenDocumentTree`) and multi-file picker (`ActivityResultContracts.OpenMultipleDocuments` or `GetMultipleContents`) to create a playlist instantly.
> - **Resume Interaction**: Each playlist card displays a prominent **"استئناف / Resume"** button showing the exact paused track name and timestamp (e.g. `استئناف: سورة البقرة عند 14:22`), allowing one-tap continuation from that exact millisecond.
> - **Isolated Playlist State**: Playing Playlist B or any other video/audio does **not** erase or alter the saved position of Playlist A. Each playlist independently remembers its active track index and exact playback millisecond.

---

### 1. Overview & Core Concept

- **What It Solves**: Users who listen to multi-part audiobooks, podcast series, lecture playlists, or study folders frequently switch between different lists or listen to music in between. Standard media players lose the user's place in the previous playlist.
- **The Solution**: Every playlist maintains its own independent playback checkpoint (`activeTrackId`, `lastPositionMs`, `totalTrackDurationMs`, `lastPlayedTimestamp`). When returning to any playlist, the user can either tap "Resume" to continue from that exact moment or "Play from Start".

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Importing Folder or Multiple Files from Phone**:
   - In the "Playlists" tab, tap **"استيراد من الهاتف (Import from Device)"**.
   - Options to either **"اختر مجلداً كاملاً (Select Folder)"** or **"تحديد عدة ملفات (Select Multiple Files)"**.
   - The user selects the folder or files. The app automatically creates a new playlist with the folder's name, extracts file names and durations, and displays them.
2. **Playing and Pausing within a Playlist**:
   - As the user plays tracks in Playlist A, progress updates in real time.
   - When the user pauses or switches to Playlist B, Playlist A's state is preserved with its exact track and timestamp.
3. **Resuming with Precision**:
   - On the playlist card in the Playlists tab, an elegant Neon Cyan badge and prominent Resume button appear:
     `استئناف: [اسم المقطع] • [الدقيقة:الثانية]`.
   - Tapping **"استئناف (Resume)"** immediately launches playback from that exact second.
   - Inside the playlist detail view, a primary **"استئناف التشغيل (Resume Playback)"** button is featured alongside **"بدء من البداية (Start from Beginning)"**.

#### Visual Polish & Layout
- Distinct glowing resume chips with playback progress bars on playlist cards.
- Clear visual indicator showing which playlist is currently playing vs. paused with a saved checkpoint.
- Full edge-to-edge support with Material 3 styling.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Storage Access Framework Folder URI & Multi-File Picker**:
  - *Chosen Approach*: `ActivityResultContracts.OpenDocumentTree` combined with `ActivityResultContracts.OpenMultipleDocuments`.
  - *Why*: Provides zero-permission, modern Android-compliant access to local storage directories on Android 10+ through Android 15/16 without broad storage permission dialogs.
- **Decision 2: Local Persistence of Checkpoints**:
  - *Chosen Approach*: In-memory reactive state synchronized with `SharedPreferences` / local JSON storage so playlist checkpoints survive app restarts.
  - *Why*: Ensures the user never loses their progress even if the app process is terminated in the background.

---

### 4. Technical Architecture & Component Mapping

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PlaylistsScreen                               │
│  - "استيراد من الهاتف" (Folder & Multi-File Picker Launcher)           │
│  - Playlist Cards with Independent "استئناف" Checkpoint Badges         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        MediaPlayerViewModel                            │
│  - importFolderUri(treeUri: Uri)                                       │
│  - importMultipleMedia(uris: List<Uri>)                                │
│  - playlistCheckpoints: Map<playlistId, PlaylistCheckpoint>            │
│  - resumePlaylist(playlistId: String)                                  │
│  - updateActivePlaylistProgress(playlistId, trackId, positionMs)       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
       ┌────────────────────────────┴───────────────────────────┐
       ▼                                                        ▼
┌──────────────────────────────┐         ┌──────────────────────────────┐
│       PlaybackManager        │         │   Checkpoint Persistence     │
│ - playAtPosition(item, ms)   │         │ - Saves (trackId, posMs)     │
│ - activePlaylistId tracking  │         │   per playlist to device     │
└──────────────────────────────┘         └──────────────────────────────┘
```

#### Core Data Entities
- `PlaylistCheckpoint`:
  - `playlistId: String`
  - `trackId: String`
  - `trackTitle: String`
  - `positionMs: Long`
  - `durationMs: Long`
  - `updatedAt: Long`
- `Playlist`:
  - `id: String`
  - `name: String`
  - `description: String`
  - `items: List<MediaItemData>`
  - `checkpoint: PlaylistCheckpoint?`
