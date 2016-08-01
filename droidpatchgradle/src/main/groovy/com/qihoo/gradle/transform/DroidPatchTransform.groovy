package com.qihoo.gradle.transform

import com.android.build.api.transform.*
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.dx.command.dexer.DxContext
import com.android.dx.command.dexer.Main
import com.android.utils.FileUtils
import com.google.common.io.Files
import com.qihoo.gradle.extension.ConfigPatchExtension
import com.qihoo.gradle.plugin.util.JavassistUtil
import com.qihoo.gradle.plugin.util.MD5Util
import javassist.*
import javassist.bytecode.annotation.Annotation
import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer

import java.util.jar.*

public class DroidPatchTransform extends Transform {
    String patchDir
    String buildDir
    Project project

    DroidPatchTransform(Project project) {
        this.project = project
        patchDir = project.rootDir.absolutePath + File.separator + "patch"
        buildDir = project.buildDir.absolutePath + File.separator + "patch"
        project.extensions.create("patchConfigs", ConfigPatchExtension);
    }

    @Override
    String getName() {
        return 'DroidPatchTransform'
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /**
     * Returns
     * @return whether the Transform can perform incremental work.
     */
    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)
        TaskContainer taskContainer = project.tasks
        taskContainer.each { task ->
            if (task.name.contains("Dex")) {
                task.doFirst() {
                    task.inputs.files.each { file ->
                        System.out.println("DroidPatchTransform Dex task inputs: " + file.absolutePath)
                        findAndInjectJarFile(file)
                    }
                }
            }
        }

        System.out.println("\n\t")
        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        Collection<TransformInput> inputs = transformInvocation.inputs
        inputs.each { TransformInput input ->

            input.directoryInputs.each { DirectoryInput directoryInput ->
                System.out.println("DroidPatchTransform transform directoryInput " + directoryInput.file.absolutePath)
                System.out.println("\n\t")
                File dest = outputProvider.getContentLocation(directoryInput.file.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
                System.out.println("DroidPatchTransform transform outputProvider dest " + dest)
                FileUtils.copy(directoryInput.file, dest.getParentFile())
            }
            input.jarInputs.each { JarInput jarInput ->
                String jarName = jarInput.file.absolutePath
                System.out.println("DroidPatchTransform transform jarInput " + jarName)
                String fileName = DigestUtils.md5Hex(jarName) + jarInput.file.name
                if(fileName.endsWith(".jar")){
                    fileName = fileName.substring(0, fileName.length()-4)
                }
                File dest = outputProvider.getContentLocation(fileName, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                File destDir = dest.parentFile
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }

                System.out.println("\n\t")
                System.out.println("DroidPatchTransform transform jarInput dest " + dest)
                Files.copy(jarInput.file, dest)
            }
        }
        System.out.println("\n\t")
    }

    def findAndInjectJarFile(File file) {
        if (file.isFile() && file.name.endsWith(".jar")) {
            System.out.println("DroidPatchTransform findAndInjectJarFile " + file.absolutePath)
            try {
                injectJar(file.absolutePath)
            } catch (Exception e) {
                e.printStackTrace()
            }
        } else if (file.isDirectory()) {
            File[] childs = file.listFiles()
            if (childs != null) {
                for (File child : childs) {
                    findAndInjectJarFile(child)
                }
            }
        }

    }

    def injectJar(String jarPath) throws Exception {
        File jarFile = new File(jarPath)
        File tempDir = new File(buildDir, "temp")
        String classesDir = tempDir.absolutePath
        Map<String, String> list = unZipJar(jarPath, classesDir)
        injectDir(classesDir, list)
        String name = jarFile.name
        jarFile.delete()
        File reZipJarFile = new File(jarFile.parent, name)
        generateJar(reZipJarFile, classesDir, list)
        if(name.endsWith(".jar")){
            name = name.substring(0, name.length()-4)
        }

        def packPatchDexEnable = project.patchConfigs.packPatchDexEnable
        System.out.println("DroidPatchTransform packPatchDexEnable " + packPatchDexEnable)
        //TODO many jars  so must multy properties
        //TODO instant-run
        //TODO debug no jar but classes
        def android = project.extensions.getByType(AppExtension)
        def versionName = android.defaultConfig.versionName

        String dir = patchDir + File.separator + versionName;
        File dirFile = new File(dir)
        System.out.println("DroidPatchTransform dirFile " + dir)
        if(!dirFile.exists()){
            boolean mkDirsOK = dirFile.mkdirs()
            System.out.println("DroidPatchTransform mkDirsOK " + mkDirsOK)
        }

        File signFile = new File(dir, name+"-last-sign.properties")
        if(packPatchDexEnable){
            File patchFile = new File(dir, name+"-patch.dex")
            generatePatchDex(signFile,patchFile, classesDir, list)
        }else {
            createDirProperties(signFile, classesDir, list)
        }
    }

    def injectDir(String dir, Map<String, String> list) {
        ClassPool pool = ClassPool.getDefault()
        //TODO SDK path
        def android = project.extensions.getByType(AppExtension)
        String sdkDir = android.sdkDirectory.absolutePath + File.separator + "platforms" + File.separator + android.compileSdkVersion + File.separator + "android.jar"
        System.out.println("DroidPatchTransform sdkDir: " + sdkDir)
        File sdkFile = new File(sdkDir)

        if (!sdkFile.exists()) {
            throw new RuntimeException("can not find android SDK")
        }
        pool.appendClassPath(sdkDir)
        ClassPath classPath = pool.appendClassPath(dir)
        pool.makeClass("com.qihoo.patch.Cat")

//        ClassLoaderHelper helper = ClassLoaderHelper.getSingleton()
//        helper.loadJar(dir)

        list.entrySet().each { entry ->
            String className = entry.value
            injectClass(pool, className, dir)
        }
        pool.removeClassPath(classPath)
    }

