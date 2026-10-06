package com.example.contentstudio.ui

data class AppLocale(val tag: String, val nativeName: String)

val appLocales = listOf(
    AppLocale("tr-TR", "Türkçe"),
    AppLocale("en-US", "English"),
    AppLocale("es", "Español"),
    AppLocale("pt-BR", "Português (Brasil)"),
    AppLocale("id", "Bahasa Indonesia"),
    AppLocale("hi", "हिन्दी"),
    AppLocale("ar", "العربية"),
    AppLocale("ru", "Русский"),
    AppLocale("ja-JP", "日本語"),
    AppLocale("fr-FR", "Français"),
    AppLocale("de-DE", "Deutsch"),
    AppLocale("pl-PL", "Polski"),
    AppLocale("ko-KR", "한국어"),
    AppLocale("zh-Hans", "简体中文"),
    AppLocale("zh-Hant", "繁體中文"),
    AppLocale("it-IT", "Italiano"),
    AppLocale("th-TH", "ไทย"),
    AppLocale("vi-VN", "Tiếng Việt"),
    AppLocale("fa", "فارسی"),
    AppLocale("sw", "Kiswahili"),
    AppLocale("ha", "Hausa"),
    AppLocale("ro", "Română"),
)
