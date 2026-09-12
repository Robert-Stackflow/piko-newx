import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class ResumeTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "Needs Java 21")
    def test_user_seek_timeout_and_duration_guards(self):
        source = ROOT / "features/source/extensions/newx/src/main/java/app/morphe/extension/newx/mediatools/ResumePolicy.java"
        harness = '''import app.morphe.extension.newx.mediatools.ResumePolicy;
public class ResumeTest {
  static int checks;
  static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("resume " + checks); }
  public static void main(String[] args) {
    ResumePolicy p = new ResumePolicy();
    check(p.choose(0,0,0,false,15000,60000) == -1); check(p.pending());
    check(p.choose(500,500,60000,true,15000,60000) == 15000); check(!p.pending());
    check(p.choose(800,800,60000,true,15000,60000) == -1);
    p = new ResumePolicy(); p.cancel();
    check(p.choose(500,0,60000,true,15000,60000) == -1); // manual seek to zero
    p = new ResumePolicy();
    check(p.choose(4001,0,60000,true,15000,60000) == -1); check(!p.pending());
    p = new ResumePolicy();
    check(p.choose(500,1600,60000,true,15000,60000) == -1); check(!p.pending());
    p = new ResumePolicy();
    check(p.choose(500,0,60000,true,59000,60000) == -1); // almost finished
    p = new ResumePolicy();
    check(p.choose(500,0,60000,true,1000,60000) == -1); // insignificant saved progress
    p = new ResumePolicy();
    check(p.choose(500,0,60000,true,15000,30000) == -1); // different duration/variant
    p = new ResumePolicy();
    check(p.choose(500,0,60000,false,15000,60000) == -1); check(p.pending());
    check(p.choose(700,0,60000,true,15000,60000) == 15000);
    p = new ResumePolicy();
    check(p.choose(100,-1,60000,true,15000,60000) == -1); check(!p.pending());
    p = new ResumePolicy();
    check(p.choose(500,0,60000,true,-1,-1) == -1); check(!p.pending());
    System.out.println("Resume safety checks passed: " + checks);
  }
}'''
        with tempfile.TemporaryDirectory(prefix="piko-resume-") as temporary:
            test = Path(temporary) / "ResumeTest.java"
            test.write_text(harness, encoding="utf-8")
            subprocess.run(["javac", "-d", temporary, str(source), str(test)], check=True)
            subprocess.run(["java", "-cp", temporary, "ResumeTest"], check=True)
