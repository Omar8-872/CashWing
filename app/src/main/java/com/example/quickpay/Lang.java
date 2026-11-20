package com.example.quickpay;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

// Language of the app: Arabic or English. The choice is saved by Android and
// the screens are recreated with the matching strings (values / values-ar).
public class Lang {

    public static boolean isArabic() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty() && locales.get(0) != null) {
            return locales.get(0).getLanguage().equals("ar");
        }
        return Locale.getDefault().getLanguage().equals("ar");
    }

    // locale used for month and day names (Arabic names, but normal digits)
    public static Locale labelLocale() {
        if (isArabic()) {
            return Locale.forLanguageTag("ar-u-nu-latn");
        }
        return Locale.US;
    }

    // switch between Arabic and English
    public static void toggle() {
        String tag = "ar";
        if (isArabic()) {
            tag = "en";
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }
}
