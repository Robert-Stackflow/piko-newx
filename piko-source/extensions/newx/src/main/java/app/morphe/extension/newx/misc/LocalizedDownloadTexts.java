package app.morphe.extension.newx.misc;

import app.morphe.extension.crimera.downloader.messages.DownloadText;
import app.morphe.extension.crimera.downloader.messages.DownloadTexts;
import app.morphe.extension.shared.StringRef;

/** NewX download summaries use the same app-locale resources as the rest of the UI. */
final class LocalizedDownloadTexts implements DownloadTexts {
    @Override
    public String get(DownloadText id, int count) {
        return switch (id) {
            case DOWNLOAD_STARTED -> StringRef.str("piko_newx_l10n_download_started");
            case DOWNLOADS_STARTED, PART_QUEUED -> count == 1
                    ? StringRef.str("piko_newx_l10n_download_started_one")
                    : StringRef.str("piko_newx_l10n_downloads_started", count);
            case ALREADY_DOWNLOADED -> StringRef.str("piko_newx_l10n_download_skipped");
            case MEDIA_ALREADY_DOWNLOADED -> StringRef.str("piko_newx_l10n_media_skipped_count", count);
            case FOLDER_LOST -> StringRef.str("piko_newx_l10n_folder_lost");
            case COULD_NOT_START -> StringRef.str("piko_newx_l10n_download_start_failed");
            case PART_SKIPPED -> count == 1
                    ? StringRef.str("piko_newx_l10n_skipped_one")
                    : StringRef.str("piko_newx_l10n_skipped_count", count);
            case PART_FAILED -> count == 1
                    ? StringRef.str("piko_newx_l10n_failed_one")
                    : StringRef.str("piko_newx_l10n_failed_count", count);
            case PART_LOST -> count == 1
                    ? StringRef.str("piko_newx_l10n_folder_lost_one")
                    : StringRef.str("piko_newx_l10n_folder_lost_count", count);
        };
    }
}
