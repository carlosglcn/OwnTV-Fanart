package tv.own.owntv.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.abs

/**
 * glcn: a programme's picture (XMLTV `<programme><icon>`) in a box of any shape.
 *
 * Guide pictures come in every shape — wide stills, 4:3 title cards, portrait posters. Cropping
 * them all to the box cut the title off the taller ones. So the picture's own shape decides:
 * close to the box's shape it fills the box (cropped a little, biased towards the top, where faces
 * and titles usually are); otherwise it is shown whole, over a blurred, darkened copy of itself.
 */
@Composable
fun ProgrammeArt(
    url: String,
    modifier: Modifier = Modifier,
    /** How far the picture's shape may differ from the box's before it is shown whole instead. */
    tolerance: Float = 0.18f,
) {
    var imageRatio by remember(url) { mutableStateOf<Float?>(Memo.ratio.get(url)) }
    // glcn: the box's shape from its measured size — no subcomposition per card (BoxWithConstraints),
    // which is what a row of fifty pictures pays for on every scroll.
    var boxRatio by remember { mutableStateOf(16f / 9f) }
    androidx.compose.foundation.layout.Box(
        modifier.onSizeChanged { if (it.width > 0 && it.height > 0) boxRatio = it.width.toFloat() / it.height },
    ) {
        val r = imageRatio
        val fits = r == null || abs(r / boxRatio - 1f) <= tolerance
        if (fits) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = TopBias,
                onSuccess = { s ->
                    val img = s.result.image
                    if (img.height > 0) { imageRatio = img.width.toFloat() / img.height; Memo.ratio.put(url, imageRatio!!) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // glcn: the guide's own picture, one copy, cropped from the top — never a second blurred copy
            // behind it (on TVs without blur support it showed as repeated slices).
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = TopBias,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val TopBias = BiasAlignment(0f, -0.4f)

/** glcn: what was already learnt about a picture, so coming back to it does not wait again. */
/**
 * glcn: a picture fetched ahead (to disk and memory, at screen size) with its shape noted, so a page that shows it next
 * has it at once — no blank frame, no switch of layout once its shape is known.
 */
suspend fun warmProgrammeArt(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    val request = coil3.request.ImageRequest.Builder(context).data(url).size(WARM_W, WARM_H).build()
    val result = coil3.SingletonImageLoader.get(context).execute(request) as? coil3.request.SuccessResult ?: return
    val img = result.image
    if (img.height > 0) Memo.ratio.put(url, img.width.toFloat() / img.height)
}

private const val WARM_W = 1920
private const val WARM_H = 1080

private object Memo {
    val ratio = object : android.util.LruCache<String, Float>(300) {}
    val wide = object : android.util.LruCache<String, String>(300) {}
}
