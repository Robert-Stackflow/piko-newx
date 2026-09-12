package app.morphe.extension.newx.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import app.morphe.extension.newx.settings.SettingsRegistry;

/** Display-only filter. Never deletes repository items or changes network pagination. */
public final class TimelineRepostFilter {
    private static final Object REMOVED = new Object();

    public static boolean showReposts(Enum<?> timeline) {
        if (timeline == null) return true;
        String suffix;
        switch (timeline.name()) {
            case "FOR_YOU": suffix = "for_you"; break;
            case "FOLLOWING":
            case "RANKED_FOLLOWING": suffix = "following"; break;
            case "LIST_POSTS": suffix = "lists"; break;
            default: return true;
        }
        return SettingsRegistry.getBooleanOrDefault("newx.timeline.show_reposts_" + suffix, true);
    }

    public interface Model {
        boolean repost(Object item);
        boolean wrapper(Object item);
        Object unwrap(Object item);
        Object rewrap(Object old, Object item);
        List<?> children(Object item);
        Object replaceChildren(Object old, List<?> children, Set<Object> removedIds);
        Object postId(Object item);
        Object immutable(List<Object> items);
    }

    private static final Model NATIVE = new Model() {
        public boolean repost(Object item) { return isTimelinePost(item) && isRepostContext(getPostSocialContext(item)); }
        public boolean wrapper(Object item) { return isTimelineModuleItem(item); }
        public Object unwrap(Object item) { return getModuleItem(item); }
        public Object rewrap(Object old, Object item) { return copyModuleItem(old, item, isModuleItemDispensable(old)); }
        public List<?> children(Object item) { return isTimelineModule(item) ? getModuleInnerContent(item) : null; }
        public Object postId(Object item) { return isTimelinePost(item) ? getPostId(item) : null; }
        public Object immutable(List<Object> items) { return immutableList(items); }
        public Object replaceChildren(Object old, List<?> children, Set<Object> removedIds) {
            Object display = getModuleDisplayType(old);
            if (!removedIds.isEmpty() && isVerticalConversation(display)) {
                List<?> ids = getVerticalConversationPostIds(display);
                if (ids != null) {
                    List<Object> kept = new ArrayList<>();
                    for (Object id : ids) if (!removedIds.contains(id)) kept.add(id);
                    if (kept.size() != ids.size()) display = copyVerticalConversation(display, kept);
                }
            }
            return copyModule(old, children, getModuleHeader(old), getModuleFooter(old), display,
                    getModuleSortIndex(old), getModuleEntryId(old), getModuleClientEventInfo(old));
        }
    };

    public static Object filter(Object items, Enum<?> timeline) {
        return filter(items, timeline, NATIVE);
    }

    public static Object filter(Object items, Enum<?> timeline, Model model) {
        if (showReposts(timeline) || !(items instanceof List<?>)) return items;
        try {
            List<?> original = (List<?>) items;
            List<Object> filtered = new ArrayList<>(original.size());
            boolean changed = false;
            for (Object item : original) {
                Object result = filterItem(item, model, 0);
                if (result != item) changed = true;
                if (result != REMOVED) filtered.add(result);
            }
            return changed ? model.immutable(filtered) : items;
        } catch (RuntimeException ignored) {
            // Unknown model shapes must not empty the timeline.
            return items;
        }
    }

    private static Object filterItem(Object item, Model model, int depth) {
        if (item == null || depth > 32) return item;
        if (model.wrapper(item)) {
            Object inner = model.unwrap(item);
            Object result = filterItem(inner, model, depth + 1);
            if (result == REMOVED) return REMOVED;
            return result == inner ? item : model.rewrap(item, result);
        }
        if (model.repost(item)) return REMOVED;
        List<?> children = model.children(item);
        if (children == null || children.isEmpty()) return item;
        List<Object> kept = new ArrayList<>();
        Set<Object> removedIds = new HashSet<>();
        boolean changed = false;
        for (Object child : children) {
            Object result = filterItem(child, model, depth + 1);
            if (result != child) changed = true;
            if (result != REMOVED) kept.add(result);
            else {
                Object post = model.wrapper(child) ? model.unwrap(child) : child;
                Object id = model.postId(post);
                if (id != null) removedIds.add(id);
            }
        }
        if (!changed) return item;
        if (kept.isEmpty()) return REMOVED;
        return model.replaceChildren(item, kept, removedIds);
    }

    // Generated from the already-resolved upstream model adapters at patch time.
    private static boolean isTimelinePost(Object item) { return false; }
    private static boolean isTimelineModule(Object item) { return false; }
    private static boolean isTimelineModuleItem(Object item) { return false; }
    private static Object getModuleItem(Object item) { return null; }
    private static boolean isModuleItemDispensable(Object item) { return false; }
    private static Object copyModuleItem(Object old, Object item, boolean dispensable) { return old; }
    private static List<?> getModuleInnerContent(Object item) { return null; }
    private static Object getModuleDisplayType(Object item) { return null; }
    private static Object getModuleHeader(Object item) { return null; }
    private static Object getModuleFooter(Object item) { return null; }
    private static long getModuleSortIndex(Object item) { return 0; }
    private static String getModuleEntryId(Object item) { return null; }
    private static Object getModuleClientEventInfo(Object item) { return null; }
    private static Object getPostId(Object item) { return null; }
    private static boolean isVerticalConversation(Object item) { return false; }
    private static List<?> getVerticalConversationPostIds(Object item) { return null; }
    private static Object copyVerticalConversation(Object old, List<?> ids) { return old; }
    private static Object copyModule(Object old, List<?> children, Object header, Object footer,
                                     Object display, long sortIndex, String entryId, Object info) { return old; }
    private static Object immutableList(List<Object> items) { return Collections.unmodifiableList(items); }
    private static Object getPostSocialContext(Object item) { return null; }
    private static boolean isRepostContext(Object item) { return false; }
}
