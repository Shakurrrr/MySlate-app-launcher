# MySlates App Launcher

A custom Android launcher built with Kotlin that provides a minimalist interface designed for corporate or educational tablets. The launcher features a clean time/date/weather display and a swipe-up gesture to reveal only whitelisted apps.

---

## Features

-  **Minimalist Home Screen**  
  Displays time, date, and weather with a gradient background.

-  **App Whitelisting**  
  Only pre-approved apps are visible and accessible to the user.

-  **Swipe-Up App Drawer**  
  Pull up from anywhere on the screen to access allowed apps via a smooth animated drawer.

-  **Blur Overlay**  
  Visually separates foreground drawer from the background with a semi-transparent blur.

-  **Live Time and Date**  
  Continuously updates in real time using `Handler`.

---

##  Architecture

- **Language:** Kotlin  
- **UI:** ConstraintLayout, FrameLayout  
- **Gesture Detection:** `GestureDetector.SimpleOnGestureListener`  
- **App Discovery:** Uses `PackageManager` to find installed apps  
- **Animation:** `AccelerateDecelerateInterpolator` for smooth transitions

---

 Configuration

✅ Whitelisted Apps
You can modify the allowed apps in MainActivity.kt:

private val allowedApps = listOf(
    "com.ATS.MySlates.Parent",
    "com.ATS.MySlates",
    "com.ATS.MySlates.Teacher",
    "com.adobe.reader"
)
Only these apps will appear in the swipe-up drawer.


📂 Project Structure

MySlatesAppLauncher/
├── app/
│   ├── src/main/java/com/myslates/launcher/
│   │   ├── MainActivity.kt
│   │   ├── AppAdapter.kt
│   │   └── AppObject.kt
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   └── drawable/
│   │       ├── drag_handle.xml
│   │       ├── gradient_background.xml
│   │       └── icons and images...
├── AndroidManifest.xml
└── README.md

📦 Dependencies
No third-party libraries required — the app uses only Android SDK components for maximum control and performance.


🛡️ License
This project is licensed under the MIT License.

👨‍💻 Author
MySlates Engineering Team
For inquiries, contact: support@myslates.example.com



---

Let me know if you'd like to:
- Include actual screenshot image links
- Add Gradle or device setup instructions
- Add versioning or CI/CD info for deployment to MDM-managed devices


