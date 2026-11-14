"""Track the current SAF download lifecycle without replacing NewX's writer."""
STORE = "app.morphe.extension.newx.mediatools.DownloadTaskStore"
EDITS = [
    (
        "        downloadAsync(context, download.url, target, username, notificationId);",
        f"        long taskId = {STORE}.queuedSaf(context, download.url, target.fileName(), "
        "download.mimeType, postContext.id, username);\n"
        "        downloadAsync(context, download.url, target, username, notificationId, taskId);",
    ),
    (
        "            int notificationId\n    ) {\n        DOWNLOAD_EXECUTOR.execute(() -> {",
        "            int notificationId,\n            long taskId\n    ) {\n        DOWNLOAD_EXECUTOR.execute(() -> {",
    ),
    (
        "            // Success is reported by the OS download notification, which is already on screen.",
        f"            if (saved) {STORE}.published(context, taskId, target.documentUri().toString());\n"
        f"            else {STORE}.failed(context, taskId, \"transfer\");\n\n"
        "            // Success is reported by the OS download notification, which is already on screen.",
    ),
]
