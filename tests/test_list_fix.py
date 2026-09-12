import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from fixes.apply_fixes import apply_fixes

ROOT = Path(__file__).resolve().parents[1]


class ListFixTests(unittest.TestCase):
    def test_native_refresh_is_not_modified(self):
        patch = (ROOT / "fixes/source/patches/src/main/kotlin/app/crimera/patches/newx/timeline/PreserveListReadingPositionPatch.kt").read_text(encoding="utf-8")
        helper = (ROOT / "fixes/source/extensions/newx/src/main/java/app/morphe/extension/newx/timeline/ListReadingPosition.java").read_text(encoding="utf-8")
        for forbidden in ("VIEWPORT_AWARE_AUTO_REFRESH", "pikoListMergeMode", "pikoListScrollToTop", "capture_list_viewport", "preserveMerge"):
            self.assertNotIn(forbidden, patch)
            self.assertNotIn(forbidden, helper)

    def test_null_array_path_does_not_join_holder_return(self):
        source = (ROOT / "fixes/source/patches/src/main/kotlin/app/crimera/patches/newx/timeline/PreserveListReadingPositionPatch.kt").read_text(encoding="utf-8")
        self.assertIn("if-nez v0, :restore\n            const/4 v0, 0x0\n            return-object v0\n            :restore", source)
        self.assertNotIn("if-eqz v0, :done", source)

    @unittest.skipUnless(os.getenv("PIKO_TEST_SOURCE"), "Needs pinned upstream checkout")
    def test_overlay_targets_are_additive_and_pinned(self):
        report = apply_fixes(Path(os.environ["PIKO_TEST_SOURCE"]), check_only=True)
        self.assertEqual(len(report["fix_files"]), 7)
        self.assertEqual(report["fix_resources"], 8)

    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "Needs Java")
    def test_real_java_policy_and_per_list_persistence(self):
        fixtures = {
            "android/util/Log.java": '''package android.util;
public class Log { public static int d(String tag,String message) { return 0; } }''',
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
  static int checks;
  static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
  public static void main(String[] args) {
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1"),new int[]{0,0}));
    check(ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS1",23,41));
    check(ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS2",7,12));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1"),new int[]{0,0}));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS2"),new int[]{0,0}));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS3"),new int[]{0,0}));
    check(ListReadingPosition.restore(T.FOLLOWING,"LIST_POSTS1")==null);
    check(!ListReadingPosition.save(T.FOR_YOU,"LIST_POSTS1",0,0));
    check(ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS1",-1,0));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1"),new int[]{0,0}));
    SettingsRegistry.values.put("newx.timeline.restore_position",false);
    check(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1")==null);
    check(!ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS1",0,0));
    SettingsRegistry.values.put("newx.timeline.list_reading_position",false);
    check(!ListReadingPosition.enabled(T.LIST_POSTS));
    SettingsRegistry.values.clear();
    Utils.context=null;
    check(ListReadingPosition.save(T.LIST_POSTS,"LIST_POSTS1",3,4));
    check(Arrays.equals(ListReadingPosition.restore(T.LIST_POSTS,"LIST_POSTS1"),new int[]{0,0}));
    System.out.println("List policy/persistence checks passed: " + checks);
  }
}''',
            "RepostTest.java": '''import java.util.*;
import app.morphe.extension.newx.timeline.TimelineRepostFilter;
import app.morphe.extension.newx.settings.SettingsRegistry;
public class RepostTest {
  enum T { FOR_YOU,FOLLOWING,RANKED_FOLLOWING,LIST_POSTS,LIST_MEMBERS,USER_PROFILE_POSTS,SEARCH_LATEST,BOOKMARKS }
  record Post(String id,boolean repost) {}
  record Wrap(Object item) {}
  record Module(List<?> children,Set<Object> removed) {}
  static int checks;
  static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("repost check " + checks); }
  static final TimelineRepostFilter.Model model = new TimelineRepostFilter.Model() {
    public boolean repost(Object i) { return i instanceof Post p && p.repost(); }
    public boolean wrapper(Object i) { return i instanceof Wrap; }
    public Object unwrap(Object i) { return ((Wrap)i).item(); }
    public Object rewrap(Object old,Object i) { return new Wrap(i); }
    public List<?> children(Object i) { return i instanceof Module m ? m.children() : null; }
    public Object replaceChildren(Object old,List<?> c,Set<Object> ids) { return new Module(c,ids); }
    public Object postId(Object i) { return i instanceof Post p ? p.id() : null; }
    public Object immutable(List<Object> i) { return Collections.unmodifiableList(i); }
  };
  public static void main(String[] args) {
    Object ordinary = new Post("ordinary",false), quote = new Post("quote",false), rp = new Post("reposted",true);
    Object cursor = new Object();
    List<?> input = List.of(ordinary,rp,quote,cursor);
    for (T t:T.values()) {
      check(TimelineRepostFilter.showReposts(t));
      check(TimelineRepostFilter.filter(input,t,model)==input);
    }
    for (String suffix:List.of("for_you","following","lists")) {
      SettingsRegistry.values.clear();
      SettingsRegistry.values.put("newx.timeline.show_reposts_"+suffix,false);
      for (T t:T.values()) {
        boolean hide = suffix.equals("for_you") && t==T.FOR_YOU || suffix.equals("following") && (t==T.FOLLOWING || t==T.RANKED_FOLLOWING) || suffix.equals("lists") && t==T.LIST_POSTS;
        Object result = TimelineRepostFilter.filter(input,t,model);
        check(TimelineRepostFilter.showReposts(t)==!hide);
        check(hide ? result.equals(List.of(ordinary,quote,cursor)) : result==input);
      }
    }
    Object wrapped = new Wrap(rp);
    Object module = new Module(List.of(new Wrap(ordinary),wrapped),Set.of());
    List<?> result = (List<?>)TimelineRepostFilter.filter(List.of(module),T.LIST_POSTS,model);
    Module changed = (Module)result.get(0);
    check(changed.children().equals(List.of(new Wrap(ordinary))));
    check(changed.removed().equals(Set.of("reposted")));
    check(((List<?>)TimelineRepostFilter.filter(List.of(new Module(List.of(wrapped),Set.of())),T.LIST_POSTS,model)).isEmpty());
    check(((List<?>)TimelineRepostFilter.filter(List.of(wrapped),T.LIST_POSTS,model)).isEmpty());
    check(TimelineRepostFilter.filter(List.of(ordinary),T.LIST_POSTS,model).equals(List.of(ordinary)));
    check(TimelineRepostFilter.filter(null,T.LIST_POSTS,model)==null);
    check(TimelineRepostFilter.filter(input,null,model)==input);
    check(input.size()==4);
    SettingsRegistry.values.put("newx.timeline.show_reposts_lists",true);
    check(TimelineRepostFilter.filter(input,T.LIST_POSTS,model)==input);
    System.out.println("Repost visibility and display filtering checks passed: " + checks);
  }
}''',
        }
        helper = ROOT / "fixes/source/extensions/newx/src/main/java/app/morphe/extension/newx/timeline/ListReadingPosition.java"
        with tempfile.TemporaryDirectory(prefix="piko-list-tests-") as folder:
            directory = Path(folder)
            inputs = [str(helper), str(helper.with_name("TimelineRepostFilter.java"))]
            for name, text in fixtures.items():
                path = directory / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
                inputs.append(str(path))
            subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), *inputs], check=True)
            subprocess.run(["java", "-cp", str(directory), "ListFixTest"], check=True)
            subprocess.run(["java", "-cp", str(directory), "RepostTest"], check=True)
