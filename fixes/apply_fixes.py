"""Install an additive, pinned-source experimental List repair after localization."""
import json
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
STRINGS = {
    "piko_newx_list_reading_position_title": ["Keep list reading position (experimental)", "保持列表阅读位置（实验性）"],
    "piko_newx_list_reading_position_summary": [
        "Keep your place when refreshing Lists and save each List separately. Restart after changing this setting.",
        "刷新列表时保持阅读位置，并分别记住每个列表的位置。更改后请重启应用。"],
}
for suffix, english, chinese in (("for_you", "For You", "为你推荐"), ("following", "Following", "正在关注"), ("lists", "Lists", "列表")):
    STRINGS[f"piko_newx_show_reposts_{suffix}_title"] = [f"Show reposts in {english}", f"{chinese}：展示转推"]
    STRINGS[f"piko_newx_show_reposts_{suffix}_summary"] = [
        f"Show reposted posts in {english}. Quote posts are unaffected. Restart after changing.",
        f"在{chinese}中展示转推的帖子。不影响引用帖。更改后请重启应用。"]


def apply_fixes(source, check_only=False):
    source = Path(source).resolve(strict=True)
    config = json.loads((ROOT.parent / "localization/source.json").read_text(encoding="utf-8"))
    if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip() != config["commit"]:
        raise ValueError("Unreviewed upstream source")
    planned = []
    for path in sorted((ROOT / "source").rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT / "source")
        target = source / relative
        if not target.resolve().is_relative_to(source) or target.exists():
            raise ValueError(f"Fix would overwrite existing source: {relative}")
        planned.append((target, path.read_bytes()))
    if len(planned) != 4:
        raise ValueError("Expected exactly two patch files and two extensions")
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
    return {"experimental_fix": "per-list-reading-position-v1", "fix_files": [p.relative_to(source).as_posix() for p, _ in planned],
            "fix_resources": len(STRINGS), "runtime_tested": False}
