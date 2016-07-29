package com.qihoo.gradle.plugin.util;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Created by zhangying-pd on 2016/7/25.
 */
public class ClassLoaderHelper {
    ClassLoader loader;

    private static class ClassLoaderHelperChild {
        private static ClassLoaderHelper helper = new ClassLoaderHelper();
    }

    private ClassLoaderHelper(){}

    public static ClassLoaderHelper getSingleton() {
        return ClassLoaderHelperChild.helper;
    }

    /**
     * 装载jar File
     * @param jarPath jar File path
     */
    public void loadJar(String jarPath) {
        File file = new File(jarPath);
        URL url = null;
        try {
            url = file.toURI().toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        loader =  new URLClassLoader(new URL[]{url});
    }

    public Class loadClass(String className) {
        if(loader!=null){
            try {
                return loader.loadClass(className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
