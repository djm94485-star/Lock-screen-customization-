package com.example.clockresizer;

import android.view.View;
import android.view.ViewParent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class MainHook implements IXposedHookLoadPackage {
    private static final String PKG = "com.android.systemui.shared.clocks";
    private static final Set<View> done = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private static String idName(View v) {
        int id = v.getId();
        if (id == View.NO_ID || (id >>> 24) != 0x7f) return null;
        try { return v.getResources().getResourceEntryName(id); } catch (Throwable t) { return null; }
    }

    static void consider(View v) {
        try {
            if (!v.getClass().getName().startsWith(PKG)) return;
            // climb to the top-most clock-face view
            View top = v;
            ViewParent p = v.getParent();
            while (p instanceof View && p.getClass().getName().startsWith(PKG)) {
                top = (View) p;
                p = p.getParent();
            }
            if (done.contains(top)) return;
            // large / small clock key, from ancestors' ids
            String key = "clock";
            StringBuilder chain = new StringBuilder();
            ViewParent q = top.getParent();
            for (int i = 0; q != null && i < 10; i++) {
                if (q instanceof View) {
                    String n = idName((View) q);
                    chain.append(" > ").append(q.getClass().getSimpleName()).append("#").append(n);
                    if ("lockscreen_clock_view_large".equals(n) || "lockscreen_clock_view".equals(n)) key = n;
                }
                q = q.getParent();
            }
            done.add(top);
            XposedBridge.log("ClockResizer: hooked key=" + key + " view=" + top.getClass().getName() + chain);
            new ClockResizer(top, key).install();
        } catch (Throwable t) { XposedBridge.log(t); }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        if (!"com.android.systemui".equals(lp.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { consider((View) p.thisObject); }
            });
            XposedBridge.log("ClockResizer: v6 hooks ready");
        } catch (Throwable t) { XposedBridge.log(t); }
    }
        }
