package com.example.clockresizer;

import android.view.View;
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
    // Class-name independent: we look at every View that attaches inside SystemUI and match by its id name.
    private static final String[] IDS = {"lockscreen_clock_view_large", "lockscreen_clock_view"};
    private static final Set<View> done = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<String> logged = new HashSet<>();

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        if (!"com.android.systemui".equals(lp.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        View v = (View) p.thisObject;
                        int id = v.getId();
                        if (id == View.NO_ID || (id >>> 24) != 0x7f) return;
                        String name = v.getResources().getResourceEntryName(id);
                        if (name == null) return;
                        // discovery log: every clock-ish view once, so we can adapt to this ROM
                        if (name.toLowerCase().contains("clock") && logged.add(name)) {
                            XposedBridge.log("ClockResizer seen: id=" + name + " class=" + v.getClass().getName()
                                    + " parent=" + (v.getParent() == null ? "null" : v.getParent().getClass().getName()));
                        }
                        for (String want : IDS) {
                            if (want.equals(name) && done.add(v)) {
                                XposedBridge.log("ClockResizer: hooked " + name + " (" + v.getClass().getName() + ")");
                                new ClockResizer(v, name).install();
                            }
                        }
                    } catch (Throwable ignored) { }
                }
            });
            XposedBridge.log("ClockResizer: View.onAttachedToWindow hook ready");
        } catch (Throwable t) { XposedBridge.log(t); }
    }
}
