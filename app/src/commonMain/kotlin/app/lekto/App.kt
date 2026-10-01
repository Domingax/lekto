package app.lekto

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import app.lekto.core.text.TextSegmenter
import app.lekto.reader.ReaderRenderer
import app.lekto.reader.ReaderScreen
import app.lekto.reader.SampleChapter

/**
 * The application root. It shows the reader over a bundled sample chapter until
 * book import lands; the word-token layer needs a segmenter, so the platform
 * entry points supply one behind the `TextSegmenter` seam (ADR-0007).
 */
@Composable
fun App(segmenter: TextSegmenter) {
    MaterialTheme {
        ReaderScreen(
            chapter = SampleChapter.chapter,
            renderer = ReaderRenderer(segmenter = segmenter, mastery = SampleChapter.mastery),
        )
    }
}
