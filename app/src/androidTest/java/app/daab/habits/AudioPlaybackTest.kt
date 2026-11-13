package app.daab.habits

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.daab.habits.audio.QuranAudio
import app.daab.habits.audio.QuranAudioService
import app.daab.habits.data.Mushaf
import app.daab.habits.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Plays al-Fatihah on a real Android system and records which word was lit at each moment. */
@RunWith(AndroidJUnit4::class)
class AudioPlaybackTest {
    @Test
    fun fatihahFollowsWords() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val data = Mushaf.load(ctx)
            val log = StringBuilder()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { QuranAudioService.play(ctx, 0, 7) }
            val start = System.currentTimeMillis()
            var lastWord = -2
            var words = 0
            while (System.currentTimeMillis() - start < 45_000) {
                val w = QuranAudio.word.value
                if (w != lastWord) {
                    val text = if (w >= 0) data.words[w] else "-"
                    log.append("${System.currentTimeMillis() - start} ms ayah ${QuranAudio.current.value} word $w $text playing ${QuranAudio.playing.value}\n")
                    lastWord = w
                    if (w >= 0) words++
                }
                Thread.sleep(40)
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync { QuranAudioService.stop(ctx) }
            File(ctx.getExternalFilesDir(null), "audio_check.txt").writeText(log.toString())
            assertTrue("words lit: $words\n$log", words >= 20)
        }
    }
}
