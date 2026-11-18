package com.example.clockresizer;

import android.view.View;
import android.view.ViewParent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

public class MainHook implements IXposedHookLoadPackage {
    private static final String[] IDS = {"lockscreen_clock_view_large", "lockscreen_clock_view"};
    private static final String[] CLASSES = {"FlexClockView", "KeyguardClockSwitch", "AnimatableClockView",
            "DigitalClockFaceView", "DefaultClockView", "ClockFaceView"};
    private static final Set<View> done = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<String> logged = new HashSet<>();

    private static String idName(View v) {
        int id = v.getId();
        if (id == View.NO_ID || (id >>> 24) != 0x7f) return null;
        try { return v.getResources().getResourceEntryName(id); } catch (Throwable t) { return null; }
    }

    private static String matchedClass(View v) {
        for (Class<?> c = v.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            String n = c.getName();
            for (String s : CLASSES) if (n.endsWith("." + s)) return s;
        }
        return null;
    }

    private static boolean ancestorDone(View v) {
        ViewParent p = v.getParent();
        while (p != null) {
            if (p instanceof View && done.contains(p)) return true;
            p = p.getParent();
        }
        return false;
    }

    static void consider(View v) {
        try {
            String cls = v.getClass().getName();
            if (cls.contains("statusbar")) return;
            String name = idName(v);
            boolean hit = false;
            String key = null;
            if (name != null) {
                for (String w : IDS) if (w.equals(name)) { hit = true; key = name; }
            }
            if (!hit) {
                String m = matchedClass(v);
                if (m != null) { hit = true; key = "cls_" + m; }
            }
            String look = (name != null ? name : cls).toLowerCase();
            if (look.contains("clock") && logged.add(cls + "|" + name)) {
                XposedBridge.log("ClockResizer seen: id=" + name + " class=" + cls + " parent="
                        + (v.getParent() == null ? "null" : v.getParent().getClass().getName()));
            }
            if (!hit || ancestorDone(v) || !done.add(v)) return;
            XposedBridge.log("ClockResizer: hooked " + key + " (" + cls + ")");
            new ClockResizer(v, key).install();
        } catch (Throwable ignored) { }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        if (!"com.android.systemui".equals(lp.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { consider((View) p.thisObject); }
            });
            XposedHelpers.findAndHookMethod(View.class, "setId", int.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { consider((View) p.thisObject); }
            });
            XposedBridge.log("ClockResizer: v5 hooks ready");
        } catch (Throwable t) { XposedBridge.log(t); }
    }
                                            }
