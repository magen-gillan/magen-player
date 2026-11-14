<p align="center">
  <img src="docs/logo.gif" width="150" alt="Magen animated logo">
</p>

<h1 align="center">Magen Player</h1>

<p align="center">
  <img src="docs/alisha.png" width="420" alt="Alisha character">
</p>

<p align="center"><strong>مرحبًا! أنا أليشا. شاهد فيديوهاتك المحلية بحرية، بسرعة، وبدون إنترنت.</strong></p>
<p align="center"><em>Hello! I’m Alisha. Watch your local videos freely, quickly, and completely offline.</em></p>

<p align="center">
  <a href="https://github.com/magen-gillan/magen-player/releases/latest/download/magen-player-arm64-v8a.apk"><img src="https://img.shields.io/badge/%E2%AC%87%EF%B8%8F_%D8%AA%D8%AD%D9%85%D9%8A%D9%84_%D8%A7%D9%84%D8%AA%D8%B7%D8%A8%D9%8A%D9%82-00C853?style=for-the-badge&logo=android&logoColor=white" alt="تحميل آخر إصدار"></a>
</p>

<p align="center"><a href="https://github.com/magen-gillan/magen-player/releases/latest">عرض جميع الإصدارات | View all releases</a></p>

---

## عن التطبيق | About

**العربية:** Magen Player مشغل فيديو محلي يعمل بالكامل على الجهاز، مبني على libmpv وJetpack Compose. يكتشف فيديوهاتك ويشغلها دون اتصال بالإنترنت أو بث خارجي، مع واجهة زجاجية سريعة وقابلة للتخصيص.

**English:** Magen Player is a local-first video player built with libmpv and Jetpack Compose. It discovers and plays your videos without internet access or remote streaming, with a fast, customizable glass-style interface.

## المميزات | Features

- تشغيل محلي قوي عبر libmpv مع إيماءات قابلة للتخصيص.
- Powerful local playback through libmpv with customizable gestures.
- التنقل بين الإطارات، والتكبير، والتحريك.
- Frame stepping, zooming, and panning controls.
- تصفح مجلدات الهاتف وإنشاء قوائم تشغيل محلية.
- Browse device folders and organize local playlists.
- تتبع الفيديوهات المشاهدة ووضع الفيديوهات القصيرة.
- Watched-video tracking and a dedicated Shorts mode.
- فرز فوري بالاسم الطبيعي أو الحجم أو تاريخ الإضافة، مع المفضلة.
- Instant sorting by natural name, size, or date, plus local favorites.
- دعم ملفات الترجمة المحلية وتحويل HDR إلى SDR.
- Local subtitle support and HDR-to-SDR tone mapping.
- بدون إذن الإنترنت؛ لا توجد اتصالات شبكية داخل التطبيق.
- No internet permission; the app makes no network connections.
- واجهة قابلة للتخصيص ودعم العربية والإنجليزية.
- Customizable interface with Arabic and English support.

## التثبيت | Installation

1. اضغط زر التحميل الأخضر أعلاه لتنزيل آخر APK لمعمارية arm64-v8a.
2. افتح الملف واسمح بالتثبيت من مصادر غير معروفة.
3. اختر فيديو من مكتبة الهاتف وابدأ المشاهدة.

1. Press the green button above to download the latest arm64-v8a APK.
2. Open the file and allow installation from unknown sources.
3. Choose a video from your device library and start watching.

**المتطلبات | Requirements:** Android 8.0+ (API 26+)، arm64-v8a | Current release: v5.5.4.

## البناء والترخيص | Build & License

```bash
./gradlew :app:assembleRelease
```

مرخص تحت Apache License 2.0. يعتمد على libmpv ومشروعات Android مفتوحة المصدر ذات الصلة.

Licensed under Apache License 2.0. Built with libmpv and related open-source Android projects.