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

##  How to Build and Run

1. **Clone the Repository**

   ```bash
   git clone https://github.com/yourusername/myslates-launcher.git
   cd myslates-launcher
