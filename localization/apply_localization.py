"""Apply the reviewed, version-locked Simplified Chinese overlay to Piko source."""

import argparse
import copy
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RESOURCES = Path("patches/src/main/resources/addresources")
JAVA = Path("extensions/newx/src/main/java/app/morphe/extension/newx")
STR_IMPORT = "import static app.morphe.extension.shared.StringRef.str;"


def read_strings(path):
    elements = list(ET.parse(path).getroot())
    names = [element.attrib["name"] for element in elements]
    if len(names) != len(set(names)):
        raise ValueError(f"Duplicate resources: {path}")
    if any(element.tag != "string" for element in elements):
        raise ValueError(f"Only string resources are supported: {path}")
    return {element.attrib["name"]: element for element in elements}


def placeholders(text):
    return Counter(re.findall(r"%(?:\d+\$)?[a-zA-Z%]", text or ""))


def write_strings(path, strings):
    root = ET.Element("resources")
    root.extend(copy.deepcopy(list(strings.values())))
    ET.indent(root, space="    ")
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def apply(source, check_only=False):
    source = Path(source).resolve(strict=True)
    config = json.loads((ROOT / "source.json").read_text(encoding="utf-8"))
    commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=source, text=True
    ).strip()
    if commit != config["commit"]:
        raise ValueError(f"Unreviewed source revision: {commit}")

    planned = []
    all_english = {}
    translations = {}
    for module in ("newx", "shared"):
        original = read_strings(source / RESOURCES / "values" / module / "strings.xml")
        translated = read_strings(ROOT / "zh-CN" / f"{module}.xml")
        expected = {key for key, value in original.items()
                    if value.get("translatable") != "false"}
        if expected != translated.keys():
            raise ValueError(f"{module}: missing {expected - translated.keys()}, "
                             f"unexpected {translated.keys() - expected}")
        for key, value in translated.items():
            if not value.text or placeholders(value.text) != placeholders(original[key].text):
                raise ValueError(f"Empty translation or changed placeholders: {key}")
        all_english.update(original)
        translations[module] = translated

    messages_file = ROOT / "messages.json"
    messages = json.loads(messages_file.read_text(encoding="utf-8")) if messages_file.exists() else {}
    for suffix, (english, chinese) in messages.items():
        key = "piko_newx_l10n_" + suffix
        if key in all_english or placeholders(english) != placeholders(chinese):
            raise ValueError(f"Invalid additional message: {key}")
        element = ET.Element("string", name=key)
        element.text = english
        all_english[key] = element
        translated = ET.Element("string", name=key)
        translated.text = chinese
        translations["newx"][key] = translated

    edits_file = ROOT / "source-edits.json"
    edits = json.loads(edits_file.read_text(encoding="utf-8")) if edits_file.exists() else {}
    edit_count = 0
    for relative, replacements in edits.items():
        path = (source / JAVA / relative).resolve(strict=True)
        if not path.is_relative_to(source / JAVA) or path.suffix != ".java":
            raise ValueError(f"Unsafe source target: {relative}")
        original = path.read_text(encoding="utf-8")
        # Validate every anchor before changing any source file.
        for old, new, count in replacements:
            if original.count(old) != count:
                raise ValueError(f"Source drift in {relative}: {old!r}, "
                                 f"expected {count}, found {original.count(old)}")
            for key in re.findall(r'str\("([^"]+)"', new):
                if key not in all_english:
                    raise ValueError(f"Unknown resource: {key}")
        changed = original
        for old, new, count in replacements:
            if changed.count(old) != count:
                raise ValueError(f"Overlapping source edits in {relative}: {old!r}")
            changed = changed.replace(old, new)
            edit_count += count
        if STR_IMPORT not in changed:
            changed = changed.replace(";\n", ";\n\n" + STR_IMPORT + "\n", 1)
        planned.append((path, changed))

    report = {"source": commit, "translated_resources": sum(map(len, translations.values())),
              "localized_java_files": len(planned), "source_edits": edit_count}
    if not check_only:
        for path, changed in planned:
            path.write_text(changed, encoding="utf-8", newline="\n")
        if messages:
            default_path = source / RESOURCES / "values/newx/strings.xml"
            english = read_strings(default_path)
            english.update({key: value for key, value in all_english.items()
                            if key.startswith("piko_newx_l10n_")})
            write_strings(default_path, english)
        for module, translated in translations.items():
            write_strings(source / RESOURCES / "values-zh-rCN" / module / "strings.xml", translated)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    print(json.dumps(apply(args.source, args.check_only), ensure_ascii=False, indent=2))
