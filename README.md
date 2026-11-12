<div align="center">

<img src="docs/logo_green.gif" alt="magen player" width="280"/>

# magen player

<p><b>مشغّل فيديو محلي لأندرويد — سريع، خفيف، وبدون إنترنت</b></p>
<p><sub>Local-first Android video player powered by libmpv</sub></p>

<p>
  <img src="https://img.shields.io/github/v/release/magen-gillan/magen-player?style=flat-square&color=00C853&label=latest%20release" alt="latest release"/>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/No%20Internet%20Permission-2E7D32?style=flat-square" alt="No Internet Permission"/>
  <img src="https://img.shields.io/github/license/magen-gillan/magen-player?style=flat-square&color=FBC02D&label=license" alt="license"/>
</p>

<br/>

<a href="https://github.com/magen-gillan/magen-player/releases/latest/download/magen-player-arm64-v8a.apk">
  <img src="docs/download-button.svg" alt="تحميل آخر نسخة APK" width="440"/>
</a>

<p><a href="https://github.com/magen-gillan/magen-player/releases">تصفّح جميع الإصدارات</a></p>

</div>

<div dir="rtl">

## ما هو magen player؟

مشغّل فيديو يعمل بالكامل على الجهاز، مبني على libmpv بواجهة Jetpack Compose. يكتشف الفيديوهات المخزّنة على الهاتف ويشغّلها دون أي اتصال بالإنترنت أو خدمات بعيدة — لا بثّ من المواقع ولا شبكات وسائط خارجية.

## الميزات

- تشغيل الفيديو المحلي عبر libmpv مع إيماءات قابلة للتخصيص، والتنقل بين الإطارات، والتكبير والتحريك.
- تصفّح مجلدات الجهاز، وتنظيم قوائم تشغيل محلية، وتتبّع الفيديوهات المشاهدة.
- وضع **الفيديوهات القصيرة** مع فرز فوري (الاسم بالترتيب الطبيعي أو الحجم أو تاريخ الإضافة) ومجلد **المفضلة** المحلي.
- واجهة زجاجية أنيقة وتبويبات قابلة للتخصيص ودعم ملفات الترجمة المحلية وتعيين نغمات HDR إلى SDR.
- **بدون إذن الإنترنت**: لا اتصال شبكي إطلاقًا داخل التطبيق.

## التحميل والتثبيت

1. اضغط زر التحميل الأخضر في الأعلى — ينزّل آخر نسخة `magen-player-arm64-v8a.apk` مباشرةً من الإصدار الأحدث.
2. افتح الملف على الهاتف واسمح بالتثبيت من مصادر غير معروفة عند الطلب.
3. إن كانت لديك نسخة أقدم فاحذفها أولًا ثم ثبّت النسخة الجديدة — توقيع التطبيق موحّد نهائيًا بدءًا من v5.5.4.
4. للتحقق من سلامة الملف قارن بصمة `SHA-256` مع ملف `.sha256` المرفق مع كل إصدار.

**متطلبات التشغيل:** أندرويد 8.0 فأحدث (minSdk 26) — معمارية `arm64-v8a` — حجم الملف حوالي 36 ميجابايت.

## آخر التحديثات — v5.5.4

- فتح **المفضلة** من وضع الفيديوهات القصيرة لم يعد يخرج من التطبيق؛ المفضلة تعمل بنفس وضع الفيديوهات القصيرة مع رسالة ودّية عند عدم وجود مفضلات.
- الفرز بالحجم أو تاريخ الإضافة أو الاسم يُطبّق **فورًا** عند الاختيار.
- الترتيب الطبيعي للاسم: 1، 2، … 10، 100، 800 بدل 1، 10، 100.
- لم تعد الفيديوهات تتخطى عرض بعضها أثناء جلب معلوماتها.
- أيقونة التطبيق تظهر **كاملة دون قصّ** (adaptive safe-zone).
- أسماء المجلدات في عرض الشبكة لم تعد تختفي خلف شريط التنقل السفلي.

## البناء من المصدر

```bash
./gradlew :app:assembleRelease
```

نسخة Release مُعدّة لمعمارية `arm64-v8a` للحصول على أصغر APK ممكن، وتتطلب توفّر تبعيات Gradle في الكاش المحلي للبناء دون اتصال.

## الترجمات

تُخزّن ترجمات الواجهة في `app/src/main/res/values*/strings.xml` — راجع `CONTRIBUTING.md` لسير العمل الخاص بالمساهمات المحلية.

## الشكر والاعتمادات

مبني على libmpv، ويستفيد من أفكار وشيفرات المشروعات: mpvEx وmpv-android وmpvKt وNext Player وGramophone — مع حفظ تراخيصها وإشعاراتها مع الشيفرة المصدرية.

## الترخيص

موزّع تحت رخصة Apache License 2.0 — انظر ملف `LICENSE`.

</div>
