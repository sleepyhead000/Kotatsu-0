package org.koitharu.kotatsu.reader.data

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import androidx.core.content.edit
import org.koitharu.kotatsu.R

enum class NovelReaderTheme(
	val background: Int,
	val foreground: Int,
	@StringRes val titleResId: Int,
) {
	LIGHT(Color.rgb(255, 255, 255), Color.rgb(26, 26, 26), R.string.novel_theme_light),
	SEPIA(Color.rgb(245, 236, 217), Color.rgb(59, 50, 41), R.string.novel_theme_sepia),
	DARK(Color.rgb(18, 18, 18), Color.rgb(227, 227, 227), R.string.novel_theme_dark),
}

class NovelReaderPreferences(context: Context) {

	private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

	var theme: NovelReaderTheme
		get() = NovelReaderTheme.entries.getOrElse(preferences.getInt(KEY_THEME, 0)) {
			NovelReaderTheme.LIGHT
		}
		set(value) = preferences.edit { putInt(KEY_THEME, value.ordinal) }

	var isSerif: Boolean
		get() = preferences.getBoolean(KEY_SERIF, false)
		set(value) = preferences.edit { putBoolean(KEY_SERIF, value) }

	var fontSizeSp: Int
		get() = preferences.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
			.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
		set(value) = preferences.edit {
			putInt(KEY_FONT_SIZE, value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))
		}

	companion object {
		private const val FILE_NAME = "novel_reader"
		private const val KEY_THEME = "theme"
		private const val KEY_SERIF = "font_serif"
		private const val KEY_FONT_SIZE = "font_size"

		const val DEFAULT_FONT_SIZE = 18
		const val MIN_FONT_SIZE = 12
		const val MAX_FONT_SIZE = 32
	}
}