    static def injectClass(ClassPool pool, String className, String dir) {
        CtClass cc = pool.getCtClass(className);
        if (cc.isFrozen()) {
            cc.defrost()
        }

        Annotation annotation = JavassistUtil.getAnnotation(cc, "com.qihoo.library.annotation.UnInject")
        if (annotation == null) {
            try {
                CtConstructor[] cts = cc.getDeclaredConstructors()
                //TODO Code Attribute mybe null
                //inject code
                if (cts == null || cts.length == 0) {
                    CtConstructor constructor = CtNewConstructor.defaultConstructor(cc)
                    constructor.insertBeforeBody("System.out.println(com.qihoo.patch.Cat.class);")
                    cc.addConstructor(constructor)
                } else {
                    cts[0].insertBeforeBody("System.out.println(com.qihoo.patch.Cat.class);")
                }
                System.out.println("inject success " + className);
            } catch (Exception e) {
                System.out.println("inject fail " + e.message.toString());
            }
            cc.writeFile(dir)
            cc.detach()
        } else {
            System.out.println("DroidPatchTransform do not injectClass " + className)
        }
    }

    /**
     * 将该jar包解压到指定目录
     * @param jarPath jar包的绝对路径
     * @param destDirPath jar包解压后的保存路径
     * @return 返回该jar包中包含的所有class的完整类名类名集合，其中一条数据如：com.aitski.hotpatch.Xxxx.class
     */
    static def Map<String, String> unZipJar(String jarPath, String destDirPath) {
        Map<String, String> list = new HashMap<>()
        if (jarPath.endsWith('.jar')) {
            JarFile jarFile = new JarFile(jarPath)
            Enumeration<JarEntry> jarEntrys = jarFile.entries()
            while (jarEntrys.hasMoreElements()) {
                JarEntry jarEntry = jarEntrys.nextElement()
                if (jarEntry.directory) {
                    continue
                }
                String entryName = jarEntry.getName()
                if (entryName.endsWith('.class')) {
                    String className = entryName.replace('\\', '.').replace('/', '.').replace(".class", "")
                    list.put(entryName, className)
                }
                String outFileName = destDirPath + "/" + entryName
                File outFile = new File(outFileName)
                outFile.getParentFile().mkdirs()
                InputStream inputStream = jarFile.getInputStream(jarEntry)
                FileOutputStream fileOutputStream = new FileOutputStream(outFile)
                fileOutputStream << inputStream
                fileOutputStream.close()
                inputStream.close()
            }
            jarFile.close()
        }
        return list
    }

    static def generateJar(File jarFile, String classesDir, Map<String, String> list) {
        //create required jar name
        JarOutputStream jos = null;
        try {
            OutputStream os = new FileOutputStream(jarFile);
            String version = "1.0.0";
            String author = "zhangying";
            Manifest manifest = new Manifest();
            Attributes global = manifest.getMainAttributes();
            global.put(Attributes.Name.MANIFEST_VERSION, version);
            global.put(new Attributes.Name("Created-By"), author);
            jos = new JarOutputStream(os, manifest);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //start writing in jar
        int len = 0;
        byte[] buffer = new byte[1024];

        list.entrySet().each { entry ->
            String entryName = entry.key
            //write the bytes of file into jar
            String outFileName = classesDir + "/" + entryName
            File outFile = new File(outFileName)
            if (outFile.exists()) {
                JarEntry je = new JarEntry(entryName);
                je.setComment("Create Jar");
                je.setTime(Calendar.getInstance().getTimeInMillis());
                jos.putNextEntry(je);
                InputStream is = new BufferedInputStream(new FileInputStream(outFileName));
                while ((len = is.read(buffer, 0, buffer.length)) != -1) {
                    jos.write(buffer, 0, len);
                }
                is.close();
                jos.closeEntry();
            }
        }

        jos.close();
    }

    static def generatePatchDex(File signFile, File dexFile, String classesDir, Map<String, String> list) {
        InputStream is = new FileInputStream(signFile)

        Properties properties = new Properties()
        properties.load(is)

        list.entrySet().each { entry ->
            String entryName = entry.key
            //write the bytes of file into jar
            String classFileName = classesDir + "/" + entryName
            File classFile = new File(classFileName)
            String md5 = MD5Util.getMD5codeFromFile(new File(classFileName))
            String lastMd5 = properties.getProperty(entryName)
            if (md5.equals(lastMd5)) {
                classFile.delete()
            } else {
                System.out.println("DroidPatchTransform generatePatchDex file:" + classFileName)
            }
        }

        //TODO jar TO dex
        String[] argArray = new String[2];
        argArray[0] = "--output="+dexFile.absolutePath;
        argArray[1] = classesDir;

        DxContext context = new DxContext();
        Main.Arguments arguments = new Main.Arguments();
        arguments.parse(argArray, context);
        new Main(context).run(arguments);

    }

    static def createDirProperties(File signFile, String classesDir, Map<String, String> list) {
        Properties properties = new Properties()

        list.entrySet().each { entry ->
            String entryName = entry.key
            //write the bytes of file into jar
            String outFileName = classesDir + "/" + entryName
            String md5 = MD5Util.getMD5codeFromFile(new File(outFileName))
            properties.put(entryName, md5)
        }

        if (signFile.exists()) {
            signFile.delete()
        }

        signFile.createNewFile()

        OutputStream os = new FileOutputStream(signFile)
        String comment = "droidpatch files signature"
        properties.store(os, comment)
    }
}
