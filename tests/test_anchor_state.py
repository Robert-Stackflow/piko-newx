from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class AnchorTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'Needs Java')
    def test_identity_state_machine(self):
        source = ROOT / 'fixes/source/extensions/newx/src/main/java/app/morphe/extension/newx/timeline/ListAnchorState.java'
        program = '''import java.util.*;
import app.morphe.extension.newx.timeline.ListAnchorState;
public class AnchorTest {
  static int checks;
  static void check(boolean b) { if(!b) throw new AssertionError("check "+checks); checks++; }
  static void target(ListAnchorState s,int i,int o) { check(Arrays.equals(s.resolve(s.generation()),new int[]{i,o})); }
  public static void main(String[] args) {
    var s=new ListAnchorState(new ListAnchorState.Anchor("b",90));
    s.entries(new String[]{null}); check(s.pending());
    s.entries(new String[]{"a","b","c"}); target(s,1,90); check(!s.pending());
    check(s.resolve(s.generation())==null);
    check(s.observe("b",1,72));
    String[] input={"new",null,"a","b","c"}; s.entries(input); input[3]="mutated";
    target(s,3,72); check(s.keyAt(3).equals("b"));
    s.entries(new String[]{"a","c"}); check(s.resolve(s.generation())==null); check(s.current()==null);
    check(s.observe("c",1,15)); s.entries(new String[]{"c","c"}); check(s.resolve(s.generation())==null);
    check(!s.observe("c",0,1));
    s.entries(new String[]{"a","b"}); check(s.observe("b",1,3));
    s.entries(new String[]{"x","a","b"}); long stale=s.generation();
    s.entries(new String[]{"y","x","a","b"}); check(s.resolve(stale)==null); target(s,3,3);
    check(s.observe("b",3,40)); s.entries(new String[]{"z","b"}); s.cancel();
    check(s.resolve(s.generation())==null); s.entries(new String[]{"late","z","b"}); check(!s.pending());
    check(!s.observe("b",0,0)); check(!s.observe("b",2,-1)); check(s.observe("b",2,10));
    s.entries(new String[0]); check(s.current().key.equals("b"));
    s.entries(new String[]{"b"}); target(s,0,10);
    var account2=new ListAnchorState(null); account2.entries(new String[]{"b"}); check(!account2.pending());
    var restart=new ListAnchorState(new ListAnchorState.Anchor("deleted",1));
    restart.entries(new String[]{"a"}); check(restart.resolve(restart.generation())==null);
    for(int n=0;n<100;n++) {
      var p=new ListAnchorState(new ListAnchorState.Anchor("anchor",n));
      String[] posts=new String[n+1]; Arrays.fill(posts,null); posts[n]="anchor";
      p.entries(posts); target(p,n,n); check(posts[n].equals("anchor"));
    }
    System.out.println("Anchor state checks passed: "+checks);
  }
}'''
        with tempfile.TemporaryDirectory(prefix='piko-anchor-') as directory:
            test = Path(directory) / 'AnchorTest.java'
            test.write_text(program, encoding='utf-8')
            subprocess.run(['javac', '-d', directory, str(source), str(test)], check=True)
            subprocess.run(['java', '-cp', directory, 'AnchorTest'], check=True)
