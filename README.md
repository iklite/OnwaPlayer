#OnwaPlayer

OnwaPlayer is a lightweight Android media player built using the native MediaPlayer API, enhanced with custom UI components and additional utilities for a smoother and more flexible media experience.
📱 Key Features
Audio and video playback using Android MediaPlayer
Custom IkVideoView for video rendering and control
Custom waveform seek bar for media scrubbing
Custom dialog system (IkBeautifulDialog)
Built-in logging utility:
Logs to Logcat (android.util.Log)
Saves logs to the device Downloads folder
Peer-to-peer WiFi Direct media sharing with playback syncing
Supports Android API 23 to 35+
🧱 Tech Stack
Java (Android SDK)
Native MediaPlayer
Custom Views and UI components
Canvas-based drawing (waveform)
File-based logging system
Android WiFi Direct APIs
📂 Project Structure (high level)
Plain text
com.ikechi.studio.onwa.player
├── player/
├── view/
├── dialog/
├── utils/
├── activity/
└── adapter/
🚀 Installation
Bash
git clone https://github.com/iklite/OnwaPlayer.git
Open in Android Studio, sync Gradle, and run on a device or emulator (API 23+).
🔐 Permissions
The app may require:
Storage/media permissions (for reading local files)
WiFi permissions (for WiFi Direct sharing)
Foreground service permission (for playback stability)
📄 License
This project is licensed under the MIT License.
You are free to:
Use this software in your own projects
Modify it
Distribute it (including commercial use)
Conditions
You must include the original MIT license and give credit to the original author.
If you use parts of this project, your work must also remain open source.
You are not allowed to publish this project verbatim as-is under your own name without meaningful modification or originality.
🤝 Contribution
Contributions are welcome and encouraged.
The goal is to improve performance, stability, UI polish, and expand features such as media handling and sharing.
If you contribute improvements, you are free to:
Use your contributions in your own projects
Publish your own work as open source
Build derivative systems
Please ensure that:
You maintain proper attribution to the original project
You respect the license terms
You do not re-upload this project unchanged as your own work
👤 Author
GitHub: https://github.com/iklite⁠�
Project: OnwaPlayer
