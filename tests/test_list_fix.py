import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from fixes.apply_fixes import apply_fixes

ROOT = Path(__file__).resolve().parents[1]


class ListFixTests(unittest.TestCase):
    @unittest.skipUnless(os.getenv("PIKO_TEST_SOURCE"), "Needs pinned upstream checkout")
    def test_overlay_targets_are_additive_and_pinned(self):
        report = apply_fixes(Path(os.environ["PIKO_TEST_SOURCE"]), check_only=True)
        self.assertEqual(len(report["fix_files"]), 2)
        self.assertEqual(report["fix_resources"], 2)

    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "Needs Java")
    def test_real_java_policy_and_per_list_persistence(self):
        fixtures = {
            "android/content/SharedPreferences.java": '''package android.content;
public class SharedPreferences {
  private final java.util.Map<String,Integer> values = new java.util.HashMap<>();
  public int getInt(String k, int d) { return values.getOrDefault(k,d); }
  public Editor edit() { return new Editor(); }
  public class Editor {
    public Editor putInt(String k, int v) { values.put(k,v); return this; }
    public void apply() {}
  }
}''',
            "android/content/Context.java": '''package android.content;
public class Context {
  public static final int MODE_PRIVATE=0;
  private final SharedPreferences prefs = new SharedPreferences();
  public SharedPreferences getSharedPreferences(String name,int mode) { return prefs; }
}''',
            "app/morphe/extension/shared/Utils.java": '''package app.morphe.extension.shared;
public class Utils {
  public static android.content.Context context = new android.content.Context();
  public static android.content.Context getContext() { return context; }
}''',
            "app/morphe/extension/newx/settings/SettingsRegistry.java": '''package app.morphe.extension.newx.settings;
public class SettingsRegistry {
  public static final java.util.Map<String,Boolean> values = new java.util.HashMap<>();
  public static boolean getBooleanOrDefault(String k,boolean d) { return values.getOrDefault(k,d); }
}''',
            "ListFixTest.java": '''import java.util.*;
import app.morphe.extension.newx.timeline.ListReadingPosition;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.shared.Utils;
public class ListFixTest {
  enum T { LIST_POSTS, FOR_YOU, FOLLOWING, USER_PROFILE_POSTS, LIST_MEMBERS }
  enum R { AUTO_REFRESH, PULL_TO_REFRESH, OLDER_THAN, NEWER_THAN, GAP, VIEWPORT_AWARE_AUTO_REFRESH }
  static int checks;
  static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
  static boolean merge(T t,R r,Object cursor,List<?> items) { return ListReadingPosition.preserveMerge(t,r,cursor,items); }
  public static void main(String[] args) {
    for (T t : T.values()) for (R r : R.values()) {
      check(merge(t,r,null,List.of("post")) == (t==T.LIST_POSTS && (r==R.AUTO_REFRESH || r==R.PULL_TO_REFRESH)));
      check(!merge(t,r,new Object(),List.of("post")));
      check(!merge(t,r,null,List.of()));
      check(!merge(t,r,null,null));
      check(!merge(t,r,null,List.of(List.of())));
    }
    check(merge(T.LIST_POSTS,R.PULL_TO_REFRESH,null,List.of(List.of(),List.of("post"))));
    check(!merge(null,R.PULL_TO_REFRESH,null,List.of("post")));
    check(!merge(T.LIST_POSTS,null,null,List.of("post")));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1"),new int[]{0,0}));
    check(ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS1",23,41));
    check(ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS2",7,12));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1"),new int[]{23,41}));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS2"),new int[]{7,12}));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS3"),new int[]{0,0}));
    check(ListReadingPosition.restore(T.FOLLOWING,"LIST_POSTS1")==null);
    check(!ListReadingPosition.save(T.FOR_YOU,"LIST_POSTS1",0,0));
    check(ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS1",-1,0));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1"),new int[]{23,41}));
    SettingsRegistry.values.put("newx.timeline.restore_position",false);
    check(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1")==null);
    check(!ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS1",0,0));
    check(merge(T.LIST_POSTS,R.PULL_TO_REFRESH,null,List.of("post")));
    SettingsRegistry.values.put("newx.timeline.list_reading_position",false);
    check(!merge(T.LIST_POSTS,R.PULL_TO_REFRESH,null,List.of("post")));
    check(!ListReadingPosition.enabled(T.LIST_POSTS));
    SettingsRegistry.values.clear();
    Utils.context=null;
    check(ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS1",3,4));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1"),new int[]{0,0}));
    System.out.println("List policy/persistence checks passed: " + checks);
  }
}''',
        }
        helper = ROOT / "fixes/source/extensions/newx/src/main/java/app/morphe/extension/newx/timeline/ListReadingPosition.java"
        with tempfile.TemporaryDirectory(prefix="piko-list-tests-") as folder:
            directory = Path(folder)
            inputs = [str(helper)]
            for name, text in fixtures.items():
                path = directory / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
                inputs.append(str(path))
            subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), *inputs], check=True)
            subprocess.run(["java", "-cp", str(directory), "ListFixTest"], check=True)
