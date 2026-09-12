"""Build-input checks, independent of the upstream checkout and Android runtime."""

from pathlib import Path
import re
import tempfile
import unittest

from fixes.apply_fixes import EXTENSION_PACKAGE, OVERLAY_FILES, validated_overlay_files

ROOT = Path(__file__).resolve().parents[1]


class OverlayManifestTests(unittest.TestCase):
    def test_reviewed_overlay_is_complete_and_sorted(self):
        source = ROOT / "fixes/source"
        names = [path.relative_to(source).as_posix() for path in validated_overlay_files(source)]
        self.assertEqual(names, sorted(OVERLAY_FILES))

    def test_missing_extra_and_same_count_replacement_are_rejected(self):
        missing = sorted(OVERLAY_FILES)[0]
        cases = {
            "missing": OVERLAY_FILES - {missing},
            "extra": OVERLAY_FILES | {"Unexpected.java"},
            "same_count_replacement": (OVERLAY_FILES - {missing}) | {"Unexpected.java"},
        }
        for name, files in cases.items():
            with self.subTest(name=name), tempfile.TemporaryDirectory(prefix="piko-manifest-") as folder:
                source = Path(folder)
                for relative in files:
                    path = source / relative
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.touch()
                with self.assertRaisesRegex(ValueError, "Fix source manifest mismatch"):
                    validated_overlay_files(source)

    def test_art_probe_includes_every_extension_and_initializes_classes(self):
        probe = (ROOT / "fixes/tools/AndroidTypeProbe.java").read_text(encoding="utf-8")
        names = re.findall(r'"((?:com\.x|androidx|app\.morphe)\.[\w.]+)"', probe)
        self.assertEqual(len(names), 14)
        self.assertEqual(len(set(names)), 14)
        for path in OVERLAY_FILES:
            if path.startswith(EXTENSION_PACKAGE + "/"):
                self.assertIn("app.morphe.extension.newx.timeline." + Path(path).stem, names)
        self.assertIn("Class.forName(name, true,", probe)


if __name__ == "__main__":
    unittest.main()
