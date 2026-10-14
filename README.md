# SawerOverlay - TikTok Live Overlay Android (Stealth Mode) 🔴

> Aplikasi overlay Saweria / Teks untuk Live TikTok di Android. Overlay bisa diedit, auto muncul pas live, **di HP lu bersih ga nutupin kamera, tapi di penonton keliatan**, dan konsisten di semua resolusi layar.

## 🔥 Fitur yang lu minta

1. **Overlay Saweria & Teks Custom**
   - Drag & drop di HP, pinch resize
   - Font TikTok style (Bebas Neue), Poppins, Inter
   - Gradient TikTok (cyan-pink), Fire, Solid

2. **Khusus Android & TikTok Live**
   - Vertical 9:16 (1080x1920) full HD - sesuai TikTok
   - Safe Area biar gak ketutup UI TikTok (like, comment, gift)

3. **Stealth Mode - Kunci Utama**
   - Masalah: Kalau pake overlay biasa (SYSTEM_ALERT_WINDOW), penonton GAK LIAT, cuma lu yang liat nutupin layar
   - Solusi gua: App ini streaming langsung via **RTMP ke TikTok**, bukan via app TikTok
   - Hasil: 
     - `PreviewCanvas` (di HP lu) = BERSIH, tanpa overlay stealth
     - `ProgramCanvas` (yang dikirim ke TikTok) = ADA OVERLAY
   - Toggle: Per layer ada switch "Hanya di Output Live (Stealth)"

4. **Konsisten All Resolusi**
   - Semua posisi disimpan sebagai **normalized 0.0 - 1.0** (persen), bukan pixel
   - Contoh: x=0.05 = 5% dari kiri, di HP 720p atau 1080p tetap 5%
   - Canvas selalu 1080x1920, di-render ulang sesuai layar

5. **Auto Muncul Pas Live**
   - Setiap layer ada toggle "Auto Muncul Pas Live"
   - Pas klik MULAI LIVE, overlay otomatis visible

## 📱 Cara Kerja (Biar Paham)

### Kenapa harus RTMP?
TikTok app di Android tidak bisa di-inject overlay yang keliatan penonton. Satu-satunya cara agar penonton liat overlay adalah kamu yang jadi streamer-nya (bukan TikTok app).

Flow:
```
Kamera HP -> SawerOverlay App -> Composite + Overlay -> RTMP -> TikTok Server -> Penonton liat overlay
                |
                -> Preview bersih (tanpa overlay stealth) -> Lu liat di HP
```

### Kalau belum 1000 followers (gak dapet RTMP key)?
Pakai mode **Browser Source**:
1. Edit overlay di app ini
2. Klik COPY BROWSER SOURCE LINK
3. Buka TikTok Live Studio di PC
4. Add Source > Browser Source > Paste link
5. Live dari PC, tapi overlay tetap dari HP lu (bisa diedit live)

## 🚀 Cara Pakai - PWA Version (Langsung Jalan)

PWA sudah jadi di folder `/pwa` - bisa langsung lu buka di Android:

1. Buka `index.html` di Chrome Android
2. Add to Home Screen -> jadi APK-like
3. Edit overlay: drag posisi, ubah teks, warna
4. Tab SAWERIA: isi username saweria.co lu (contoh: `jamalstream`)
5. Tab STREAM: isi RTMP URL + Stream Key dari TikTok Live Center
6. Klik toggle 👁 PENONTON vs 📱 LU (STEALTH) untuk cek bedanya
7. Klik 🔴 MULAI LIVE

**Test Saweria:** Klik 🧪 TEST di tab Saweria - akan muncul alert + TTS

## 🛠️ Build Jadi APK Beneran

### Opsi 1: PWABuilder (5 menit, tanpa coding)
1. Hosting folder `pwa` ke Vercel / Netlify
2. Buka pwabuilder.com, paste URL
3. Build APK

### Opsi 2: Capacitor (Full RTMP)
```bash
cd saweroverlay
npm i -g @capacitor/cli
npx cap init SawerOverlay com.saweroverlay.live
# copy pwa ke www/
npx cap add android
npx cap open android
# Build di Android Studio
```

### Opsi 3: Native Project (Udah gua siapin)
Lihat `android-app/` - sudah ada:
- `MainActivity.kt` dengan CameraX + RTMP streaming
- `OverlayFilter` (OpenGL) yang render overlay hanya ke stream
- Stealth logic: preview bersih, program ada overlay
- Normalized coords

Build:
```bash
cd android-app
./gradlew assembleDebug
# APK ada di app/build/outputs/apk/debug/
```

## 🔌 Integrasi Saweria.co

Saweria overlay asli: `https://saweria.co/overlays/alerts?streamKey=XXX`

Di app ini gua polling:
```js
fetch(`https://saweria.co/api/donations/${username}`)
```

Format alert:
```
{name} baru sawer {amount}! - {message}
```

Fitur:
- Auto popup 8 detik
- TTS Indonesia (pakai Web Speech API)
- Log donasi
- Update layer Saweria otomatis

## 📐 Konsistensi Resolusi - Detail Teknis

Semua layer disimpan:
```json
{
  "x": 0.05, // 5% dari kiri
  "y": 0.07, // 7% dari atas
  "w": 0.9,  // 90% lebar
  "h": 0.08
}
```

Saat render:
```js
const xPixel = x * canvasWidth // canvasWidth bisa 1080 atau 720, tetap proporsional
```

Safe Area TikTok: inset 6% top, 4% sides, 18% bottom (area tombol gift/comment)

## 🎨 Editor

- Drag untuk pindah
- Drag pojok kanan bawah untuk resize
- Tap layer untuk edit
- Switch "Hanya di Output Live" = stealth
- Switch "Auto Muncul Pas Live" = auto show

## ❓ FAQ

**Q: Di HP gua overlay nutupin tombol TikTok?**
A: Aktifkan Stealth Mode Global di tab STREAM, atau per layer. Lu liat bersih, penonton liat overlay.

**Q: Bisa tanpa RTMP key?**
A: Bisa pakai Browser Source link ke TikTok Live Studio PC. Atau kalau tetap mau HP only, harus pakai screen capture + RTMP server tengah (butuh PC/server).

**Q: Saweria gak connect?**
A: Pastikan username benar. Saweria API kadang butuh streamKey. Isi Overlay URL dari dashboard Saweria > Overlay.

**Q: Support all Android?**
A: Min SDK 24 (Android 7.0). Tested di 720p sampai 1440p, semua konsisten karena normalized.

---

Built for TikTok Live Android - Jakarta Time. Good luck live-nya bre! 🔥
