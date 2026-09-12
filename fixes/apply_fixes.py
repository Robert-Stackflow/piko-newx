"""Install an additive, pinned-source experimental List repair after localization."""
import json
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
PATCH_PACKAGE = "patches/src/main/kotlin/app/crimera/patches/newx/timeline"
EXTENSION_PACKAGE = "extensions/newx/src/main/java/app/morphe/extension/newx/timeline"
# Keep the build fail-closed: the right count alone does not prove the right files.
OVERLAY_FILES = frozenset({
    f"{PATCH_PACKAGE}/PreserveListReadingPositionPatch.kt",
    f"{PATCH_PACKAGE}/HomeTimelineOptionsPatch.kt",
    f"{PATCH_PACKAGE}/ListPositionUiHooks.kt",
    f"{EXTENSION_PACKAGE}/ListReadingPosition.java",
    f"{EXTENSION_PACKAGE}/TimelineRepostFilter.java",
    f"{EXTENSION_PACKAGE}/ListAnchorState.java",
    f"{EXTENSION_PACKAGE}/ListPositionRuntime.java",
})
STRINGS = {
    "piko_newx_list_reading_position_title": ["Restore List reading position", "恢复列表阅读位置"],
    "piko_newx_list_reading_position_summary": [
        "Find the same displayed post per account and List. Missing posts are not reinserted. Dragging or jumping to top cancels restoration. Restart after changing.",
        "按账号和列表找回同一帖子的位置。原帖不存在时不补回旧帖；拖动或主动回顶会取消恢复。更改后请重启应用。"],
}
for suffix, english, chinese in (("for_you", "For You", "为你推荐"), ("following", "Following", "正在关注"), ("lists", "Lists", "列表")):
    STRINGS[f"piko_newx_show_reposts_{suffix}_title"] = [f"Show reposts in {english}", f"{chinese}：展示转推"]
    STRINGS[f"piko_newx_show_reposts_{suffix}_summary"] = [
        f"Show reposted posts in {english}. Quote posts are unaffected. Restart after changing.",
        f"在{chinese}中展示转推的帖子。不影响引用帖。更改后请重启应用。"]


def validated_overlay_files(source_root):
    """Return the exact reviewed source files in deterministic archive/copy order."""
    source_root = Path(source_root).resolve(strict=True)
    paths = sorted(path for path in source_root.rglob("*") if path.is_file())
    actual = {path.relative_to(source_root).as_posix() for path in paths}
    if actual != OVERLAY_FILES:
        missing = sorted(OVERLAY_FILES - actual)
        unexpected = sorted(actual - OVERLAY_FILES)
        raise ValueError(f"Fix source manifest mismatch: missing={missing}, unexpected={unexpected}")
    for path in paths:
        if not path.resolve().is_relative_to(source_root):
            raise ValueError(f"Fix source escapes overlay: {path.relative_to(source_root)}")
    return paths


def apply_fixes(source, check_only=False):
    source = Path(source).resolve(strict=True)
    config = json.loads((ROOT.parent / "localization/source.json").read_text(encoding="utf-8"))
    if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip() != config["commit"]:
        raise ValueError("Unreviewed upstream source")
    planned = []
    for path in validated_overlay_files(ROOT / "source"):
        relative = path.relative_to(ROOT / "source")
        target = source / relative
        if not target.resolve().is_relative_to(source) or target.exists():
            raise ValueError(f"Fix would overwrite existing source: {relative}")
        planned.append((target, path.read_bytes()))
    resources = []
    for locale, index in (("values", 0), ("values-zh-rCN", 1)):
        path = source / "patches/src/main/resources/addresources" / locale / "newx/strings.xml"
        # check_only can run on pristine source before the Chinese resource exists.
        if not path.exists() and check_only and locale == "values-zh-rCN":
            continue
        tree = ET.parse(path)
        names = {item.get("name") for item in tree.getroot()}
        if names.intersection(STRINGS):
            raise ValueError("List fix resources already present")
        for key, translations in STRINGS.items():
            ET.SubElement(tree.getroot(), "string", name=key).text = translations[index]
        resources.append((path, tree))
    if not check_only:
        for path, contents in planned:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(contents)
        for path, tree in resources:
            ET.indent(tree, space="    ")
            tree.write(path, encoding="utf-8", xml_declaration=True)
    return {
        # Retain the old report identifier for consumers of existing build reports.
        "experimental_fix": "per-list-reading-position-v1",
        "position_strategy": "account-list-ui-key-v2",
        "fix_files": [p.relative_to(source).as_posix() for p, _ in planned],
        "fix_resources": len(STRINGS),
        "runtime_tested": False,
    }
