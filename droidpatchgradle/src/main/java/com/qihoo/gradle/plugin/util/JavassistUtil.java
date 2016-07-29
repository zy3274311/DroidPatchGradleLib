package com.qihoo.gradle.plugin.util;

import org.gradle.util.TextUtil;

import java.lang.reflect.Array;

import javassist.CtClass;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.annotation.Annotation;

/**
 * Created by zhangying-pd on 2016/7/21.
 */
public class JavassistUtil {

    public static Annotation getAnnotation(CtClass cc, String typeName) throws Exception {
        Annotation[] annotations = getAnnotations(cc);
        if(annotations!=null){
            for(Annotation annotation:annotations){
                String name = annotation.getTypeName();
                if(name!=null&&name.equals(typeName)){
                    System.out.println("JavassistUtil Annotation typeName:"+name);
                    return annotation;
                }
            }
        }
        return null;
    }

    public static Annotation[] getAnnotations(CtClass cc) throws Exception {
        ClassFile cf = cc.getClassFile2();
        AnnotationsAttribute ainfo1 = (AnnotationsAttribute)
                cf.getAttribute(AnnotationsAttribute.invisibleTag);
        AnnotationsAttribute ainfo2 = (AnnotationsAttribute)
                cf.getAttribute(AnnotationsAttribute.visibleTag);

        Annotation[] annotations1 = null;
        if (ainfo1 != null){
            annotations1 = ainfo1.getAnnotations();
        }
        Annotation[] annotations2 = null;
        if (ainfo2 != null){
            annotations2 = ainfo2.getAnnotations();
        }

        return (Annotation[]) combineArray(annotations1, annotations2);
    }


    private static Object combineArray(Object firstArray, Object secondArray) {
        if(firstArray==null){
            return secondArray;
        }
        if(secondArray==null){
            return firstArray;
        }

        Class<?> localClass = firstArray.getClass().getComponentType();
        int firstArrayLength = Array.getLength(firstArray);
        int allLength = firstArrayLength + Array.getLength(secondArray);
        Object result = Array.newInstance(localClass, allLength);
        for (int k = 0; k < allLength; ++k) {
            if (k < firstArrayLength) {
                Array.set(result, k, Array.get(firstArray, k));
            } else {
                Array.set(result, k, Array.get(secondArray, k - firstArrayLength));
            }
        }
        return result;
    }


}
