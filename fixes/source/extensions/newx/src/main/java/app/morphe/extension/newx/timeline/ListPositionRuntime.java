package app.morphe.extension.newx.timeline;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import app.morphe.extension.shared.Utils;

/** All native adapter bodies are replaced with verified direct bytecode accesses. */
public final class ListPositionRuntime {
    private static final Map<Object,String> SCOPES = new WeakHashMap<>();
    private static final Map<Object,WeakReference<Object>> PROVIDERS = new WeakHashMap<>();
    private static final Map<Object,Session> ACTIVE = new WeakHashMap<>();
    private static final Map<Object,WeakReference<Object>> INTERACTIONS = new WeakHashMap<>();
    private static Handler handler;
    private static boolean scheduled;
    private static final class Session {
        final String scope;
        final ListAnchorState state;
        WeakReference<Object> provider = new WeakReference<>(null);
        Object previousLayout;
        long changedAt, lastWrite;
        String writtenKey;
        int writtenOffset = -1;
        Session(String scope) { this.scope=scope; state=new ListAnchorState(load(scope)); }
    }
    private ListPositionRuntime() {}
    public static void register(Object flow, Enum<?> type, String id, long account) {
        if (flow == null) return;
        synchronized (SCOPES) {
            if (ListReadingPosition.restore(type,id) != null && id != null && !id.isEmpty() && account > 0)
                SCOPES.put(flow,account+":"+id.length()+":"+id);
            else SCOPES.remove(flow);
        }
    }
    public static void bind(Object component, Object lazy) {
        if (Looper.myLooper()!=Looper.getMainLooper()) return;
        try {
            Object flow=nativeFlow(component); String scope;
            synchronized(SCOPES) { scope=SCOPES.get(flow); }
            if(scope==null || lazy==null) return;
            Session old=ACTIVE.get(lazy);
            if(old!=null && old.scope.equals(scope)) return;
            if(old!=null) persist(old,true);
            Session session=new Session(scope); ACTIVE.put(lazy,session);
            INTERACTIONS.put(nativeInteraction(lazy),new WeakReference<>(lazy));
            WeakReference<Object> ref=PROVIDERS.get(lazy);
            if(ref!=null && ref.get()!=null) render(lazy,ref.get());
            trace("bind",session); schedule();
        } catch(RuntimeException error) { Log.d("PikoListAnchor","bind unavailable"); }
    }
    public static void pause(Object lazy) {
        if(Looper.myLooper()!=Looper.getMainLooper()) return;
        Session session=ACTIVE.remove(lazy);
        if(session!=null) {
            observe(lazy,session); persist(session,true);
            INTERACTIONS.remove(nativeInteraction(lazy)); trace("pause",session);
        }
    }
    public static void render(Object lazy,Object provider) {
        if(Looper.myLooper()!=Looper.getMainLooper()) return;
        PROVIDERS.put(lazy,new WeakReference<>(provider));
        Session session=ACTIVE.get(lazy);
        if(session==null || session.provider.get()==provider) return;
        try {
            observe(lazy,session); // old measured key is compared with OLD final keys
            int count=nativeCount(provider);
            if(count<0 || count>20000) return;
            String[] keys=new String[count];
            for(int i=0;i<count;i++) keys[i]=nativeKey(nativeKeyAt(provider,i));
            session.provider=new WeakReference<>(provider);
            if(session.state.entries(keys)) {
                session.previousLayout=nativeLayout(lazy);
                session.changedAt=SystemClock.uptimeMillis(); trace("data",session);
            }
            schedule();
        } catch(RuntimeException error) { session.state.cancel(); }
    }
    public static void interaction(Object source) {
        if(Looper.myLooper()!=Looper.getMainLooper()) return;
        WeakReference<Object> ref=INTERACTIONS.get(source);
        Session session=ref==null?null:ACTIVE.get(ref.get());
        if(session!=null) { session.state.cancel(); trace("gesture-cancel",session); }
    }
    public static void top(Object flow) {
        if(Looper.myLooper()!=Looper.getMainLooper()) return;
        String scope; synchronized(SCOPES) { scope=SCOPES.get(flow); }
        if(scope==null) return;
        for(Session session:new ArrayList<>(ACTIVE.values())) if(scope.equals(session.scope)) {
            session.state.cancel(); trace("top-cancel",session);
        }
        try { SharedPreferences p=preferences(); if(p!=null) p.edit().remove(scope+".key").remove(scope+".offset").apply(); }
        catch(RuntimeException ignored) {}
    }
    private static void schedule() {
        if(scheduled || ACTIVE.isEmpty()) return;
        if(handler==null) handler=new Handler(Looper.getMainLooper());
        scheduled=true; handler.postDelayed(ListPositionRuntime::tick,80);
    }
    private static void tick() {
        scheduled=false;
        for(Map.Entry<Object,Session> entry:new ArrayList<>(ACTIVE.entrySet())) {
            Object lazy=entry.getKey(); Session session=entry.getValue();
            try {
                int[] snapshot=nativeSnapshot(lazy);
                if(snapshot==null || snapshot[2]!=0 || session.provider.get()==null) continue;
                if(session.state.pending()) {
                    if(nativeMeasuredKey(lazy)==null || nativeLayout(lazy)==session.previousLayout) continue;
                    if(SystemClock.uptimeMillis()-session.changedAt<240) continue;
                    int[] target=session.state.resolve(session.state.generation());
                    if(target!=null) {
                        nativeRequest(lazy,target[0],target[1]); trace("restore",session); continue;
                    }
                    trace("missing",session);
                }
                observe(lazy,session); persist(session,false);
            } catch(RuntimeException error) { session.state.cancel(); }
        }
        schedule();
    }
    private static void observe(Object lazy,Session session) {
        if(session.provider.get()==null || session.state.pending()) return;
        try {
            int[] snapshot=nativeSnapshot(lazy);
            if(snapshot!=null && snapshot[2]==0)
                session.state.observe(nativeKey(nativeMeasuredKey(lazy)),snapshot[0],snapshot[1]);
        } catch(RuntimeException ignored) {}
    }
    private static ListAnchorState.Anchor load(String scope) {
        try {
            SharedPreferences p=preferences(); if(p==null) return null;
            String key=p.getString(scope+".key",null); int offset=p.getInt(scope+".offset",-1);
            return key==null || key.length()>2048 || offset<0 || offset>100000?null:new ListAnchorState.Anchor(key,offset);
        } catch(RuntimeException ignored) { return null; }
    }
    private static void persist(Session session,boolean force) {
        ListAnchorState.Anchor a=session.state.current();
        if(a==null || session.state.pending() || (a.key.equals(session.writtenKey) && a.offset==session.writtenOffset)) return;
        long now=SystemClock.uptimeMillis(); if(!force && now-session.lastWrite<700) return;
        try {
            SharedPreferences p=preferences(); if(p==null) return;
            p.edit().putString(session.scope+".key",a.key).putInt(session.scope+".offset",a.offset).apply();
            session.writtenKey=a.key; session.writtenOffset=a.offset; session.lastWrite=now;
        } catch(RuntimeException ignored) {}
    }
    public static String pack(String kind,String id,String extra) {
        return id==null || extra==null || id.length()>1024 || extra.length()>256?null:kind+":"+id.length()+":"+id+":"+extra;
    }
    private static void trace(String action,Session s) {
        Log.d("PikoListAnchor",action+" scope="+Integer.toHexString(s.scope.hashCode())+" generation="+s.state.generation());
    }
    private static SharedPreferences preferences() {
        Context c=Utils.getContext(); return c==null?null:c.getSharedPreferences("piko_newx_list_anchors_v2",Context.MODE_PRIVATE);
    }
    private static Object nativeFlow(Object component) { return null; }
    private static Object nativeInteraction(Object lazy) { return null; }
    private static Object nativeLayout(Object lazy) { return null; }
    private static Object nativeMeasuredKey(Object lazy) { return null; }
    private static int[] nativeSnapshot(Object lazy) { return null; }
    private static void nativeRequest(Object lazy,int index,int offset) {}
    private static int nativeCount(Object provider) { return 0; }
    private static Object nativeKeyAt(Object provider,int index) { return null; }
    private static String nativeKey(Object key) { return null; }
}
