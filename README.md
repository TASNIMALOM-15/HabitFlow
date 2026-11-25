# HabitFlow

Native Android (Kotlin + Jetpack Compose + Room) habit tracker. Offline, local-first, no subscription.

## APK পাওয়ার নিয়ম
1. এই প্রজেক্টের সব ফাইল (`.github` ফোল্ডারসহ) একটি GitHub repository-র root-এ আপলোড করো (`main` ব্রাঞ্চ)।
2. repository-র **Actions** ট্যাবে `Build APK` workflow নিজে চলবে (না চললে "Run workflow" চাপো)।
3. শেষ হলে run-এর নিচে **Artifacts → HabitFlow-APK** থেকে `app-release.apk` ডাউনলোড করে ফোনে ইনস্টল করো।

APK `app/hf-release.jks` কি দিয়ে সাইন করা হয়, তাই পরের আপডেটগুলো আগের অ্যাপের উপর সরাসরি ইনস্টল হবে।

## Structure
- `data/` Room database, Universal Habit Engine (`Engine.kt`), backup (JSON/CSV)
- `notify/` reminders (AlarmManager), boot rescheduling, home-screen widget
- `ui/` Compose screens (Home, Calendar, Stats, Categories, Settings, Editor)
- নতুন Tracking Type যোগ করতে: `HType` enum + `Engine` + `HabitCard.kt`/`EditorScreen.kt`-এ একটি করে branch।
