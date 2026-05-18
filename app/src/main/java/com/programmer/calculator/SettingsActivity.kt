package com.programmer.calculator

import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val exit = findViewById<TextView>(R.id.btnBack)

        exit.setOnClickListener {
            finish()
        }

        // Инициализация выбора темы через ThemeManager.allThemes + displayName()
        var selectedTheme = ThemeManager.getSavedTheme(this)
        val themeGroup = findViewById<RadioGroup>(R.id.themeRadioGroup)

        // Динамически создаём RadioButton для каждой темы из allThemes
        ThemeManager.allThemes.forEach { themeKey ->
            val rb = RadioButton(this).apply {
                id = themeKey.hashCode()
                text = ThemeManager.displayName(themeKey)   // используем displayName()
                textSize = 15f
                setPadding(8, 16, 8, 16)
            }
            themeGroup.addView(rb)
            if (themeKey == selectedTheme) themeGroup.check(rb.id)
        }

        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = ThemeManager.allThemes.firstOrNull { it.hashCode() == checkedId }
                ?: ThemeManager.THEME_DARK
            if (newTheme != selectedTheme) {
                selectedTheme = newTheme
                ThemeManager.saveTheme(this, newTheme)
                Snackbar.make(themeGroup, "Тема изменена — перезапустите приложение", Snackbar.LENGTH_LONG)
                    .setAction("Перезапустить") {
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                    }.show()
            }
        }
    }
}