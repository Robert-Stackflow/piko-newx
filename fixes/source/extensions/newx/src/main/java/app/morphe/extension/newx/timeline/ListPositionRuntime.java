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
    private static boolean rendererSeen;
    private static boolean timelineDrawSeen, rendererEntrySeen;
    public static void traceTimelineDraw() {
        if (!timelineDrawSeen) {
            timelineDrawSeen = true;
            Log.d("PikoListAnchor", "timeline-draw");
        }
    }
    public static void traceRendererEntry() {
        if (!rendererEntrySeen) {
            rendererEntrySeen = true;
            Log.d("PikoListAnchor", "renderer-entry");
        }
    }
    private static final class Session {
        final String scope;
        final ListAnchorState state;
        WeakReference<Object> provider = new WeakReference<>(null);
        long lastWrite;
        String writtenKey;
        String awaitingKey;
        long awaitingGeneration, blockedGeneration = -1, readyGeneration = -1;
        boolean userTopPending;
        long requestedAt;
        int writtenOffset = -1;
        boolean loggedRender, loggedMissingProvider, loggedMissingSnapshot, loggedSnapshot, loggedCount;
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
            if(old!=null) { observe(lazy,old); persist(old,true); }
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
            // Capture a coherent measured frame even when leaving during a fling.
            observe(lazy,session); persist(session,true);
            INTERACTIONS.remove(nativeInteraction(lazy)); trace("pause",session);
        }
    }
    public static void render(Object lazy,Object provider) {
        if(!rendererSeen) {
            rendererSeen=true;
            Log.d("PikoListAnchor",Looper.myLooper()==Looper.getMainLooper()?"renderer-main":"renderer-background");
        }
        if(Looper.myLooper()!=Looper.getMainLooper()) return;
        // Called inside Compose's derived item-provider calculation. NEVER read snapshots here:
        // provider -> layoutInfo -> provider is a cyclic dependency and can freeze the main thread.
        PROVIDERS.put(lazy,new WeakReference<>(provider));
        Session session=ACTIVE.get(lazy);
        if(session!=null && !session.loggedRender) {
            session.loggedRender=true;
            trace("render-provider",session);
        }
        schedule();
    }
    private static void publish(Object lazy,Object provider) {
        Session session=ACTIVE.get(lazy);
        if(session==null || session.provider.get()==provider) return;
        try {
            observe(lazy,session); // old measured key is compared with OLD final keys
            int count=nativeCount(provider);
            if(!session.loggedCount) {
                session.loggedCount=true;
                Log.d("PikoListAnchor","provider-count="+count+" scope="+Integer.toHexString(session.scope.hashCode()));
            }
            if(count<0 || count>20000) return;
            String[] keys=new String[count];
            for(int i=0;i<count;i++) keys[i]=nativeKey(nativeKeyAt(provider,i));
            session.provider=new WeakReference<>(provider);
            if(session.state.entries(keys)) {
                session.readyGeneration=-1; trace("data",session);
            }
            schedule();
        } catch(RuntimeException error) {
            Log.d("PikoListAnchor","provider-unavailable="+error.getClass().getSimpleName());
        }
    }
    public static void interaction(Object source) {
        if(Looper.myLooper()!=Looper.getMainLooper()) return;
        WeakReference<Object> ref=INTERACTIONS.get(source);
        Session session=ref==null?null:ACTIVE.get(ref.get());
        if(session!=null) {
            session.state.cancel(); session.awaitingKey=null; session.userTopPending=false;
            trace("gesture-cancel",session);
        }
    }
    public static void top(Object flow) {
        if(Looper.myLooper()!=Looper.getMainLooper()) return;
        String scope; synchronized(SCOPES) { scope=SCOPES.get(flow); }
        if(scope==null) return;
        for(Session session:new ArrayList<>(ACTIVE.values())) if(scope.equals(session.scope)) {
            session.state.clear(); session.awaitingKey=null;
            session.userTopPending=true;
            session.writtenKey=null; session.writtenOffset=-1; trace("user-top",session);
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
                WeakReference<Object> ref=PROVIDERS.get(lazy);
                Object provider=ref==null?null:ref.get();
                int[] snapshot=nativeSnapshot(lazy);
                if(snapshot==null) {
                    if(!session.loggedMissingSnapshot) {
                        session.loggedMissingSnapshot=true; trace("snapshot-null",session);
                    }
                    continue;
                }
                if(!session.loggedSnapshot) {
                    session.loggedSnapshot=true;
                    Log.d("PikoListAnchor","snapshot-index="+snapshot[0]+" scope="+Integer.toHexString(session.scope.hashCode()));
                }
                if(provider==null && !session.loggedMissingProvider) {
                    session.loggedMissingProvider=true; trace("provider-null",session);
                }
                // A real ongoing scroll wins over automatic restoration. Recording
                // its coherent frame is allowed; a fling must not erase the bookmark.
                if(snapshot[2]!=0) { session.state.cancel(); session.awaitingKey=null; }
                if(provider!=null) publish(lazy,provider);
                if(session.provider.get()==null) continue;
                if(snapshot[2]!=0) {
                    session.state.cancel(); observe(lazy,session); persist(session,false); continue;
                }
                long generation=session.state.generation();
                if(!coherent(lazy,session,snapshot)) continue;
                if(session.userTopPending) {
                    if(snapshot[0]!=0 || snapshot[1]!=0) continue;
                    session.userTopPending=false;
                }
                if(session.awaitingKey!=null && session.awaitingGeneration!=generation) session.awaitingKey=null;
                if(session.awaitingKey!=null) {
                    if(session.state.confirm(generation,nativeKey(nativeMeasuredKey(lazy)),snapshot[0],snapshot[1])) {
                        session.awaitingKey=null; trace("confirmed",session);
                    }
                    else if(SystemClock.uptimeMillis()-session.requestedAt<1000) continue;
                    else {
                        // A timeout is not evidence that the old bookmark is invalid.
                        // Retry only on a new data generation, never in a scroll loop.
                        session.awaitingKey=null; session.blockedGeneration=generation; trace("unconfirmed",session);
                    }
                }
                if(session.state.pending()) {
                    if(!session.state.hasItems() || session.blockedGeneration==generation) continue;
                    // Two coherent observations, not an arbitrary refresh delay.
                    if(session.readyGeneration!=generation) { session.readyGeneration=generation; continue; }
                    int[] target=session.state.resolve(generation);
                    if(target!=null) {
                        if(session.state.confirm(generation,nativeKey(nativeMeasuredKey(lazy)),snapshot[0],snapshot[1])) {
                            persist(session,false); continue; // Native key preservation already did the work.
                        }
                        session.awaitingKey=session.state.keyAt(target[0]);
                        session.awaitingGeneration=generation;
                        session.requestedAt=SystemClock.uptimeMillis();
                        nativeRequest(lazy,target[0],target[1]); trace("restore",session); continue;
                    }
                    if(session.readyGeneration==generation) {
                        trace("missing-retained",session); session.blockedGeneration=generation;
                    }
                    continue; // Do not replace a missing bookmark with the new head.
                }
                observe(lazy,session); persist(session,false);
            } catch(RuntimeException error) {
                session.awaitingKey=null; session.blockedGeneration=session.state.generation();
                trace("deferred-error",session);
            }
        }
        schedule();
    }
    private static void observe(Object lazy,Session session) {
        if(session.provider.get()==null || session.state.pending() || session.awaitingKey!=null || session.userTopPending) return;
        try {
            int[] snapshot=nativeSnapshot(lazy);
            if(snapshot!=null && coherent(lazy,session,snapshot))
                session.state.observe(nativeKey(nativeMeasuredKey(lazy)),snapshot[0],snapshot[1]);
        } catch(RuntimeException ignored) {}
    }
    private static boolean coherent(Object lazy,Session session,int[] snapshot) {
        Object provider=session.provider.get();
        if(provider==null || snapshot[0]<0 || snapshot[0]>=nativeCount(provider)) return false;
        Object measured=nativeMeasuredKey(lazy);
        return measured!=null && measured.equals(nativeKeyAt(provider,snapshot[0]));
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
        if(a==null || session.state.pending() || session.awaitingKey!=null ||
            (a.key.equals(session.writtenKey) && a.offset==session.writtenOffset)) return;
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
