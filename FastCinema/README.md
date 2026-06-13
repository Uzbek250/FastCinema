# FastCinema Browser — Build Qo'llanmasi

## Loyiha tuzilmasi
```
FastCinema/
├── app/
│   ├── src/main/
│   │   ├── java/com/fastcinema/
│   │   │   ├── FastCinemaApp.kt
│   │   │   ├── browser/
│   │   │   │   ├── AdBlocker.kt
│   │   │   │   ├── BrowserActivity.kt
│   │   │   │   └── FastWebViewClient.kt
│   │   │   ├── sniffer/
│   │   │   │   └── VideoSniffer.kt
│   │   │   ├── downloader/
│   │   │   │   ├── DownloadTask.kt
│   │   │   │   ├── DownloadManager.kt
│   │   │   │   ├── DownloadService.kt
│   │   │   │   └── MultiSegmentDownloader.kt
│   │   │   ├── player/
│   │   │   │   └── PlayerActivity.kt
│   │   │   ├── cast/
│   │   │   │   └── CastManager.kt
│   │   │   └── ui/
│   │   │       └── SplashActivity.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 1-QADAM: GitHub ga yuklash

```bash
cd /workspace
git clone https://github.com/SENING_USERNAME/FastCinema.git
# yoki yangi repo yarating va fayllarni ko'chiring
```

---

## 2-QADAM: Gitpod da build

### Gitpod ochish
1. https://gitpod.io ga kiring
2. GitHub repo URLini kiriting
3. Workspace ochilishini kuting

### Terminal da:
```bash
# Java versiyasini tekshirish
java -version  # 17 bo'lishi kerak

# Gradle wrapper yaratish (agar yo'q bo'lsa)
gradle wrapper --gradle-version=8.6

# Build qilish
./gradlew assembleDebug

# APK qayerda?
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 3-QADAM: APK ni telefoningizga yuborish

### Variant A — GitHub Releases
```bash
# APK ni artifacts sifatida yuklash
```

### Variant B — adb (USB orqali)
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Variant C — To'g'ridan Gitpod dan yuklash
Gitpod da fayl menejeri orqali APK ni yuklab oling

---

## MUHIM ESLATMALAR

### Ruxsatlar
- `SYSTEM_ALERT_WINDOW` — birinchi marta so'ralganda ruxsat bering
- `Notifications` — Android 13+ da so'raladi
- `Storage` — yuklash uchun kerak

### Cast ishlashi uchun
- Telefon va TV bir xil Wi-Fi da bo'lsin
- Google Home ilovasi o'rnatilgan bo'lsin
- TV da Chromecast yoki Android TV bo'lsin

### Video Sniffer eslatmasi
- Video boshlanganidan keyin 2-5 soniya kuting
- Panel ekran pastida paydo bo'ladi
- Ba'zi saytlar JavaScript bilan videoni yashiradi —
  bunday hollarda saytning "embed" versiyasiga o'ting

---

## Xatolar va Yechimlar

### "Duplicate class" xatosi
```bash
./gradlew assembleDebug --stacktrace
# Conflict bo'lgan kutubxonani build.gradle.kts da exclude qiling
```

### Cast SDK xatosi
CastManager.kt dagi try/catch — Cast bo'lmasa ham ilova ishlaydi

### DLNA
Ushbu versiyada Google Cast bor.
DLNA kengaytirish uchun Cling kutubxonasini qo'shing (ixtiyoriy).

---

## Keyingi bosqichlar (v2.0)

- [ ] Downloads sahifasi (RecyclerView + progress)
- [ ] Bookmarks / Tarixcha
- [ ] Incognito rejim
- [ ] Qo'shimcha AdBlock filtrlari (EasyList API)
- [ ] DLNA (Cling) integratsiyasi
- [ ] M3U8 segment yuklash (HLS to MP4)
