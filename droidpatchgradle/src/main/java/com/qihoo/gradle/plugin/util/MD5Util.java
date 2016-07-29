package com.qihoo.gradle.plugin.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Created by zhangying-pd on 2016/7/26.
 */
public class MD5Util {

    public static String getMD5codeFromFile(File file) throws FileNotFoundException {
        InputStream inputStream = new FileInputStream(file);
        byte[] data = StreamTool.readStream(inputStream);
        return getMD5code(data);
    }

    public static String getMD5code(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            byte b[] = md.digest();
            StringBuilder buf = new StringBuilder("");
            for (byte aB : b) {
                int i = aB;
                if (i < 0)
                    i += 256;
                if (i < 16)
                    buf.append("0");
                buf.append(Integer.toHexString(i));
            }
            return buf.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
