from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'app/morphe/extension/newx/timeline/'


class RuntimeTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'Needs Java')
    def test_runtime_lifecycle_and_races(self):
        # Only the nine patch-time native stubs are replaced. Run the actual observer/state/store.
        replacements = {
            'private static Object nativeFlow(Object component) { return null; }': 'private static Object nativeFlow(Object component) { return component; }',
            'private static Object nativeInteraction(Object lazy) { return null; }': 'private static Object nativeInteraction(Object lazy) { return lazy; }',
            'private static Object nativeLayout(Object lazy) { return null; }': 'private static Object nativeLayout(Object lazy) { return ((Fixture.Lazy)lazy).layout; }',
            'private static Object nativeMeasuredKey(Object lazy) { return null; }': 'private static Object nativeMeasuredKey(Object lazy) { return ((Fixture.Lazy)lazy).key; }',
            'private static int[] nativeSnapshot(Object lazy) { return null; }': 'private static int[] nativeSnapshot(Object lazy) { var s=(Fixture.Lazy)lazy; return new int[]{s.index,s.offset,s.moving?1:0}; }',
            'private static void nativeRequest(Object lazy,int index,int offset) {}': 'private static void nativeRequest(Object lazy,int index,int offset) { var s=(Fixture.Lazy)lazy; s.requests++; s.index=index; s.offset=offset; s.key=s.keys[index]; s.layout=new Object(); }',
            'private static int nativeCount(Object provider) { return 0; }': 'private static int nativeCount(Object provider) { return ((String[])provider).length; }',
            'private static Object nativeKeyAt(Object provider,int index) { return null; }': 'private static Object nativeKeyAt(Object provider,int index) { return ((String[])provider)[index]; }',
            'private static String nativeKey(Object key) { return null; }': 'private static String nativeKey(Object key) { return (String)key; }',
        }
        fixtures = {
            'android/util/Log.java': 'package android.util; public class Log { public static int d(String a,String b){return 0;} }',
            'android/os/Looper.java': 'package android.os; public class Looper { static final Looper L=new Looper(); public static Looper myLooper(){return L;} public static Looper getMainLooper(){return L;} }',
            'android/os/SystemClock.java': 'package android.os; public class SystemClock { public static long now=10000; public static long uptimeMillis(){return now;} }',
            'android/os/Handler.java': '''package android.os;
public class Handler { static java.util.Queue<Runnable> q=new java.util.ArrayDeque<>();
 public Handler(Looper l){} public boolean postDelayed(Runnable r,long d){q.add(r);return true;}
 public static void step(){SystemClock.now+=80; Runnable r=q.poll(); if(r!=null)r.run();} }''',
            'android/content/Context.java': '''package android.content; public class Context {
 public static final int MODE_PRIVATE=0; static final java.util.Map<String,SharedPreferences> p=new java.util.HashMap<>();
 public SharedPreferences getSharedPreferences(String n,int m){return p.computeIfAbsent(n,k->new SharedPreferences());} }''',
            'android/content/SharedPreferences.java': '''package android.content; public class SharedPreferences {
 java.util.Map<String,Object> p=new java.util.HashMap<>(); public String getString(String k,String d){return (String)p.getOrDefault(k,d);}
 public int getInt(String k,int d){return (Integer)p.getOrDefault(k,d);} public Editor edit(){return new Editor();}
 public class Editor { public Editor putString(String k,String v){p.put(k,v);return this;} public Editor putInt(String k,int v){p.put(k,v);return this;}
 public Editor remove(String k){p.remove(k);return this;} public void apply(){} } }''',
            'app/morphe/extension/shared/Utils.java': 'package app.morphe.extension.shared; public class Utils { static android.content.Context c=new android.content.Context(); public static android.content.Context getContext(){return c;} }',
            'app/morphe/extension/newx/settings/SettingsRegistry.java': 'package app.morphe.extension.newx.settings; public class SettingsRegistry { public static boolean getBooleanOrDefault(String k,boolean d){return d;} }',
            PACKAGE+'Fixture.java': '''package app.morphe.extension.newx.timeline;
public class Fixture {
 enum Type { LIST_POSTS, FOLLOWING }
 static int checks;
 static class Lazy { Object layout=new Object(); String[] keys; String key; int index,offset,requests; boolean moving;
   Lazy(String...keys){this.keys=keys;key=keys.length==0?null:keys[0];} }
 static void check(boolean v){if(!v)throw new AssertionError("runtime "+checks); checks++;}
 static void steps(int n){for(int i=0;i<n;i++)android.os.Handler.step();}
 static Object flow(long account,String id){Object f=new Object(); ListPositionRuntime.register(f,Type.LIST_POSTS,id,account);return f;}
 public static void main(String[] args){
   Object f=flow(1,"list"); Lazy s=new Lazy("a","b","c");
   ListPositionRuntime.bind(f,s); ListPositionRuntime.render(s,s.keys); steps(3); check(s.requests==0);
   s.index=1;s.offset=60;s.key="b";steps(12);
   String[] next={"new",null,"a","b","c"};ListPositionRuntime.render(s,next);s.keys=next;
   s.index=0;s.offset=0;s.key="new";s.layout=new Object(); steps(8);
   check(s.requests==1);check(s.index==3 && s.offset==60);check(next.length==5 && next[0].equals("new"));
   steps(12);ListPositionRuntime.pause(s);
   Lazy restart=new Lazy(next);ListPositionRuntime.render(restart,restart.keys);ListPositionRuntime.bind(f,restart);
   steps(8);check(restart.requests==1);check(restart.index==3 && restart.offset==60);
   Lazy other=new Lazy(next);Object f2=flow(2,"list");ListPositionRuntime.bind(f2,other);ListPositionRuntime.render(other,other.keys);
   steps(8);check(other.requests==0);ListPositionRuntime.pause(other);
   String[] newer={"newer","b"};ListPositionRuntime.render(restart,newer);restart.keys=newer;restart.layout=new Object();
   ListPositionRuntime.interaction(restart);restart.index=0;restart.key="newer";restart.offset=2;
   steps(8);check(restart.requests==1);
   String[] missing={"absent"};ListPositionRuntime.render(restart,missing);restart.keys=missing;restart.key="absent";restart.layout=new Object();restart.offset=0;
   steps(8);check(restart.requests==1);check(missing.length==1);
   ListPositionRuntime.top(f);ListPositionRuntime.pause(restart);
   Lazy top=new Lazy(next);ListPositionRuntime.bind(f,top);ListPositionRuntime.render(top,top.keys);steps(8);check(top.requests==0);
   ListPositionRuntime.pause(top);int r=top.requests;steps(20);check(top.requests==r);
   check(!ListPositionRuntime.pack("r","a:b","c").equals(ListPositionRuntime.pack("r","a","b:c")));
   System.out.println("Runtime lifecycle/race checks passed: "+checks);
 } }''',
        }
        with tempfile.TemporaryDirectory(prefix='piko-runtime-') as folder:
            directory = Path(folder)
            native = ROOT / 'fixes/source/extensions/newx/src/main/java' / PACKAGE
            for name in ('ListAnchorState.java', 'ListReadingPosition.java', 'ListPositionRuntime.java'):
                content = (native/name).read_text(encoding='utf-8')
                if name == 'ListPositionRuntime.java':
                    for old, new in replacements.items():
                        self.assertEqual(content.count(old), 1)
                        content = content.replace(old, new)
                fixtures[PACKAGE+name] = content
            inputs=[]
            for name,content in fixtures.items():
                path=directory/name
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text(content,encoding='utf-8')
                inputs.append(str(path))
            subprocess.run(['javac','-d',folder,*inputs],check=True)
            subprocess.run(['java','-cp',folder,'app.morphe.extension.newx.timeline.Fixture'],check=True)
