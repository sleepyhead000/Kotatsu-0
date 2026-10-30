package org.koitharu.kotatsu.reader.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.DialogErrorObserver
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.model.getLocalizedTitle
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.databinding.ActivityNovelReaderBinding
import org.koitharu.kotatsu.reader.data.NovelReaderPreferences
import org.koitharu.kotatsu.reader.data.NovelReaderTheme

@AndroidEntryPoint
class NovelReaderActivity : BaseActivity<ActivityNovelReaderBinding>() {

	private val viewModel: ReaderViewModel by viewModels()
	private lateinit var preferences: NovelReaderPreferences

	private var currentChapterId = 0L
	private var renderedText: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityNovelReaderBinding.inflate(layoutInflater))
		preferences = NovelReaderPreferences(this)
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		applyAppearance()

		viewBinding.buttonPrev.setOnClickListener { switchChapter(-1) }
		viewBinding.buttonNext.setOnClickListener { switchChapter(1) }
		viewBinding.buttonPrev.isEnabled = false
		viewBinding.buttonNext.isEnabled = false

		viewModel.content.observe(this) { render(it) }
		viewModel.isLoading.observe(this) {
			viewBinding.progressBar.isVisible = it
		}
		viewModel.onLoadingError.observeEvent(
			this,
			DialogErrorObserver(
				host = viewBinding.root,
				fragment = null,
				resolver = exceptionResolver,
				onResolved = { isResolved ->
					if (isResolved) {
						viewModel.reload()
					} else {
						finishAfterTransition()
					}
				},
			),
		)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.root, null))
	}

	override fun onPause() {
		super.onPause()
		saveScrollState()
		viewModel.onPause()
	}

	override fun onStop() {
		super.onStop()
		viewModel.onStop()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.opt_novel_reader, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		R.id.action_chapters -> {
			showChaptersDialog()
			true
		}

		R.id.action_theme -> {
			cycleTheme()
			true
		}

		R.id.action_font -> {
			preferences.isSerif = !preferences.isSerif
			applyAppearance()
			Toast.makeText(
				this,
				if (preferences.isSerif) R.string.novel_font_serif else R.string.novel_font_sans,
				Toast.LENGTH_SHORT,
			).show()
			true
		}

		R.id.action_font_size_increase -> {
			changeFontSize(1)
			true
		}

		R.id.action_font_size_decrease -> {
			changeFontSize(-1)
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.appbar.updatePadding(left = bars.left, right = bars.right, top = bars.top)
		viewBinding.scrollView.updatePadding(left = bars.left, right = bars.right)
		viewBinding.bottomBar.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
		return insets
	}

	private fun render(content: ReaderContent) {
		val state = content.state ?: return
		val pages = content.pages.filter { it.chapterId == state.chapterId }
		if (pages.isEmpty()) {
			return
		}
		val html = pages.asSequence()
			.mapNotNull { it.text }
			.joinToString("\n")
			.takeIf { it.isNotBlank() }
		if (html == null) {
			viewBinding.textContent.text = getString(R.string.novel_no_text)
			updateChapterUi(state.chapterId)
			return
		}
		if (state.chapterId == currentChapterId && html == renderedText) {
			return
		}
		val isNewChapter = state.chapterId != currentChapterId
		currentChapterId = state.chapterId
		renderedText = html
		viewBinding.textContent.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
		viewBinding.progressBar.isVisible = false
		updateChapterUi(state.chapterId)
		if (isNewChapter) {
			val scroll = state.scroll
			viewBinding.textContent.post { viewBinding.scrollView.scrollTo(0, scroll) }
		}
	}

	private fun updateChapterUi(chapterId: Long) {
		val details = viewModel.mangaDetails.value ?: return
		val chapters = details.allChapters
		val index = chapters.indexOfFirst { it.id == chapterId }
		title = details.toManga().title
		supportActionBar?.subtitle = chapters.getOrNull(index)?.getLocalizedTitle(resources, index)
		viewBinding.buttonPrev.isEnabled = index > 0
		viewBinding.buttonNext.isEnabled = index in 0 until chapters.lastIndex
	}

	private fun switchChapter(delta: Int) {
		if (currentChapterId == 0L) {
			return
		}
		saveScrollState()
		viewModel.switchChapterBy(delta)
	}

	private fun saveScrollState() {
		val chapterId = currentChapterId
		if (chapterId == 0L) {
			return
		}
		viewModel.saveCurrentState(
			ReaderState(
				chapterId = chapterId,
				page = 0,
				scroll = viewBinding.scrollView.scrollY,
			),
		)
	}

	private fun showChaptersDialog() {
		val chapters = viewModel.mangaDetails.value?.allChapters ?: return
		if (chapters.isEmpty()) {
			return
		}
		val titles = chapters.mapIndexed { index, chapter ->
			chapter.getLocalizedTitle(resources, index)
		}.toTypedArray()
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.chapters)
			.setSingleChoiceItems(titles, chapters.indexOfFirst { it.id == currentChapterId }) { dialog, which ->
				dialog.dismiss()
				if (chapters[which].id != currentChapterId) {
					saveScrollState()
					viewModel.switchChapter(chapters[which].id, 0)
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun cycleTheme() {
		val themes = NovelReaderTheme.entries
		preferences.theme = themes[(preferences.theme.ordinal + 1) % themes.size]
		applyAppearance()
		Toast.makeText(this, preferences.theme.titleResId, Toast.LENGTH_SHORT).show()
	}

	private fun changeFontSize(delta: Int) {
		preferences.fontSizeSp += delta
		applyAppearance()
	}

	private fun applyAppearance() {
		val theme = preferences.theme
		val foreground = ColorStateList.valueOf(theme.foreground)
		viewBinding.root.setBackgroundColor(theme.background)
		viewBinding.appbar.setBackgroundColor(theme.background)
		viewBinding.toolbar.setBackgroundColor(theme.background)
		viewBinding.toolbar.setTitleTextColor(theme.foreground)
		viewBinding.toolbar.setSubtitleTextColor(theme.foreground)
		viewBinding.toolbar.navigationIcon?.setTint(theme.foreground)
		viewBinding.toolbar.overflowIcon?.setTint(theme.foreground)
		viewBinding.scrollView.setBackgroundColor(theme.background)
		viewBinding.bottomBar.setBackgroundColor(theme.background)
		viewBinding.textContent.setTextColor(theme.foreground)
		viewBinding.textContent.textSize = preferences.fontSizeSp.toFloat()
		viewBinding.textContent.typeface = if (preferences.isSerif) Typeface.SERIF else Typeface.SANS_SERIF
		viewBinding.buttonPrev.setTextColor(foreground)
		viewBinding.buttonNext.setTextColor(foreground)
		viewBinding.buttonPrev.iconTint = foreground
		viewBinding.buttonNext.iconTint = foreground
	}
}
