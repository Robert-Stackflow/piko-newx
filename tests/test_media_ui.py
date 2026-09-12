import json
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "features"
JAVA = ROOT / "source/extensions/newx/src/main/java/app/morphe/extension/newx/mediatools"


class MediaUiTests(unittest.TestCase):
    def test_flat_rows_and_header_only_clear_actions(self):
        for name, count in (("HistoryFragment.java", "history_count"), ("DownloadsFragment.java", "download_count")):
            source = (JAVA / name).read_text(encoding="utf-8")
            self.assertIn("host.setPageAction(clear)", source)
            self.assertIn("host.setPageAction(null)", source)
            self.assertIn("list.setEmptyView(status)", source)
            self.assertNotIn('text("' + count + '")', source)
            self.assertNotIn("addView(clear", source)
        ui = (JAVA / "MediaToolsUi.java").read_text(encoding="utf-8")
        self.assertIn("ripple(card, Theme.surfaceContainer(c), 0)", ui)
        self.assertIn("Theme.dividerColor(c)", ui)
        self.assertNotIn("outer.setPadding", ui)
        from features.apply_features import EDITS
        activity = next(edits for name, edits in EDITS.items() if name.endswith("NewXSettingsActivity.java"))
        self.assertTrue(any("toolbar.removeView(customPageAction)" in after for _, after in activity))

    def test_titles_and_no_duplicate_history_toggle(self):
        history = (JAVA / "HistoryFragment.java").read_text(encoding="utf-8")
        downloads = (JAVA / "DownloadsFragment.java").read_text(encoding="utf-8")
        self.assertIn('setPageTitle(text("history_title"))', history)
        self.assertIn('setPageTitle(text("downloads_title"))', downloads)
        for obsolete in ("SwitchRow", "saveEnabled", "Spinner", "simple_list_item", "new Button("):
            self.assertNotIn(obsolete, history)
        self.assertIn("setOnItemClickListener", history)
        self.assertIn("previews.close()", history)
        self.assertIn("previews.close()", downloads)
        self.assertIn("if (scrolling)", downloads)
        self.assertIn("hasStableIds() { return true; }", downloads)
        self.assertIn("list.setSelectionFromTop(first, top)", downloads)

    def test_history_schema_upgrade_keeps_existing_records(self):
        source = (JAVA / "MediaHistoryStore.java").read_text(encoding="utf-8")
        migration = "ALTER TABLE history ADD COLUMN previews TEXT NOT NULL DEFAULT '[]'"
        self.assertIn(migration, source)
        db = sqlite3.connect(":memory:")
        db.execute("CREATE TABLE history (post TEXT PRIMARY KEY, body TEXT)")
        db.execute("INSERT INTO history VALUES ('123','kept')")
        db.execute(migration)
        self.assertEqual(db.execute("SELECT * FROM history").fetchone(), ("123", "kept", "[]"))
        db.close()

    def test_previews_are_bounded_and_do_not_log_history_urls(self):
        source = (JAVA / "MediaPreviewLoader.java").read_text(encoding="utf-8")
        for contract in ('new ArrayBlockingQueue<>(32)', 'new LruCache<>(8192)',
                         'setInstanceFollowRedirects(false)', 'if (safeRemote(source).isEmpty() || video)',
                         'context.getContentResolver().loadThumbnail', 'key.equals(image.getTag())',
                         'io.shutdownNow()', 'uri.getUserInfo() == null'):
            self.assertIn(contract, source)
        self.assertNotIn("NewXLogger", source)
        self.assertNotIn("Authorization", source)

    def test_header_is_native_and_direct_navigation_is_allowlisted(self):
        source = (JAVA / "HeaderToolsRuntime.java").read_text(encoding="utf-8")
        patch = (ROOT / "source/patches/src/main/kotlin/app/crimera/patches/newx/mediatools/MediaHeaderPatch.kt").read_text(encoding="utf-8")
        self.assertIn('"downloads".equals(destination)', source)
        self.assertIn('"history".equals(destination)', source)
        self.assertNotIn("Class.forName", source)
        self.assertNotIn("getDecorView", source)
        self.assertIn('"home_logo_scroll_to_top"', patch)
        self.assertIn('"more_options"', patch)
        self.assertIn('"AndroidView factory/update renderer"', patch)
        lock = (JAVA / "VideoToolsRuntime.java").read_text(encoding="utf-8")
        self.assertIn("if (!locked) return;", lock)
        self.assertIn("getGlobalVisibleRect(lastHeaderBounds)", lock)
        self.assertNotIn("new Button(", lock)

    def test_all_media_resource_placeholders_are_preserved(self):
        import re
        resources = json.loads((ROOT / "resources.json").read_text(encoding="utf-8"))
        for name, (english, chinese) in resources.items():
            self.assertEqual(re.findall(r"%[0-9]+\$[ds]", english), re.findall(r"%[0-9]+\$[ds]", chinese), name)

    def test_kotlin_callbacks_survive_shrinking_as_named_classes(self):
        source = (JAVA / "HeaderToolsRuntime.java").read_text(encoding="utf-8")
        for name in ("HomeFactory", "VideoFactory", "HeaderUpdate", "VideoActions"):
            self.assertIn("class " + name + " implements Function", source)
        self.assertNotIn("= context ->", source)
        self.assertNotIn("return (scope, composer, flags) ->", source)
        probe = (ROOT / "tools/AndroidMediaProbe.java").read_text()
        self.assertIn('getDeclaredMethod("invoke", parameters)', probe)
        self.assertIn('ART_HEADER_CALLBACK_PASS', probe)
        self.assertIn('ART_NATIVE_ACTION_FORWARD_PASS', probe)
        self.assertIn('private final Object action;', source)
        self.assertNotIn('(Function2<Object', source)
        self.assertIn('invokeNativeActions(action, composer)', source)
