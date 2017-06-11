package converter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import util.NotificationUtil;

import java.io.*;
import java.util.*;

/**
 * Created by trangdp on 17/05/2017.
 * This class is to convert intellij dependecies to netbeans
 */
public class IntellijToNetbeansConverter implements ProjectFileConverter {
    private final Logger logger = Logger.getInstance("com.trangdp.IntellijToNetbeansConverter");

    @NotNull
    private Module module;

    @NotNull
    private String projectFileContent;

    @Override
    public void setModule(@NotNull Module module) {
        this.module = module;
    }

    @Override
    public void setProjectFileContent(@NotNull String projectFileContent) {
        this.projectFileContent = projectFileContent;
    }

    @Override
    public void convert() {
        NotificationUtil.notify("Intellij to Netbeans converting...");

        //TODO:clear netbeans classpath, read orderEntry module-library intellij, re-build netbeans classpath
        Properties properties = clearNetbeansClasspath();

        if(properties != null) {
            List<String> references = extractIntellijReferences();
            Properties transformedNetbeansClasspath = buildNetbeansClasspath(properties, references);

            try {
                flushNetbeansClasspath(transformedNetbeansClasspath);
            } catch (IOException e) {
                logger.error("Unable to override netbeans project properties");
            }
        }

        NotificationUtil.notify("Intellij to Netbeans converted DONE");
    }

    private void flushNetbeansClasspath(Properties transformedNetbeansClasspath) throws IOException {
        Project project = module.getProject();
        String relPath = module.getModuleFilePath().substring(0, module.getModuleFilePath().lastIndexOf(File.separator));
        VirtualFile moduleRoot = project.getBaseDir().getFileSystem().findFileByPath(relPath);
        if (moduleRoot.isDirectory()) {
            String netbeansPropertiesPath = String.format("%s/nbproject/project.properties", moduleRoot.getCanonicalPath());

            PrintWriter writer = new PrintWriter(netbeansPropertiesPath);
            try {
                writer.print("");
                Enumeration propertiesKeys = sortPropertiesKeys(transformedNetbeansClasspath);
                while(propertiesKeys.hasMoreElements()) {
                    Object propertiesKey = propertiesKeys.nextElement();
                    writer.println(String.format("%s=%s", propertiesKey, transformedNetbeansClasspath.get(propertiesKey)));
                }

//                for (String name: transformedNetbeansClasspath.stringPropertyNames()) {
//                    writer.println(String.format("%s=%s", name, transformedNetbeansClasspath.get(name)));
//                }
                writer.flush();
            } finally {
                if(writer != null) writer.close();
            }
        }
    }

    private Properties buildNetbeansClasspath(Properties properties, List<String> references) {
        String javacClassPath = "\\\r\n";
        for (int i = 0; i < references.size(); ++i) {
            String reference = references.get(i);
            String[] split = reference.split("/");
            String libraryName = split[split.length - 1];

            properties.put("file.reference." + libraryName, reference);
            if(i < references.size() - 1) {
                javacClassPath += String.format("\t${file.reference.%s}:\\\r\n", libraryName);
            } else {
                javacClassPath += String.format("\t${file.reference.%s}", libraryName);
            }
        }

        properties.put("javac.classpath", javacClassPath);

        return properties;
    }

    private Enumeration sortPropertiesKeys(Properties properties) {
        Enumeration<?> keysEnum = properties.propertyNames();

        Vector keyList = new Vector();
        while(keysEnum.hasMoreElements()){
            keyList.add(keysEnum.nextElement());
        }
        Collections.sort(keyList);

        return keyList.elements();
    }

    private List<String> extractIntellijReferences() {
        List<String> references = new ArrayList<String>();
        String moduleName = module.getName();

        OrderEnumerator.orderEntries(module).forEach(new Processor<OrderEntry>() {
            @Override
            public boolean process(OrderEntry orderEntry) {
                if(orderEntry instanceof ModuleLibraryOrderEntryImpl) {
                    System.out.println(orderEntry.getPresentableName());
                    String libraryReference = orderEntry.getPresentableName();

                    if(libraryReference.contains(moduleName)) {
                        String[] split = libraryReference.split(moduleName + "/");
                        references.add(split[1]);
                    } else {
                        references.add(libraryReference);
                    }
                }

                return true;
            }
        });

        return references;
    }

    private Properties clearNetbeansClasspath() {
        Project project = module.getProject();
        String relPath = module.getModuleFilePath().substring(0, module.getModuleFilePath().lastIndexOf(File.separator));
        VirtualFile moduleRoot = project.getBaseDir().getFileSystem().findFileByPath(relPath);
        if (moduleRoot.isDirectory()) {
            String netbeansPropertiesPath = String.format("%s/nbproject/project.properties", moduleRoot.getCanonicalPath());

            VirtualFile netbeansPropertiesVfLocal = LocalFileSystem.getInstance().findFileByPath(netbeansPropertiesPath);

            if (netbeansPropertiesVfLocal!= null) {
                try {
                    Properties properties = new Properties();
                    InputStream is = new ByteArrayInputStream(netbeansPropertiesVfLocal.contentsToByteArray());
                    properties.load(is);

                    Set<String> propertyNames = properties.stringPropertyNames();
                    for(String name: propertyNames) {
                        if(name.startsWith("file.reference") || name.equals("javac.classpath")) {
                            properties.remove(name);
                        }
                    }

                    return properties;
                } catch (IOException e) {
                    logger.error("Unable to read Netbeans property file. Stop process!");
                }
            } else {
                logger.error("Netbeans property file not found. Stop process!");
            }
        }
        return null;
    }
}

