"""Small native download lifecycle hooks; do not replace the downloader or conflict policy."""
STORE = "app.morphe.extension.newx.mediatools.DownloadTaskStore"
EDITS = [
    ("                download.mimeType,\n                ", "                download.mimeType,\n                postId,\n                username,\n                "),
    ("            String mimeType,\n            String description\n", "            String mimeType,\n            String postId,\n            String username,\n            String description\n"),
    ("savePendingDownload(context, downloadId, temporaryFileName, fileName, mimeType, url);",
     "savePendingDownload(context, downloadId, temporaryFileName, fileName, mimeType, url, postId, username);\n"
     f"                {STORE}.queued(context, downloadId, url, fileName, mimeType, postId, username);"),
    ('            NewXLogger.printException(() -> "Failed to enqueue NewX media download", exception);',
     f"            {STORE}.enqueueFailed(context, url, fileName, mimeType, postId, username);\n"
     '            NewXLogger.printException(() -> "Failed to enqueue NewX media download", exception);'),
    ("            String mimeType,\n            String url\n    ) {\n        String value = temporaryFileName",
     "            String mimeType,\n            String url,\n            String postId,\n            String username\n    ) {\n        String value = temporaryFileName"),
    (' + (url != null ? url : "");',
     ' + (url != null ? url : "") + "\\n" + (postId != null ? postId : "") + "\\n" + (username != null ? username : "");'),
    ("return new PendingDownload(fields[0], fields[1], fields[2], url);",
     'return new PendingDownload(fields[0], fields[1], fields[2], url, fields.length >= 5 ? fields[4] : "", fields.length >= 6 ? fields[5] : "");'),
    ("        PendingDownload(String temporaryFileName, String fileName, String mimeType, String url) {",
     "        final String postId;\n        final String username;\n\n"
     "        PendingDownload(String temporaryFileName, String fileName, String mimeType, String url, String postId, String username) {\n"
     "            this.postId = postId;\n            this.username = username;"),
    ("        if (pending == null) return;\n\n        int status = downloadStatus(manager, downloadId);",
     "        if (pending == null) return;\n"
     f"        {STORE}.queued(context, downloadId, pending.url, pending.fileName, pending.mimeType, pending.postId, pending.username);\n\n"
     "        int status = downloadStatus(manager, downloadId);"),
    ("            if (!moved) {", "            if (!moved) {\n"
     f'                {STORE}.failed(context, downloadId, "finalize");'),
    ("            removePendingDownload(context, manager, downloadId);\n            Utils.showToastShort(",
     f"            {STORE}.finished(context, downloadId);\n"
     "            removePendingDownload(context, manager, downloadId);\n            Utils.showToastShort("),
    ('            NewXLogger.printException(() -> "Failed to finalize NewX media download", exception);',
     f'            {STORE}.failed(context, downloadId, "finalize");\n'
     '            NewXLogger.printException(() -> "Failed to finalize NewX media download", exception);'),
    ("        removePendingDownload(context, manager, downloadId);\n        if (pending.url != null",
     f"        {STORE}.failed(context, downloadId, managedFailureReason(manager, downloadId));\n"
     "        removePendingDownload(context, manager, downloadId);\n        if (pending.url != null"),
    ("enqueueFallbackDownload(context, manager, fallbackUrl, pending.fileName, pending.mimeType);",
     "enqueueFallbackDownload(context, manager, fallbackUrl, pending.fileName, pending.mimeType, pending.postId, pending.username);"),
    ("            String fallbackUrl,\n            String fileName,\n            String mimeType\n",
     "            String fallbackUrl,\n            String fileName,\n            String mimeType,\n            String postId,\n            String username\n"),
    ("queueDownload(context, manager, fallbackUrl, fileName, mimeType, ",
     "queueDownload(context, manager, fallbackUrl, fileName, mimeType, postId, username, "),
    ("        deleteExistingMedia(resolver, collection, fileName, relativePath, destination);\n        return true;",
     "        deleteExistingMedia(resolver, collection, fileName, relativePath, destination);\n"
     f"        {STORE}.published(context, downloadId, destination.toString());\n        return true;"),
    ("    private static EnqueueState queueDownload(", '''    private static String managedFailureReason(DownloadManager manager, long id) {
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor != null && cursor.moveToFirst())
                return Integer.toString(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
        } catch (RuntimeException ignored) {}
        return "system_task_missing";
    }

    public static void retryManagedDownload(Context context, long id, String url, String fileName,
            String mime, String post, String author, java.util.function.Consumer<Boolean> done) {
        Context app = context.getApplicationContext();
        DOWNLOAD_EXECUTOR.execute(() -> {
            boolean success = false;
            try {
                DownloadManager manager = downloadManager(app);
                if (manager == null) return;
                if (pendingDownload(app, id) != null) {
                    // A publication failure retries publication, not the network transfer.
                    finishPendingDownload(app, manager, id);
                    success = pendingDownload(app, id) == null;
                } else if (NewXUtils.isHttpUrl(url)) {
                    String target = resolveTargetFileName(app, fileName, conflictBehavior(), mime);
                    if (target != null) {
                        success = queueDownload(app, manager, url, target, mime, post, author,
                                str("piko_newx_l10n_downloading")) == EnqueueState.QUEUED;
                        if (success) STORE_PLACEHOLDER.retried(app, id);
                    }
                }
            } catch (RuntimeException ignored) {
            } finally { if (done != null) done.accept(success); }
        });
    }

    private static EnqueueState queueDownload('''.replace("STORE_PLACEHOLDER", STORE)),
]
