"""Install the additive media-tools development overlay; exact source and anchors only."""
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JAVA = "extensions/newx/src/main/java/app/morphe/extension/newx"
PACKAGE = f"{JAVA}/mediatools"
MANIFEST = frozenset({
    f"{PACKAGE}/{name}.java" for name in (
        "WatchSession", "MediaHistoryStore", "DownloadTaskStore", "HistoryFragment", "MediaHistoryRuntime")
}) | {"patches/src/main/kotlin/app/crimera/patches/newx/mediatools/MediaHistoryPatch.kt"}
STORE = "app.morphe.extension.newx.mediatools.MediaHistoryStore"
RUNTIME = "app.morphe.extension.newx.mediatools.MediaHistoryRuntime"
EDITS = {
    f"{JAVA}/settings/SettingsRenderer.java": [(
        "            if (item.setting.get() == value) return true;\n            item.setting.save(value);",
        "            if (item.setting.get() == value) return true;\n"
        f"            if (item.id.equals({STORE}.ENABLED)) {STORE}.invalidatePending();\n"
        "            item.setting.save(value);\n"
        f"            if (item.id.equals({STORE}.ENABLED)) {RUNTIME}.resetVisits();")],
    f"{JAVA}/settings/SettingsBackupRestore.java": [(
        "            Setting.importFromJSON(activity, json);",
        f"            {STORE}.invalidatePending();\n"
        "            Setting.importFromJSON(activity, json);\n"
        f"            {RUNTIME}.resetVisits();")],
}


def files():
    source = ROOT / "source"
    paths = sorted(p for p in source.rglob("*") if p.is_file())
    actual = {p.relative_to(source).as_posix() for p in paths}
    if actual != MANIFEST:
        raise ValueError(f"Media manifest mismatch: missing={MANIFEST - actual}, extra={actual - MANIFEST}")
    if any(not p.resolve().is_relative_to(source.resolve()) for p in paths):
        raise ValueError("Media source escapes overlay")
    return paths


def edited(text, edits, label):
    for before, after in edits:
        if text.count(before) != 1:
            raise ValueError(f"Media source drift: {label}: expected one anchor, got {text.count(before)}")
        text = text.replace(before, after)
    return text


def apply_features(source, check_only=False):
    source = Path(source).resolve(strict=True)
    config = json.loads((ROOT.parent / "localization/source.json").read_text(encoding="utf-8"))
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
    if revision != config["commit"]:
        raise ValueError("Unreviewed upstream source for media tools")
    planned = []
    added = []
    for path in files():
        relative = path.relative_to(ROOT / "source")
        target = source / relative
        if target.exists() or not target.resolve().is_relative_to(source):
            raise ValueError(f"Media tools would overwrite source: {relative}")
        planned.append((target, path.read_bytes()))
        added.append(relative.as_posix())
    for relative, edits in EDITS.items():
        target = source / relative
        contents = edited(target.read_text(encoding="utf-8"), edits, relative)
        planned.append((target, contents.encode("utf-8")))
    strings = json.loads((ROOT / "resources.json").read_text(encoding="utf-8"))
    for locale, index in (("values", 0), ("values-zh-rCN", 1)):
        target = source / "patches/src/main/resources/addresources" / locale / "newx/strings.xml"
        if check_only and not target.exists() and index == 1:
            continue
        tree = ET.parse(target)
        if {item.get("name") for item in tree.getroot()}.intersection(strings):
            raise ValueError("Media resources already present")
        for name, values in strings.items():
            if len(values) != 2 or any(not value for value in values):
                raise ValueError(f"Invalid bilingual media string: {name}")
            ET.SubElement(tree.getroot(), "string", name=name).text = values[index]
        ET.indent(tree, space="    ")
        planned.append((target, ET.tostring(tree.getroot(), encoding="utf-8", xml_declaration=True)))
    if not check_only:
        for target, contents in planned:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(contents)
    return {"feature_files": added, "feature_resources": len(strings), "feature_source_edits": len(EDITS),
            "history_policy": "foreground-current-page-immediate", "media_runtime_tested": False}
