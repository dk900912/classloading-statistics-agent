package io.github.dk900912.classloading.statistics.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author dukui
 */
public class ClassLoadingStatisticsAgent {

    public static final Logger logger = LoggerFactory.getLogger(ClassLoadingStatisticsAgent.class);

    public static void agentmain(String args, Instrumentation inst) {
        logger.info("The agent was attached successfully.");

        Class<?>[] allLoadedClasses = inst.getAllLoadedClasses();
        processClassLoaderStats(allLoadedClasses);
    }

    private static void processClassLoaderStats(Class<?>[] allLoadedClasses) {
        List<ClassLoaderFamilyInfo> classLoaderFamilyInfos = getAllClassLoaderInfo(allLoadedClasses);
        Map<String, ClassLoaderFamilyStat> classLoaderStats = new HashMap<>();
        for (ClassLoaderFamilyInfo classLoaderFamilyInfo: classLoaderFamilyInfos) {
            String name = classLoaderFamilyInfo.getName();
            ClassLoaderFamilyStat stat = classLoaderStats.get(name);
            if (null == stat) {
                stat = new ClassLoaderFamilyStat();
                classLoaderStats.put(name, stat);
            }
            stat.addLoadedCount(classLoaderFamilyInfo.loadedClassCount);
            stat.addInstanceCount(1);
            stat.addLoadedPkg(classLoaderFamilyInfo.getLoadedPkg());
        }

        System.out.println();
        System.out.println("----------------------------------------------------------------------------------------------");
        System.out.printf("%-60s %-20s %-20s", "classloaderName", "instanceCount", "loadedCount");
        System.out.println();
        for(Map.Entry<String, ClassLoaderFamilyStat> entry : classLoaderStats.entrySet()){
            System.out.format("%-60s %-20d %-20d", entry.getKey(), entry.getValue().getInstanceCount(), entry.getValue().loadedCount);
            System.out.println();
        }

        System.out.println("----------------------------------------------------------------------------------------------");
        for (Map.Entry<String, ClassLoaderFamilyStat> entry : classLoaderStats.entrySet()){
            System.out.println();
            String key = entry.getKey();
            Set<String> value = entry.getValue().getLoadedPkg();
            System.out.format(">>>>>>>>>>>>>>>>>>>> package list loaded by %-60s", key);
            System.out.println();
            for (String pkgName : value) {
                System.out.println(pkgName);
            }
        }
    }

    private static List<ClassLoaderFamilyInfo> getAllClassLoaderInfo(Class<?>[] allLoadedClasses, Filter... filters) {
        ClassLoaderFamilyInfo bootstrapFamilyInfo = new ClassLoaderFamilyInfo(null);
        Map<ClassLoader, ClassLoaderFamilyInfo> loaderFamilyInfos = new HashMap<>();
        for (Class<?> clazz : allLoadedClasses) {
            ClassLoader classLoader = clazz.getClassLoader();
            if (classLoader == null) {
                bootstrapFamilyInfo.increase();
                bootstrapFamilyInfo.registerPkg(clazz);
            } else {
                if (shouldInclude(classLoader, filters)) {
                    ClassLoaderFamilyInfo loaderFamilyInfo = loaderFamilyInfos.get(classLoader);
                    if (loaderFamilyInfo == null) {
                        loaderFamilyInfo = new ClassLoaderFamilyInfo(classLoader);
                        loaderFamilyInfos.put(classLoader, loaderFamilyInfo);
                        ClassLoader parent = classLoader.getParent();
                        while (parent != null) {
                            ClassLoaderFamilyInfo parentLoaderInfo = loaderFamilyInfos.get(parent);
                            if (parentLoaderInfo == null) {
                                parentLoaderInfo = new ClassLoaderFamilyInfo(parent);
                                loaderFamilyInfos.put(parent, parentLoaderInfo);
                            }
                            parent = parent.getParent();
                        }
                    }
                    loaderFamilyInfo.increase();
                    loaderFamilyInfo.registerPkg(clazz);
                }
            }
        }

        List<ClassLoaderFamilyInfo> result = new ArrayList<>();
        result.add(bootstrapFamilyInfo);
        result.addAll(loaderFamilyInfos.values());
        return result;
    }

    private static boolean shouldInclude(ClassLoader classLoader, Filter... filters) {
        if (filters == null) {
            return true;
        }

        for (Filter filter : filters) {
            if (!filter.accept(classLoader)) {
                return false;
            }
        }
        return true;
    }

    private interface Filter {
        boolean accept(ClassLoader classLoader);
    }

    private static class SunReflectionClassLoaderFilter implements Filter {
        private static final List<String> REFLECTION_CLASSLOADERS =
                Arrays.asList("sun.reflect.DelegatingClassLoader", "jdk.internal.reflect.DelegatingClassLoader");

        @Override
        public boolean accept(ClassLoader classLoader) {
            return !REFLECTION_CLASSLOADERS.contains(classLoader.getClass().getName());
        }
    }

    private static class ClassLoaderFamilyInfo {
        private final ClassLoader classLoader;
        private final Set<String> loadedPkg;
        private int loadedClassCount = 0;

        ClassLoaderFamilyInfo(ClassLoader classLoader) {
            this.classLoader = classLoader;
            this.loadedPkg = new HashSet<>(200);
        }

        public String getName() {
            if (classLoader != null) {
                return classLoader.getClass().getName();
            }
            return "BootstrapClassLoader";
        }

        void increase() {
            loadedClassCount++;
        }

        int loadedClassCount() {
            return loadedClassCount;
        }

        void registerPkg(Class<?> clazz) {
            String pkgName = String.join(".", Arrays.copyOf(clazz.getName().split("\\."), 2));
            if (!pkgName.startsWith("[")) {
                loadedPkg.add(pkgName);
            }
        }

        public Set<String> getLoadedPkg() {
            return loadedPkg;
        }

    }

    private static class ClassLoaderFamilyStat {
        private int loadedCount;
        private int instanceCount;
        private final Set<String> loadedPkg;

        public ClassLoaderFamilyStat() {
            this.loadedPkg = new HashSet<>();
        }

        void addLoadedCount(int count) {
            this.loadedCount += count;
        }

        void addInstanceCount(int count) {
            this.instanceCount += count;
        }

        void addLoadedPkg(Set<String> loadedPkg) {
            this.loadedPkg.addAll(loadedPkg);
        }

        public int getLoadedCount() {
            return loadedCount;
        }

        public int getInstanceCount() {
            return instanceCount;
        }

        public Set<String> getLoadedPkg() {
            return loadedPkg;
        }
    }

}
