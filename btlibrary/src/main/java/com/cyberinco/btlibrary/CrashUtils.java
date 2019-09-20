package com.cyberinco.btlibrary;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class CrashUtils {

    /**
     * 获取错误的信息
     *
     * @param throwable
     * @return
     */
    public static String getStackTraceInfo(final Throwable throwable) {
        PrintWriter pw = null;
        Writer writer = new StringWriter();
        try {
            pw = new PrintWriter(writer);
            throwable.printStackTrace(pw);
        } catch (Exception e) {
            return "";
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
        return writer.toString();
    }
}
