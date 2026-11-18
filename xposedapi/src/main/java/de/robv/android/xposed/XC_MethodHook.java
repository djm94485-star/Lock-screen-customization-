package de.robv.android.xposed;
import de.robv.android.xposed.callbacks.XCallback;
public abstract class XC_MethodHook extends XCallback {
    public XC_MethodHook() { }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }
    public static class MethodHookParam extends XCallback.Param {
        public Object thisObject; public Object[] args;
    }
    public class Unhook { }
}
