🎵 OnwaPlayer A.K.A (Onwa Media Player Pro)

A powerful, feature-rich Android media player built with the native Android MediaPlayer framework and an extensive collection of custom UI components, visualization engines, media management tools, and Wi-Fi Direct synchronization technologies.

Onwa Media Player Pro is designed to deliver a smooth and immersive audio and video experience while maintaining excellent performance, modular architecture, and broad Android compatibility.

Whether you're listening to music, watching videos, managing playlists, editing metadata, analyzing playback statistics, or synchronizing media across devices, Onwa provides a complete media ecosystem built entirely in Java.

✨ Highlights

· Native Android MediaPlayer-based playback engine
· Audio and video playback support
· Wi-Fi Direct media sharing and synchronization
· Real-time playback synchronization across connected devices
· Custom OpenGL visualizers
· Advanced waveform seekbar
· Playlist management
· Metadata editor
· Playback statistics and analytics
· Custom equalizer interface
· Media library browsing and organization
· Beautiful custom dialogs and UI components
· Optimized for Android 6.0 through Android 15+

🎧 Audio Features

Music Playback

· High-performance audio playback
· Background playback support
· Queue management
· Repeat and shuffle modes
· Favorite tracks support
· Recently played tracking
· Playback history tracking

Audio Library

· Automatic media discovery
· MediaStore integration
· Intelligent storage scanning fallback
· Album-based browsing
· Artist-based browsing
· Playlist browsing
· Track metadata extraction

Album Artwork

· Embedded artwork extraction
· Artwork caching
· Optimized memory management
· Dynamic default artwork generation

Waveform Experience

· Custom waveform seekbar
· Precision scrubbing
· Real-time position tracking
· Smooth visual feedback

Audio Visualization

· OpenGL ES visualizer engine
· Beat detection system
· Real-time audio spectrum rendering
· Hardware-accelerated graphics

🎬 Video Features

Video Playback

· Native MediaPlayer video engine
· Hardware accelerated decoding
· Custom video controls
· Landscape support
· Playback position memory
· Fast seeking

Custom Video Components

· Custom IkVideoView implementation
· Advanced playback controls
· Gesture-driven interactions
· Optimized rendering pipeline

Video Library

· Automatic video discovery
· Thumbnail generation and caching
· Media metadata extraction
· Fast library loading

📡 Wi-Fi Direct Media Sync

One of the flagship features of Onwa Media Player Pro.

Media Sharing

· Device-to-device media transfer
· Peer discovery
· Local network communication
· Wireless media sharing

Playback Synchronization

· Real-time playback synchronization
· Multi-device media sessions
· Synchronized play/pause actions
· Shared playback control

Communication Panels

· Device management interface
· Network control panel
· Media synchronization dashboard
· Integrated chat interface

🎚️ Equalizer System

· Custom equalizer interface
· Frequency band controls
· Preset management
· Real-time audio adjustments
· User-friendly audio tuning experience

📝 Metadata Editor

Built-in metadata editing capabilities allow users to manage:

· Song title
· Artist information
· Album information
· Track information
· Media organization data

📊 Playback Statistics

Track and analyze listening habits with:

· Play count tracking
· Favorite tracking
· Usage analytics
· Playback history
· Library statistics

🎨 Custom UI Framework

A major portion of the application is powered by custom-built UI components.

Included Components

· IkBeautifulDialog
· WaveformSeekBar
· IkVideoView
· OpenGL Visualizer Views
· Custom RecyclerView implementations
· Animated panels
· Media synchronization interfaces
· Settings framework

⚙️ Settings & Tools

Comprehensive settings system providing control over:

· Playback behavior
· Media scanning options
· Synchronization settings
· UI preferences
· Performance tuning
· Application tools

🧱 Technical Overview

Core Technologies

· Java
· Android SDK
· Native MediaPlayer API
· OpenGL ES 3.0
· MediaStore
· Wi-Fi Direct APIs
· SQLite
· Canvas Rendering
· XML Layout System

Performance Optimizations

· Thumbnail caching
· Album art caching
· Database-backed media indexing
· Asynchronous media scanning
· Hardware-accelerated rendering
· Memory-conscious bitmap handling

