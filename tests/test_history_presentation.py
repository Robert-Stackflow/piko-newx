import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class HistoryPresentationTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "Needs Java 21")
    def test_cycle_and_media_mosaic_geometry(self):
        source = ROOT / "features/source/extensions/newx/src/main/java/app/morphe/extension/newx/mediatools/HistoryPresentation.java"
        harness = '''import app.morphe.extension.newx.mediatools.HistoryPresentation;
public class HistoryPresentationTest {
  static int checks;
  static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("presentation " + checks); }
  public static void main(String[] args) {
    int mode = 0;
    String[] kinds={"", "post", "video"}, glyphs={"all", "post", "video"};
    for (int i=0; i<12; i++) {
      check(mode == i%3); check(HistoryPresentation.kind(mode).equals(kinds[i%3]));
      check(HistoryPresentation.glyph(mode).equals(glyphs[i%3])); mode=HistoryPresentation.nextMode(mode);
    }
    check(HistoryPresentation.mode(-1)==0); check(HistoryPresentation.mode(3)==0);
    for (int w : new int[]{300,701,924}) for(int count=1; count<=4; count++) {
      int h=w*2/3; int[][] r=new int[count][];
      for(int i=0;i<count;i++) {
        r[i]=HistoryPresentation.tile(count,i,w,h,6);
        check(r[i][0]>=0 && r[i][1]>=0 && r[i][2]<=w && r[i][3]<=h);
        check(r[i][2]>r[i][0] && r[i][3]>r[i][1]);
        for(int j=0;j<i;j++) check(r[i][0]>=r[j][2] || r[i][2]<=r[j][0] || r[i][1]>=r[j][3] || r[i][3]<=r[j][1]);
      }
      check(r[0][0]==0 && r[0][1]==0); check(r[count-1][2]==w && r[count-1][3]==h);
      if(count==3) check(r[0][3]==h && r[1][0]==r[2][0] && r[1][3]<r[2][1]);
    }
    System.out.println("History layout/mode checks passed: " + checks);
  }
}'''
        with tempfile.TemporaryDirectory(prefix="piko-history-ui-") as directory:
            test = Path(directory) / "HistoryPresentationTest.java"
            test.write_text(harness, encoding="utf-8")
            subprocess.run(["javac", "-d", directory, str(source), str(test)], check=True)
            subprocess.run(["java", "-cp", directory, "HistoryPresentationTest"], check=True)
