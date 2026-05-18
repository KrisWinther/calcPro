package com.programmer.calculator

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

object ThemeManager {
    private const val PREFS_NAME = "settings"
    private const val KEY_THEME = "app_theme"

    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    // Все темы
    val allThemes = listOf(

        THEME_LIGHT,
        THEME_DARK
        )

    // Названия
    fun displayName(theme: String): String = when (theme) {
        THEME_LIGHT -> "Светлая"
        THEME_DARK -> "Тёмная"
        else -> "Тёмная"
    }

    // Сохранить выбранную тему
    fun saveTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_THEME, theme)
            }
    }

    // Получить сохранённую тему (по умолчанию синяя)
    fun getSavedTheme(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    // Применить тему к Activity — вызывать до setContentView()
    @JvmStatic
    fun applyTheme(activity: AppCompatActivity) {
        val theme = getSavedTheme(activity)
        val styleRes = when (theme) {
            THEME_LIGHT -> R.style.Theme_ProgrammerCalc_Light
            THEME_DARK -> R.style.Theme_ProgrammerCalc
            else -> R.style.Theme_ProgrammerCalc
        }
        activity.setTheme(styleRes)
    }
}