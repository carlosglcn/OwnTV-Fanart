package tv.own.owntv.glcn

/**
 * glcn: the film played from Keep watching in Movies. Kept outside the screen, which may leave the composition while
 * the player is up, so coming back still knows to return to that card instead of the film's poster further down.
 */
object StripReturn {
    @Volatile var movieId: Long? = null
}
