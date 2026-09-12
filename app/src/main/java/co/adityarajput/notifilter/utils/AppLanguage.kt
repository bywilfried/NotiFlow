package co.adityarajput.notifilter.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.core.content.edit
import co.adityarajput.notifilter.Constants.APP_LANGUAGE
import co.adityarajput.notifilter.Constants.SETTINGS
import java.util.Locale

const val LANGUAGE_SYSTEM = ""
const val LANGUAGE_ENGLISH = "en"
const val LANGUAGE_FRENCH = "fr"

fun Context.appLanguage(): String =
    getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        .getString(APP_LANGUAGE, LANGUAGE_SYSTEM)
        .orEmpty()

fun Context.withAppLanguage(): Context {
    val languageTag = appLanguage()
    if (languageTag.isBlank()) return this

    val locale = Locale.forLanguageTag(languageTag)
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}

fun Context.setAppLanguage(languageTag: String) {
    getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        .edit { putString(APP_LANGUAGE, languageTag) }
    findActivity()?.recreate()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
