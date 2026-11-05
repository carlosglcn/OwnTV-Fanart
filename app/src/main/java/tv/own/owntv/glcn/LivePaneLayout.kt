package tv.own.owntv.glcn

/**
 * glcn: TV en directo, the column beside the list. [FICHA] (on trial): the programme's page — title, a long synopsis,
 * what follows — over the programme's picture, with the video small in the top right corner. Off: the big video on
 * top and the details under it, as before. One switch, so going back is one line.
 */
object LivePaneLayout {
    const val FICHA = true
    /** glcn: Ficha — the video's share of the column's width (smaller, so more of the synopsis reads at once). */
    const val FICHA_VIDEO_W = 1f // glcn: the whole column — edges in line with the text and the cards under it
}
