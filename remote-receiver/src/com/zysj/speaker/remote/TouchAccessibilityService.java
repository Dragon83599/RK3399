package com.zysj.speaker.remote;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class TouchAccessibilityService extends AccessibilityService {
    private static final String TAG = "TouchAccess";
    private static volatile TouchAccessibilityService instance;

    public static TouchAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isReady() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "accessibility connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (instance == this) {
            instance = null;
        }
        return super.onUnbind(intent);
    }

    public static boolean tap(final int x, final int y) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 60);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return service.dispatchGesture(builder.build(), null, null);
    }

    public static boolean swipe(final int x1, final int y1,
                                final int x2, final int y2, final long duration) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return service.dispatchGesture(builder.build(), null, null);
    }

    public static boolean scroll(final String direction) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        boolean ok = scrollFromFocus(root, direction);
        if (!ok) {
            ok = scrollSubtree(root, direction);
        }
        root.recycle();
        return ok;
    }

    private static boolean scrollFromFocus(AccessibilityNodeInfo root, String direction) {
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        }
        if (focused == null) {
            return false;
        }
        AccessibilityNodeInfo current = focused;
        while (current != null) {
            if (current.isScrollable() && performScroll(current, direction)) {
                if (current != focused) {
                    current.recycle();
                }
                focused.recycle();
                return true;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (current != focused) {
                current.recycle();
            }
            current = parent;
        }
        focused.recycle();
        return false;
    }

    private static boolean scrollSubtree(AccessibilityNodeInfo node, String direction) {
        if (node.isScrollable() && performScroll(node, direction)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean ok = scrollSubtree(child, direction);
                child.recycle();
                if (ok) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean performScroll(AccessibilityNodeInfo node, String direction) {
        if (direction.equals("down")) {
            return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }
        if (direction.equals("up")) {
            return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        return false;
    }

    public static boolean back() {
        TouchAccessibilityService service = instance;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean home() {
        TouchAccessibilityService service = instance;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public static boolean clickFocused() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        }
        if (focused == null) {
            root.recycle();
            return false;
        }
        AccessibilityNodeInfo clickable = clickableAncestor(focused);
        boolean clicked = false;
        if (clickable != null) {
            clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        if (clickable != null && clickable != focused) {
            clickable.recycle();
        }
        focused.recycle();
        root.recycle();
        return clicked;
    }

    public static boolean clickNavButton(String target) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) {
            return false;
        }
        for (AccessibilityWindowInfo window : windows) {
            CharSequence title = window.getTitle();
            String titleStr = title == null ? "" : title.toString();
            boolean navWindow = titleStr.contains("NavigationBar")
                    || titleStr.contains("navigation")
                    || window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM;
            if (!navWindow) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) {
                continue;
            }
            AccessibilityNodeInfo button = findNavButton(root, target);
            if (button != null) {
                boolean clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (button != root) {
                    button.recycle();
                }
                root.recycle();
                return clicked;
            }
            root.recycle();
        }
        return false;
    }

    public static boolean clickText(String text) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) {
            return false;
        }
        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) {
                continue;
            }
            AccessibilityNodeInfo button = findTextNode(root, text);
            if (button != null) {
                boolean clicked = false;
                try {
                    clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } catch (Exception ignored) {
                }
                if (button != root) {
                    button.recycle();
                }
                root.recycle();
                return clicked;
            }
            root.recycle();
        }
        return false;
    }

    public static int[] findTextCenter(String text) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return null;
        }
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) {
            return null;
        }
        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) {
                continue;
            }
            AccessibilityNodeInfo button = findVisibleTextNode(root, text);
            if (button != null) {
                Rect rect = new Rect();
                button.getBoundsInScreen(rect);
                int[] center = new int[]{rect.centerX(), rect.centerY()};
                if (button != root) {
                    button.recycle();
                }
                root.recycle();
                return center;
            }
            root.recycle();
        }
        return null;
    }

    private static AccessibilityNodeInfo findVisibleTextNode(AccessibilityNodeInfo node,
                                                             String text) {
        if (node == null) {
            return null;
        }
        String nodeText = textOf(node.getText()) + " "
                + textOf(node.getContentDescription());
        if (node.isVisibleToUser() && nodeText.contains(text)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findVisibleTextNode(child, text);
                if (found != null) {
                    return found;
                }
                child.recycle();
            }
        }
        return null;
    }

    public static boolean hasText(String text) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) {
            return false;
        }
        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) {
                continue;
            }
            AccessibilityNodeInfo found = findTextNodeAny(root, text);
            if (found != null) {
                root.recycle();
                return true;
            }
            root.recycle();
        }
        return false;
    }

    public static boolean navBarAccessible() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) {
            return false;
        }
        for (AccessibilityWindowInfo window : windows) {
            CharSequence title = window.getTitle();
            String titleStr = title == null ? "" : title.toString();
            if (titleStr.contains("导航栏") || titleStr.contains("NavigationBar")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDreamActive() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) {
            return false;
        }
        for (AccessibilityWindowInfo window : windows) {
            CharSequence title = window.getTitle();
            String titleStr = title == null ? "" : title.toString().toLowerCase();
            if (titleStr.contains("设置")) {
                return false;
            }
        }
        for (AccessibilityWindowInfo window : windows) {
            CharSequence title = window.getTitle();
            String titleStr = title == null ? "" : title.toString().toLowerCase();
            if (titleStr.contains("设置")) {
                continue;
            }
            if (titleStr.contains("dream") || titleStr.contains("standby")
                    || titleStr.contains("宋画") || titleStr.contains("屏保")) {
                return true;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                if (pkg != null && pkg.toString().contains("standby")) {
                    root.recycle();
                    return true;
                }
                root.recycle();
            }
        }
        return false;
    }

    public static boolean isRecentsActive() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) {
            return false;
        }
        for (AccessibilityWindowInfo window : windows) {
            CharSequence title = window.getTitle();
            String titleStr = title == null ? "" : title.toString().toLowerCase();
            if (titleStr.contains("quickstep") || titleStr.contains("recents")
                    || titleStr.contains("最近任务") || titleStr.contains("概览")) {
                return true;
            }
        }
        return false;
    }

    public static JSONArray navDump() {
        JSONArray windows = new JSONArray();
        TouchAccessibilityService service = instance;
        if (service == null) {
            return windows;
        }
        List<AccessibilityWindowInfo> list = service.getWindows();
        if (list == null) {
            return windows;
        }
        for (AccessibilityWindowInfo window : list) {
            CharSequence title = window.getTitle();
            JSONObject entry = new JSONObject();
            try {
                entry.put("type", window.getType());
                entry.put("title", title == null ? "" : title.toString());
                JSONArray nodes = new JSONArray();
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) {
                    collectTexts(root, nodes);
                    root.recycle();
                }
                entry.put("nodes", nodes);
                windows.put(entry);
            } catch (Exception ignored) {
            }
        }
        return windows;
    }

    private static AccessibilityNodeInfo findNavButton(AccessibilityNodeInfo node,
                                                       String target) {
        if (node == null) {
            return null;
        }
        if (matchesNavTarget(node, target)) {
            AccessibilityNodeInfo clickable = clickableSelfOrAncestor(node);
            if (clickable != null) {
                return clickable;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findNavButton(child, target);
                if (found != null) {
                    return found;
                }
                child.recycle();
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findTextNode(AccessibilityNodeInfo node,
                                                      String text) {
        if (node == null) {
            return null;
        }
        String nodeText = textOf(node.getText()) + " "
                + textOf(node.getContentDescription());
        if (node.isVisibleToUser() && nodeText.contains(text)) {
            AccessibilityNodeInfo clickable = clickableSelfOrAncestor(node);
            if (clickable != null) {
                return clickable;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findTextNode(child, text);
                if (found != null) {
                    return found;
                }
                child.recycle();
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findTextNodeAny(AccessibilityNodeInfo node,
                                                         String text) {
        if (node == null) {
            return null;
        }
        String nodeText = textOf(node.getText()) + " "
                + textOf(node.getContentDescription());
        if (nodeText.contains(text)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findTextNodeAny(child, text);
                if (found != null) {
                    return found;
                }
                child.recycle();
            }
        }
        return null;
    }

    private static boolean matchesNavTarget(AccessibilityNodeInfo node, String target) {
        String text = textOf(node.getText());
        String desc = textOf(node.getContentDescription());
        String all = (text + " " + desc).toLowerCase();
        if (target.equals("power")) {
            return all.contains("关机") || all.contains("重启")
                    || all.contains("电源") || all.contains("power");
        }
        if (target.equals("recents")) {
            return all.contains("后台") || all.contains("最近")
                    || all.contains("任务") || all.contains("recents")
                    || all.contains("overview") || all.contains("概览");
        }
        if (target.equals("home")) {
            return all.contains("主界面") || all.contains("主页")
                    || all.contains("首页") || all.contains("主屏幕")
                    || all.contains("home");
        }
        if (target.equals("back")) {
            return all.contains("回退") || all.contains("返回")
                    || all.contains("back");
        }
        return false;
    }

    private static AccessibilityNodeInfo clickableSelfOrAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (parent == null || parent == current) {
                break;
            }
            if (current != node) {
                current.recycle();
            }
            current = parent;
        }
        return null;
    }

    private static void collectTexts(AccessibilityNodeInfo node, JSONArray out)
            throws Exception {
        String text = textOf(node.getText());
        String desc = textOf(node.getContentDescription());
        JSONObject item = new JSONObject();
        item.put("text", text);
        item.put("desc", desc);
        item.put("clickable", node.isClickable());
        item.put("visible", node.isVisibleToUser());
        item.put("focused", node.isFocused());
        item.put("selected", node.isSelected());
        item.put("scrollable", node.isScrollable());
        item.put("id", node.getViewIdResourceName() == null
                ? "" : node.getViewIdResourceName());
        item.put("class", node.getClassName() == null
                ? "" : node.getClassName().toString());
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        item.put("bounds", rect.flattenToString());
        out.put(item);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectTexts(child, out);
                child.recycle();
            }
        }
    }

    private static String textOf(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private static AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (parent == null || parent == current) {
                break;
            }
            if (current != node) {
                current.recycle();
            }
            current = parent;
        }
        return null;
    }
}
