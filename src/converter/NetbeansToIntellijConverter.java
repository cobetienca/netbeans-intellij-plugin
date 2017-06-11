/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package converter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.OrderEntryNavigatable;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import util.NotificationUtil;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.Set;

/**
 * Created by trangdp on 16/05/2017.
 *
 * This class is to convert netbeans dependencies to intellij module & build ANT run file + Intellij run file
 */
public class NetbeansToIntellijConverter implements ProjectFileConverter {
    private final Logger logger = Logger.getInstance("com.trangdp.NetbeansToIntellijConverter");

    @NotNull
    private Module module;

    @NotNull
    private String projectFileContent;

    public void setModule(Module module) {
        this.module = module;
    }

    public void setProjectFileContent(String projectFileContent) {
        this.projectFileContent = projectFileContent;
    }

    public void convert() {
        NotificationUtil.notify("Converting " + module.getName() + " ...");

        byte[] contentWithLibraryRemoved = clearIntellijModuleLibrary();

        if (contentWithLibraryRemoved != null) {
            Properties properties = new Properties();
            InputStream is = new ByteArrayInputStream(projectFileContent.getBytes());
            String moduleLibraries = "<root>";
            try {
                properties.load(is);
            } catch (IOException e) {
                logger.error("Unable to load netbeans properties. Is it valid?", e);
                return;
            }

            Set<String> propertyNames = properties.stringPropertyNames();
            for (String name : propertyNames) {
                if (name.equals("javac.classpath")) {
                    String classPath = properties.getProperty(name);
                    String[] referenceClasses = classPath.split(":");
                    for (String reference : referenceClasses) {
                        reference = reference.replace("${", "").replace("}", "");
                        String pathToJar = properties.getProperty(reference);
                        if(pathToJar != null ) {
                            if ((pathToJar.startsWith("lib/") || pathToJar.startsWith("libs/"))) {
                                pathToJar = "$MODULE_DIR$/" + pathToJar;
                                NotificationUtil.notify(pathToJar);
                            }

                            moduleLibraries += extractNetbeansLibrary(pathToJar);
                        }



                    }
                }
            }

            moduleLibraries += "</root>";

            resolveIntellijLibrary(moduleLibraries, contentWithLibraryRemoved);

            try {
                storeChecksumIml();
            } catch (IOException | NoSuchAlgorithmException e) {
                logger.error("Checksum failed", e);
            }
        }

        NotificationUtil.notify("Converted " + module.getName() + " DONE");
    }

    private void storeChecksumIml() throws IOException, NoSuchAlgorithmException {
        IntellijModuleImlChecksum.getInstance().updateLastChecksum(module.getName(), module.getModuleFilePath());
    }

    private byte[] clearIntellijModuleLibrary() {
        if (module == null) {
            return null;
        }

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        FileOutputStream os = null;

        try {
            PsiFile moduleImlFile = getModuleImlFile();
            InputStream inputStream = moduleImlFile.getVirtualFile().getInputStream();

            dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputStream);

            inputStream.close();

            doc.getDocumentElement().normalize();
            XPathFactory xpf = XPathFactory.newInstance();
            XPath xpath = xpf.newXPath();
            XPathExpression expression = xpath.compile("//module/component/orderEntry[@type=\"module-library\"] | //module/component/orderEntry[@level=\"project\"]");
            NodeList node = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);

            for (int i = 0; i < node.getLength(); i++) {
                Node item = node.item(i);
                item.getParentNode().removeChild(item);
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            t.transform(new DOMSource(doc), new StreamResult(byteArrayOutputStream));
            t.reset();

            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            logger.error("Error processing DOM .iml file", e);
        }

        return null;
    }

    private String extractNetbeansLibrary(@NotNull String pathToJar) {
        String template = "" +
                "<orderEntry type=\"module-library\">\n" +
                "   <library>\n" +
                "       <CLASSES>\n" +
                "           <root url=\"jar://%s!/\" />\n" +
                "       </CLASSES>\n" +
                "       <JAVADOC />\n" +
                "       <SOURCES />\n" +
                "   </library>\n" +
                "</orderEntry>\n";
        String configuration = String.format(template, pathToJar);

        return configuration;
    }

    private void resolveIntellijLibrary(String input, byte[] contentWithLibraryRemoved) {
        if (module == null) {
            return;
        }

        if (!isModuleImlFileExist()) {
            Messages.showInfoMessage(module.getModuleFilePath(), "Intellij Project file not found.");
            return;
        }

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        FileOutputStream os = null;

        try {

            InputStream inputStream = new ByteArrayInputStream(contentWithLibraryRemoved);
            dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputStream);

            inputStream.close();

            doc.getDocumentElement().normalize();
            XPathFactory xpf = XPathFactory.newInstance();
            XPath xpath = xpf.newXPath();
            XPathExpression expression = xpath.compile("//module/component[@name=\"NewModuleRootManager\"]");
            Node node = (Node) expression.evaluate(doc, XPathConstants.NODE);

            if (node != null) {
                Node dependencyRoot = dBuilder
                        .parse(new ByteArrayInputStream(input.getBytes())).getDocumentElement();
                NodeList childNodes = dependencyRoot.getChildNodes();
                for (int i = 0; i < childNodes.getLength(); ++i) {
                    Node dependency = childNodes.item(i);
                    dependency = doc.importNode(dependency, true);
                    node.appendChild(dependency);
                }
            }

            os = new FileOutputStream(new File(module.getModuleFilePath()), false);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            t.transform(new DOMSource(doc), new StreamResult(byteArrayOutputStream));
            t.reset();

            os.write(byteArrayOutputStream.toByteArray());
            os.flush();

        } catch (Exception e) {
            logger.error("Error resolving libraries", e);
        } finally {
            if (os != null) try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isModuleImlFileExist() {
        String relPath = module.getModuleFilePath().substring(0, module.getModuleFilePath().lastIndexOf(File.separator));
        String path = relPath + File.separator + module.getName() + ".iml";
        VirtualFile moduleImlFile = module.getProject().getBaseDir().getFileSystem().findFileByPath(path);

        return (moduleImlFile != null);
    }

    private PsiFile getModuleImlFile() {
        String relPath = module.getModuleFilePath().substring(0, module.getModuleFilePath().lastIndexOf(File.separator));
        String path = relPath + File.separator + module.getName() + ".iml";
        VirtualFile moduleImlFile = module.getProject().getBaseDir().getFileSystem().findFileByPath(path);

        PsiFile moduleFile = PsiManager.getInstance(module.getProject()).findFile(moduleImlFile);
        return moduleFile;
    }
}
