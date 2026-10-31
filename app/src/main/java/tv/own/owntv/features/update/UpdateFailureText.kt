package tv.own.owntv.features.update

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.update.UpdateManager

@Composable
internal fun updateFailureText(failure: UpdateManager.Failure): String = when (failure) {
    is UpdateManager.Failure.CheckHttp -> stringResource(R.string.update_failed_check_http, failure.code.toString())
    is UpdateManager.Failure.TokenNeeded -> stringResource(if (failure.rejected) R.string.update_token_rejected else R.string.update_token_needed)
    UpdateManager.Failure.NoCompatibleApk -> stringResource(R.string.update_no_compatible_apk)
    UpdateManager.Failure.InvalidReleaseResponse -> stringResource(R.string.update_invalid_release_response)
    UpdateManager.Failure.CheckNetwork -> stringResource(R.string.update_failed_check)
    is UpdateManager.Failure.DownloadHttp -> stringResource(R.string.update_failed_download_http, failure.code.toString())
    UpdateManager.Failure.EmptyDownload -> stringResource(R.string.update_empty_download)
    UpdateManager.Failure.DownloadNetwork -> stringResource(R.string.update_failed_download)
    UpdateManager.Failure.Install -> stringResource(R.string.update_install_failed)
    is UpdateManager.Failure.NotEnoughSpace -> stringResource(
        R.string.update_not_enough_space,
        Formatter.formatShortFileSize(LocalContext.current, failure.requiredBytes),
    )
    UpdateManager.Failure.DamagedDownload -> stringResource(R.string.update_damaged_download)
    // The installer's own wording names the real cause when it gives one; fall back when it doesn't.
    is UpdateManager.Failure.InstallRejected -> failure.message
        ?.let { stringResource(R.string.update_install_rejected, it) }
        ?: stringResource(R.string.update_install_failed)
}
