import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from features import apply_features as media


class MediaOverlayTests(unittest.TestCase):
    def test_manifest_and_resource_contract(self):
        self.assertEqual({p.relative_to(media.ROOT / "source").as_posix() for p in media.files()}, media.MANIFEST)
        strings = json.loads((media.ROOT / "resources.json").read_text(encoding="utf-8"))
        for name in strings:
            self.assertRegex(name, r"^piko_newx_[a-z0-9_]+$")
        self.assertTrue(all(len(values) == 2 and all(values) for values in strings.values()))
        self.assertIn("暂停也算", strings["piko_newx_tools_history_enabled_summary"][1])
        self.assertNotIn("one second", strings["piko_newx_tools_history_enabled_summary"][0])

    def test_source_edit_fail_closed(self):
        self.assertEqual(media.edited("anchor", [("anchor", "new")], "test"), "new")
        for text in ("missing", "anchor anchor"):
            with self.assertRaisesRegex(ValueError, "source drift"):
                media.edited(text, [("anchor", "new")], "test")

    @unittest.skipUnless(os.getenv("PIKO_TEST_SOURCE"), "Needs pinned source")
    def test_pinned_application(self):
        source = Path(os.environ["PIKO_TEST_SOURCE"])
        media.apply_features(source, check_only=True)
        config = json.loads((media.ROOT.parent / "localization/source.json").read_text())
        with tempfile.TemporaryDirectory(prefix="piko-media-") as temporary:
            fixture = Path(temporary)
            for relative in media.EDITS:
                target = fixture / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((source / relative).read_bytes())
            for locale in ("values", "values-zh-rCN"):
                relative = f"patches/src/main/resources/addresources/{locale}/newx/strings.xml"
                target = fixture / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text("<resources />", encoding="utf-8")
            with patch.object(media.subprocess, "check_output", return_value=config["commit"]):
                report = media.apply_features(fixture)
                self.assertEqual(set(report["feature_files"]), media.MANIFEST)
                for relative in media.EDITS:
                    expected = "retryManagedDownload(" if relative.endswith("InlineDownloadButton.java") else "invalidatePending()"
                    self.assertTrue(expected in (fixture / relative).read_text(), relative)
                with self.assertRaisesRegex(ValueError, "overwrite"):
                    media.apply_features(fixture)