Logging Utility

· Logs to Logcat (android.util.Log)
· Saves logs to device Downloads folder

📂 Project Structure (High Level)

```
com.ikechi.studio.onwa.player
├── player/
├── view/
├── dialog/
├── utils/
├── activity/
├── adapter/
└── model/
```

📸 Screenshots

🎧 Audio Experience

screenshots/audio/audio_gallery.png

screenshots/audio/audio_now_playing_0.png

screenshots/audio/audio_now_playing_1.png

screenshots/audio/audio_now_playing_2.png

screenshots/audio/music_queue.png

screenshots/audio/currently_playing_list.png

🎬 Video Playback

screenshots/video/video_playback_screen.png

screenshots/video/video_playback_controls.png

screenshots/video/video_gallery.png

screenshots/video/media_syncing_screen_video_landscape.png

screenshots/video/media_syncing_screen_video_panel.png

screenshots/video/media_syncing_screen_video_panel_at_50_percent.png

screenshots/video/media_syncing_screen_video_playing.png

📡 Media Syncing & WiFi Direct

screenshots/media_sync/media_syncing_and_sharing_playback_screen.png

screenshots/media_sync/media_syncing_screen_network_panel.png

screenshots/media_sync/media_syncing_screen_chat_panel.png

screenshots/media_sync/media_syncing_screen_audio_panel.png

screenshots/media_sync/media_syncing_screen_audio_panel_at_50_percent.png

screenshots/media_sync/media_syncing_screen_audio_panel_at_50_percent_2.png

screenshots/media_sync/media_syncing_screen_bottom_nav_visible.png

screenshots/media_sync/media_syncing_screen_bottom_nav_visible_1.png

⚙️ Settings & Tools

screenshots/settings/settings_page_0.png

screenshots/settings/settings_page_1.png

screenshots/settings/settings_page_2.png

screenshots/settings/settings_page_3.png

screenshots/settings/more_options_dialog.png

🎚️ Equalizer

screenshots/equalizer/equalizer_0.png

screenshots/equalizer/equalizer_1.png

📊 Stats

screenshots/stats/stats_page_0.png

screenshots/stats/stats_page_1.png

📝 Metadata Editor

screenshots/metadata/metadata_editor.png

🎛️ Playlist Management

screenshots/playlist/playlist.png

🚀 Installation

```bash
git clone https://github.com/iklite/OnwaPlayer.git
```

Then:

· Open in Android Studio
· Sync Gradle dependencies
· Run on physical device or emulator (API 23+)

🔐 Permissions

Depending on Android version, the application may request:

· Media access permissions
· Audio permissions
· Storage permissions
· Wi-Fi permissions (for Wi-Fi Direct sharing)
· Nearby device permissions
· Foreground service permissions (for stable playback)
· Network access permissions

These permissions are used solely for media playback, media discovery, device synchronization, and Wi-Fi Direct functionality.

📱 Supported Android Versions

Requirement Version
Minimum SDK 23
Target SDK 35+
Android Support Android 6.0 – Android 15+

📄 License

This project is licensed under the MIT License.

You are free to:

· Use
· Modify
· Distribute
· Include in commercial projects

Conditions:

· You must include the original MIT license
· You must give credit to the original author
· Derivative work should remain open source where possible
· You should not re-upload this project verbatim as your own without meaningful modification or originality

🤝 Contributing

Contributions are welcome.

Areas of interest include:

· Playback engine improvements
· UI/UX enhancements
· Visualization effects
· Waveform accuracy
· Wi-Fi Direct synchronization stability
· Performance optimization
· Code quality improvements
· Testing and bug fixing

Please ensure:

· Changes are focused and well-tested
· You respect the existing architecture
· You do not break playback or syncing features

👤 Author

Developer: Ikechi Studio

GitHub: https://github.com/iklite

Project: OnwaPlayer

⚠️ Project Status

Onwa Media Player Pro is actively evolving. New features, performance improvements, visual enhancements, and synchronization capabilities continue to be developed and refined. Structure, features, and documentation may improve over time as development continues.
