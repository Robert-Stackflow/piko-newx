"""Fail the current build if the vendored NewX customization contract changes."""

import json
import subprocess
import unittest
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "piko-source"


class CurrentFeatureContract(unittest.TestCase):
    def test_source_matches_pinned_commit(self):
        config = json.loads((ROOT / "localization/source.json").read_text(encoding="utf-8"))
        pinned_tree = subprocess.check_output(
            ["git", "rev-parse", f"{config['commit']}^{{tree}}"], cwd=ROOT, text=True
        ).strip()
        vendored_tree = subprocess.check_output(
            ["git", "rev-parse", "HEAD:piko-source"], cwd=ROOT, text=True
        ).strip()
        self.assertEqual(vendored_tree, pinned_tree, "Update the pin when vendoring new source")
        subprocess.run(["git", "diff", "--exit-code", "HEAD", "--", "piko-source"],
                       cwd=ROOT, check=True, stdout=subprocess.DEVNULL)

    def test_maintained_patch_entry_points(self):
        patches = {
            "timeline/HomeTimelineOptionsPatch.kt": (
                "NewX: Per-timeline repost visibility", "NewX: Text-only pinned List tabs"),
            "timeline/PreserveListReadingPositionPatch.kt": (
                "NewX: Preserve list reading position",),
            "mediatools/MediaHistoryPatch.kt": (
                "NewX: Local post and video history",),
            "mediatools/MediaHeaderPatch.kt": ("HeaderToolsRuntime",),
            "misc/shareimage/ShareImagePatch.kt": ("NewX: Share post as image",),
            "misc/mediaquality/ForceHighestVideoQualityPatch.kt": (
                "NewX: Force highest video/audio quality",
                "newx.post_actions_media.force_highest_video_quality",
            ),
        }
        base = SOURCE / "patches/src/main/kotlin/app/crimera/patches/newx"
        for relative, markers in patches.items():
            with self.subTest(file=relative):
                content = (base / relative).read_text(encoding="utf-8")
                for marker in markers:
                    self.assertIn(marker, content)

    def test_maintained_runtime_and_chinese_resources(self):
        base = SOURCE / "extensions/newx/src/main/java/app/morphe/extension/newx"
        for relative in (
            "timeline/ListPositionRuntime.java",
            "timeline/TimelineRepostFilter.java",
            "mediatools/DownloadsFragment.java",
            "mediatools/HistoryFragment.java",
            "mediatools/MediaHistoryRuntime.java",
            "mediatools/VideoToolsRuntime.java",
            "misc/LocalizedDownloadTexts.java",
            "misc/NewXShareImageHandler.java",
        ):
            with self.subTest(file=relative):
                self.assertTrue((base / relative).is_file())
        zh = SOURCE / "patches/src/main/resources/addresources/values-zh-rCN/newx/strings.xml"
        self.assertTrue(zh.is_file())
        self.assertIn("piko_newx", zh.read_text(encoding="utf-8"))

    def test_resource_keys_are_unique(self):
        root = SOURCE / "patches/src/main/resources/addresources"
        for relative in ("values/newx/strings.xml", "values-zh-rCN/newx/strings.xml"):
            with self.subTest(file=relative):
                names = [item.get("name") for item in ElementTree.parse(root / relative).getroot()]
                self.assertEqual(len(names), len(set(names)))


if __name__ == "__main__":
    unittest.main()
