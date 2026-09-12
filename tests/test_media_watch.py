import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WatchTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "Needs Java 21")
    def test_immediate_foreground_selection_and_revisit(self):
        source = ROOT / "features/source/extensions/newx/src/main/java/app/morphe/extension/newx/mediatools/WatchSession.java"
        harness = '''import app.morphe.extension.newx.mediatools.WatchSession;
public class WatchTest {
  static int checks;
  static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("watch " + checks); }
  public static void main(String[] args) {
    WatchSession s=new WatchSession();
    check(!s.sample("A",false,true)); // prefetch is not a visit
    check(!s.sample("A",true,false)); // background is not a visit
    check(s.sample("A",true,true)); // immediate, no playback requirement
    check(!s.sample("A",true,true)); // deduplicate repeated state events
    check(!s.sample("B",false,true)); // neighbor preload cannot reset A
    check(!s.sample("A",true,true));
    check(s.sample("B",true,true)); // swipe: immediate
    check(s.sample("C",true,true)); // rapid next swipe: immediate
    check(s.sample("A",true,true)); // revisit updates timestamp
    check(!s.sample("A",true,true));
    check(!s.sample(null,false,true)); // invalid unselected event cannot reset A
    check(!s.sample("A",true,true));
    check(!s.sample("A",true,false)); // viewer background ends visit
    check(s.sample("A",true,true)); // foreground return is a new visit
    check(!s.sample("A",false,true));
    check(!s.sample("A",true,true));
    check(!s.sample(null,true,true)); // selected page has no video
    check(s.sample("A",true,true));
    check(!s.sample("",true,true));
    check(s.sample("A",true,true));
    s.reset();
    check(s.sample("A",true,true));
    check(s.sample("account-2/post-1/media-1",true,true));
    check(s.sample("account-2/post-1/media-2",true,true)); // same post, different video
    check(s.sample("account-3/post-1/media-2",true,true)); // account partition
    System.out.println("Foreground visit checks passed: " + checks);
  }
}'''
        with tempfile.TemporaryDirectory(prefix="piko-watch-") as directory:
            test = Path(directory) / "WatchTest.java"
            test.write_text(harness, encoding="utf-8")
            subprocess.run(["javac", "-d", directory, str(source), str(test)], check=True)
            subprocess.run(["java", "-cp", directory, "WatchTest"], check=True)
