# 🚗 VF5 CarStream

Ứng dụng **Android Auto** phát trực tuyến nội dung web (YouTube, VTV Go, FPT Play, Zing MP3, v.v.) tối ưu cho màn hình giải trí **8 inch của VinFast VF5 Plus 2025**.

---

## ✨ Tính năng

- 🎬 **WebView toàn màn hình** – Hiển thị web content qua VirtualDisplay lên màn hình xe
- ▶️ **YouTube & Video** – Hỗ trợ phát video toàn màn hình, không cần chạm
- 🔇 **Chặn quảng cáo** – Tích hợp sẵn adblock cho doubleclick, googlesyndication, v.v.
- 🎵 **AudioFocus** – Tự động tạm dừng khi có cuộc gọi, trợ lý ảo kích hoạt
- 🔖 **Bookmarks** – Quản lý trang yêu thích từ điện thoại, dùng ngay trên xe
- 💾 **Resume Last URL** – Tự động mở lại trang đang xem dở sau khi khởi động xe
- 🏠 **Home Launcher** – Trang chủ dark mode với các nút lớn: YouTube, YouTube Music, VTV Go, Zing MP3, FPT Play, Google

---

## 📋 Yêu cầu hệ thống

| Thành phần | Yêu cầu |
|---|---|
| Android Phone | Android 8.0+ (API 26+) |
| Android Auto | Phiên bản mới nhất |
| Màn hình xe VF5 | 8 inch, Android Automotive |
| compileSdk | 35 |
| targetSdk | 34 |

---

## 🚀 Cài đặt lên Android Auto

### Phương pháp ADB (Khuyến nghị cho Android 14/15/16)

```bash
# 1. Bật USB Debugging trên điện thoại
# 2. Kết nối cáp USB với máy tính
# 3. Chạy lệnh sau:
adb install -i "com.android.vending" app-debug.apk
```

> **Lưu ý**: Lệnh `-i "com.android.vending"` giả lập cài từ Google Play Store, 
> giúp ứng dụng vượt qua hạn chế hiển thị trên Android Auto của Android 14+.

---

## 🏗️ Build từ Source

### Yêu cầu
- Android Studio Hedgehog (2023.1.1) hoặc mới hơn
- JDK 17
- Android SDK 35

### Lệnh build
```bash
# Clone repo
git clone https://github.com/YOUR_USERNAME/vf5-carstream.git
cd vf5-carstream

# Build debug APK
./gradlew assembleDebug

# APK output
# app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ GitHub Actions CI/CD

Mỗi lần push lên nhánh `main`, GitHub Actions tự động:
1. Checkout source code
2. Cài JDK 17 (Temurin)
3. Build debug APK với `./gradlew assembleDebug`
4. Upload APK lên **Artifacts** (lưu 30 ngày)

Tải APK: **GitHub → Actions → Build VF5 CarStream APK → Artifacts → VF5-CarStream-APK**

---

## 📁 Cấu trúc dự án

```
VF5/
├── .github/
│   └── workflows/
│       └── build-apk.yml          # GitHub Actions CI
├── app/
│   └── src/main/
│       ├── java/com/vf5plus/carstream/
│       │   ├── VF5CarStreamService.kt    # Android Auto CarAppService
│       │   ├── CarStreamSession.kt       # Session tạo Screen
│       │   ├── CarStreamScreen.kt        # Screen + SurfaceCallback
│       │   ├── WebPresentation.kt        # WebView + VideoFullscreen + AdBlock
│       │   ├── BookmarksScreen.kt        # Màn hình danh sách bookmark trên xe
│       │   ├── BookmarkAdapter.kt        # RecyclerView adapter
│       │   ├── Bookmark.kt               # Data class
│       │   └── MainActivity.kt           # Cài đặt trên điện thoại
│       ├── res/
│       │   ├── layout/
│       │   │   ├── activity_main.xml
│       │   │   ├── item_bookmark.xml
│       │   │   └── dialog_add_bookmark.xml
│       │   ├── values/
│       │   │   ├── colors.xml
│       │   │   ├── strings.xml
│       │   │   └── themes.xml
│       │   └── xml/
│       │       └── automotive_app_desc.xml
│       └── AndroidManifest.xml
├── build.gradle.kts                # Root Gradle (Kotlin DSL)
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── README.md
```

---

## 🛡️ Giấy phép

MIT License – Dự án cá nhân, không liên kết chính thức với VinFast hoặc Google.
