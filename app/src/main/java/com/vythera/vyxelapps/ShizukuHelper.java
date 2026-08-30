package com.vythera.vyxelapps;

import rikka.shizuku.Shizuku;
import java.lang.reflect.Method;

/** Calls Shizuku.newProcess via reflection because the method has private Java access in the AAR. */
public class ShizukuHelper {
    public static Process newProcess(String[] cmd) throws Exception {
        Method m = Shizuku.class.getDeclaredMethod(
            "newProcess", String[].class, String[].class, String.class
        );
        m.setAccessible(true);
        return (Process) m.invoke(null, new Object[]{ cmd, null, null });
    }
}
