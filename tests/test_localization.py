import copy
import json
import os
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch

from localization import apply_localization as overlay


class ResourceTests(unittest.TestCase):
    def test_translations_have_unique_nonempty_keys(self):
        for module in ("newx", "shared"):
            strings = overlay.read_strings(overlay.ROOT / "zh-CN" / f"{module}.xml")
            self.assertTrue(all(value.text for value in strings.values()))

    def test_extra_messages_preserve_placeholders(self):
        messages = json.loads((overlay.ROOT / "messages.json").read_text(encoding="utf-8"))
        for key, (english, chinese) in messages.items():
            self.assertEqual(overlay.placeholders(english), overlay.placeholders(chinese), key)
            self.assertRegex(chinese, r"[\u4e00-\u9fff]", key)
            self.assertNotIn("&amp;", english, "JSON uses raw text, not XML entities")

    def test_format_placeholders_track_index_and_type(self):
        self.assertNotEqual(overlay.placeholders("%1$d"), overlay.placeholders("%1$s"))
        self.assertNotEqual(overlay.placeholders("%1$d"), overlay.placeholders("%2$d"))
        self.assertEqual(overlay.placeholders("%1$d %2$s"), overlay.placeholders("%2$s %1$d"))

    def test_ampersand_is_escaped_once(self):
        element = ET.Element("string", name="test")
        element.text = "Download & Merge"
        with tempfile.TemporaryDirectory(prefix="piko-xml-test-") as directory:
            path = Path(directory) / "strings.xml"
            overlay.write_strings(path, {"test": element})
            self.assertEqual(overlay.read_strings(path)["test"].text, "Download & Merge")
            self.assertNotIn("&amp;amp;", path.read_text())

    def test_duplicate_resources_rejected(self):
        with tempfile.TemporaryDirectory(prefix="piko-xml-test-") as directory:
            path = Path(directory) / "strings.xml"
            path.write_text('<resources><string name="x">a</string><string name="x">b</string></resources>')
            with self.assertRaisesRegex(ValueError, "Duplicate"):
                overlay.read_strings(path)


@unittest.skipUnless(os.getenv("PIKO_TEST_SOURCE"), "Set PIKO_TEST_SOURCE for pinned-source integration tests")
class PinnedSourceTests(unittest.TestCase):
    def setUp(self):
        self.source = Path(os.environ["PIKO_TEST_SOURCE"]).resolve()
        self.config = json.loads((overlay.ROOT / "source.json").read_text(encoding="utf-8"))

    def test_complete_overlay_matches_pinned_source(self):
        report = overlay.apply(self.source, check_only=True)
        self.assertEqual(report["translated_resources"], 356)
        self.assertEqual(report["localized_java_files"], 9)
        self.assertEqual(report["source_edits"], 94)

    def test_wrong_revision_rejected(self):
        with patch.object(overlay.subprocess, "check_output", return_value="0" * 40):
            with self.assertRaisesRegex(ValueError, "Unreviewed"):
                overlay.apply(self.source, check_only=True)

    def test_application_and_drift_fail_closed(self):
        edits = json.loads((overlay.ROOT / "source-edits.json").read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory(prefix="piko-overlay-test-") as directory:
            fixture = Path(directory)
            paths = [overlay.RESOURCES / "values" / module / "strings.xml"
                     for module in ("newx", "shared")]
            paths += [overlay.JAVA / path for path in edits]
            for relative in paths:
                target = fixture / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((self.source / relative).read_bytes())
            with patch.object(overlay.subprocess, "check_output", return_value=self.config["commit"]):
                overlay.apply(fixture)
                for module in ("newx", "shared"):
                    zh = overlay.read_strings(fixture / overlay.RESOURCES / "values-zh-rCN" / module / "strings.xml")
                    self.assertTrue(zh)
                    default = overlay.read_strings(fixture / overlay.RESOURCES / "values" / module / "strings.xml")
                    original = overlay.read_strings(self.source / overlay.RESOURCES / "values" / module / "strings.xml")
                    for key in original:
                        self.assertEqual(ET.tostring(default[key]).strip(), ET.tostring(original[key]).strip())
                for relative in edits:
                    before = (self.source / overlay.JAVA / relative).read_text(encoding="utf-8")
                    after = (fixture / overlay.JAVA / relative).read_text(encoding="utf-8")
                    # Persistent preference keys, action IDs and schema values remain untouched.
                    keys = lambda text: re.findall(r'"(?:newx\.[^"\n]+|piko_[^"\n]+)"', text)
                    old_keys = [key for key in keys(before) if key.startswith('"newx.')]
                    new_keys = [key for key in keys(after) if key.startswith('"newx.')]
                    self.assertEqual(old_keys, new_keys, relative)
                    self.assertIn(overlay.STR_IMPORT, after)
                snapshots = {relative: (fixture / relative).read_bytes() for relative in paths}
                with self.assertRaisesRegex(ValueError, "Source drift|unexpected"):
                    overlay.apply(fixture)
                self.assertEqual(snapshots, {relative: (fixture / relative).read_bytes() for relative in paths})


if __name__ == "__main__":
    unittest.main()
