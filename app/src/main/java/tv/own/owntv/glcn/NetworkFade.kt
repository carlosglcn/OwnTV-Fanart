package tv.own.owntv.glcn

import coil3.decode.DataSource
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.transition.CrossfadeTransition
import coil3.transition.Transition
import coil3.transition.TransitionTarget

/**
 * glcn: pictures fade in only when they come from the network. One read from the disk cache (a poster seen before,
 * let go from memory) used to fade in too, so going back to a row looked like everything loading again; now it is
 * simply there, as one kept in memory always was.
 */
class NetworkFade(durationMillis: Int) : Transition.Factory {
    private val fade = CrossfadeTransition.Factory(durationMillis)

    override fun create(target: TransitionTarget, result: ImageResult): Transition =
        if (result is SuccessResult && result.dataSource != DataSource.NETWORK) Transition.Factory.NONE.create(target, result)
        else fade.create(target, result)
}
