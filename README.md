# Delivio (Customer App)

Delivio is the high-performance Customer application of the 4-product Delivio Platform:
1. **Delivio Customer** (This app)
2. **Delivio Partner / Merchant**
3. **Delivio Rider**
4. **Delivio Admin**

Built with Kotlin, Jetpack Compose, Material 3, Firebase (Authentication, Firestore, Storage), and Google Maps Compose.

---

## 📱 Product Architecture & Features

- **Yellow Brand Identity**: Modern #FFC107 yellow styling with high contrast text, warm accents, and instant light/dark mode switching via DataStore.
- **5-Tab Navigation**: Shop (`/shop`), Search (`/search`), Home (`/home` - center), Cart (`/cart`), Profile (`/profile`).
- **4 Delivery Service Modes**:
  - **Food**: Customer -> Merchant -> Rider delivery flow. Live categories, availability toggles, ratings, ETA.
  - **Convenience**: 24/7 groceries, fresh essentials, snacks.
  - **Parcel (Padala)**: Customer -> Rider courier flow with sender/recipient details, package size (S/M/L/XL), weight in kg, fragile toggle, and coordinate pin-dropping.
  - **Pabili (Buy-For-Me)**: Customer -> Rider errand shopping flow with custom item checklist, estimated totals, store location, and final rider receipt updating.
- **Real-Time Order Tracking**:
  - Floating draggable tracker visible across screens when an order is active.
  - Status progression driven directly by Firestore snapshot listeners.
  - Dynamic service-specific status timeline (`DelivioStatusConstants.kt`).
  - Google Maps route display with live rider location coordinates (`orders.riderLocation`).
  - Cancel capability available strictly during `"Placed Order"` status.
- **Customer Profile & Address Book**:
  - Name and phone editable with immediate Firestore synchronization.
  - **Immutable Email**: The email address is strictly read-only on the client side with a lock icon. *Notice*: Any Firebase Auth email address change must be performed by the Admin platform via Firebase Admin SDK / Cloud Functions to maintain platform integrity.
  - Interactive Address Book subcollection (`users/{uid}/addresses`) with default selection, labels (Home, Work, Other), and custom manual coordinate setter.

---

## 🚀 Firebase Setup & Deployment Guide

### 1. Create Firebase Project
1. Navigate to the [Firebase Console](https://console.firebase.google.com/).
2. Click **Create a project** and name it `delivio` (or your preferred project ID).
3. Enable **Google Analytics** (optional).

### 2. Enable Authentication
1. Go to **Build** > **Authentication** > **Sign-in method**.
2. Enable the **Email/Password** provider (Email link passwordless sign-in optional).
3. Save settings.

### 3. Add Android Application
1. In Project Settings, click **Add app** > **Android**.
2. Package name: `com.example` (or your configured applicationId from `app/build.gradle.kts`).
3. Download the generated `google-services.json` and place it in the `/app` folder (`app/google-services.json`).

### 4. Configure Google Maps & Places API
1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Enable **Maps SDK for Android** and **Places API (New)**.
3. Generate an API Key and restrict it to Android apps with your SHA-1 fingerprint and package name.
4. Add your API key into your AI Studio Secrets panel or local `.env` file:
   ```properties
   MAPS_API_KEY=YOUR_ACTUAL_GOOGLE_MAPS_API_KEY
   ```

### 5. Deploy Firestore Security Rules & Composite Indexes
Install the Firebase CLI and run:
```bash
npm install -g firebase-tools
firebase login
firebase use YOUR_FIREBASE_PROJECT_ID

# Deploy security rules and composite indexes
firebase deploy --only firestore:rules,firestore:indexes
```

### 6. Run Initial Firestore Seed Data
A pre-configured seeding script is provided in `/seed`:
1. In the Firebase Console, go to **Project Settings** > **Service accounts**.
2. Click **Generate new private key** and save the downloaded file as `seed/serviceAccountKey.json` (this file is ignored in `.gitignore`).
3. Run the one-time Node.js seeder:
   ```bash
   cd seed
   npm install firebase-admin
   node seed.js
   ```
This populates:
- Active city: **Puerto Princesa** (with center coordinates and radius)
- Delivery rates document (`deliveryRates/puerto-princesa`) with tiered pricing for Food, Convenience, Parcel, and Pabili.
- 3 Merchants (Palawan Burger Shack, Haven Pizza & Wings, Delivio 24/7 Express Mart) with subcategories and items.
- Live active promotional banners.

---

## 🛠️ Building & Running

### Option A: Download APK from GitHub Actions (No local Android Studio needed)

1. **Push / Export project to GitHub**.
2. On GitHub, navigate to the **Actions** tab in your repository.
3. The **Build Delivio APK** workflow runs automatically on every commit. You can also trigger it manually by selecting **Build Delivio APK** on the left and clicking **Run workflow**.
4. When the build completes with a green checkmark (usually ~2–3 minutes):
   - Click on the workflow run.
   - Scroll down to the **Artifacts** section at the bottom.
   - Click **`Delivio-Debug-APK`** to download the ZIP file.
5. Extract the ZIP file to find `app-debug.apk` ready to install on your Android device!

### Option B: Build Locally with Android Studio

1. Open this project directory in **Android Studio Ladybug / Hedgehog** (or newer).
2. Allow Gradle to synchronize dependencies.
3. Build or run `:app` on your Android device or emulator (Android 7.0+ / API 24+):
   ```bash
   ./gradlew assembleDebug
   ```
   The output APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.
