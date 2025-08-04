A custom Android launcher developed in Kotlin, purpose-built for corporate or educational tablets. MySlates Launcher offers a polished, One UI-inspired experience with curated access control, dynamic UI elements, and gesture-based navigation—all while locking users into a controlled app environment.

🚀 Features
Modern Dock Layout
A professionally styled bottom dock with custom-colored icons, tap animations, and visually consistent padding.

Minimalist Home Screen
Displays large-format digital clock, date, and optional weather icon with a gradient background for visual clarity.

Swipe-Based Panels

Swipe up to reveal whitelisted apps.

Swipe left for Recent Apps panel.

Swipe right for Quick Tools (Settings, Wi-Fi toggle, etc.).

Swipe down for Search.

App Whitelisting
Only approved apps are visible and accessible, suitable for enterprise or classroom lockdown environments.

Animated Tap Effects
All icons use selector-based scale animations for interactive feedback.

No Status Bar Clutter
Clean interface optimized for focus and task simplicity.

🧱 Architecture
Layer	Implementation
Language	Kotlin
UI	ConstraintLayout, LinearLayout, FrameLayout
Gestures	GestureDetector.SimpleOnGestureListener
App Logic	PackageManager for app discovery
Animation	selector, scale, and layout transitions
UI Assets	Custom ImageView icons using .png and VectorDrawables

⚙️ Configuration
✅ Whitelisted Apps
You can modify this list in MainActivity.kt:

kotlin
Copy
Edit
private val allowedApps = listOf(
    "com.ATS.MySlates.Parent",
    "com.ATS.MySlates",
    "com.ATS.MySlates.Teacher",
    "com.adobe.reader",
    "com.android.settings",
    "com.android.dialer",
    "com.google.android.apps.messaging",
    "com.android.chrome",
    "org.chromium.chrome"
)
Only these apps appear in the swipe-up drawer and can be launched from dock icons.

🧭 UI Layout Overview
Section	Description
Top Panel	Time, Date, Weather
Center	Empty workspace (future widgets possible)
Bottom Dock	Fixed icons: Call, Message, Browser, Settings
Drawer Layer	Animated App Grid (Swipe Up)
Side Panels	Left: Recent Apps · Right: Quick Tools

🗂 Project Structure
pgsql
Copy
Edit
MySlatesAppLauncher/
├── app/
│   ├── src/main/java/com/myslates/launcher/
│   │   ├── MainActivity.kt
│   │   ├── AppAdapter.kt
│   │   ├── AppObject.kt
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   ├── drawable/
│   │   │   ├── icon_call_bg.xml
│   │   │   ├── icon_message_bg.xml
│   │   │   ├── icon_browser_bg.xml
│   │   │   ├── icon_settings_bg.xml
│   │   │   ├── drag_handle.xml
│   │   │   └── gradient_background.xml
│   │   └── mipmap/ or drawable-xxhdpi/ (custom icons)
├── AndroidManifest.xml
└── README.md
📦 Dependencies
⚠️ No third-party libraries used.
Built entirely using native Android SDK components to ensure maximum stability, offline compatibility, and full control over launcher behavior.

🧪 Tested On
Android 11 (API 30) – Emulators & Samsung Tablets

Android 12/13 – Real devices

Works well in kiosk/MDM-enforced environments

🛡️ License
This project is licensed under the MIT License.

👨‍💻 Author
MySlates Engineering Team
For inquiries or enterprise integrations, contact: support@myslates.com

