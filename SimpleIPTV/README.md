# SimpleIPTV – IPTV cho Android TV

Kotlin • Jetpack Compose • MVVM • Hilt • Media3 ExoPlayer • Navigation Compose • Coil • DataStore.

## Build
1. Cài Android Studio (Koala trở lên) + JDK 17.
2. File ▸ Open ▸ chọn thư mục `SimpleIPTV`, chờ Gradle sync (Android Studio tự tải Gradle 8.7 theo `gradle-wrapper.properties`).
3. Build ▸ Build APK(s). Chạy thử trên Android TV emulator hoặc cài qua adb: `adb install app-debug.apk`.
   Bản release được ký bằng khóa debug để cài ngay; hãy thay bằng keystore riêng khi phát hành.

## Điều khiển bằng remote
| Phím | Trang chủ | Màn hình xem |
|---|---|---|
| ↑ ↓ | chọn nhóm / kênh | ↑ = kênh kế, ↓ = kênh trước |
| → ← | sang cột kênh / nhóm | – |
| OK | phát kênh | hiện/ẩn overlay |
| Giữ OK hoặc Menu | thêm/bỏ Favorites | mở menu (Yêu thích, Chất lượng) |
| Channel +/− | – | kênh kế / kênh trước |
| Play/Pause | – | tạm dừng / tiếp tục |
| Back | thoát app | về danh sách kênh |

## Cấu trúc
```
app/src/main/java/com/example/iptv/
  IptvApp.kt, MainActivity.kt
  model/      Models.kt
  network/    NetworkModule.kt, XtreamApi.kt
  data/       PlaylistRepository.kt, SettingsStore.kt (DataStore)
  player/     PlayerManager.kt (ExoPlayer, reconnect, chất lượng)
  util/       M3uParser.kt, XmltvParser.kt, Text.kt
  viewmodel/  MainViewModel.kt
  ui/         AppNav, HomeScreen, PlayerScreen, SearchScreen, SettingsScreen, theme/, components/
```

## Ghi chú
- Cấu hình xuất/nhập nằm ở `Android/data/com.example.iptv/files/iptv_config.json`.
- Playlist được cache để mở app tức thì, sau đó tự cập nhật nền (có thể tắt trong Cài đặt).
- EPG chỉ giữ chương trình của kênh trong playlist, 24h tới, để RAM thấp.
- Mã chưa được biên dịch trong môi trường tạo ra nó (không có Android SDK), nên có thể cần sửa vài lỗi nhỏ khi sync/build lần đầu.
