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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import util.NotificationUtil;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.*;
import java.util.Properties;
import java.util.Set;

/**
 * Created by trangdp on 16/05/2017.
 */
public class NetbeansFileConverter implements ProjectFileConverter {
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
            InputStream is = new ByteArrayInputStream(projectFileContent.getBytes());
            String moduleLibraries = "<root>";
            try {
                Properties properties = new Properties();
                properties.load(is);

                Set<String> propertyNames = properties.stringPropertyNames();
                for (String name : propertyNames) {
                    if (name.startsWith("javac.classpath")) {
                        String classPath = properties.getProperty(name);
                        String[] referenceClasses = classPath.split(":");
                        for (String reference : referenceClasses) {
                            reference = reference.replace("${", "").replace("}", "");
                            String pathToJar = properties.getProperty(reference);
                            if (pathToJar.startsWith("lib/") || pathToJar.startsWith("libs/")) {
                                pathToJar = "$MODULE_DIR$/" + pathToJar;
                                NotificationUtil.notify(pathToJar);
                            }

                            moduleLibraries += extractNetbeansLibrary(pathToJar);
                        }
                    }
                }

                moduleLibraries += "</root>";

                includeLibraryToIntellij(moduleLibraries, contentWithLibraryRemoved);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        NotificationUtil.notify("Converted " + module.getName() + " DONE");
    }

    private byte[] clearIntellijModuleLibrary() {
        if (module == null) {
            return null;
        }

        String relPath = module.getModuleFilePath().substring(0, module.getModuleFilePath().lastIndexOf(File.separator));
        String path = relPath + File.separator + module.getName() + ".iml";
        VirtualFile intellijProjectFile = module.getProject().getBaseDir().getFileSystem().findFileByPath(path);

        PsiFile moduleFile = PsiManager.getInstance(module.getProject()).findFile(intellijProjectFile);

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        FileOutputStream os = null;

        try {

            InputStream inputStream = moduleFile.getVirtualFile().getInputStream();

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

            os = new FileOutputStream(new File(path), false);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            t.transform(new DOMSource(doc), new StreamResult(byteArrayOutputStream));
            t.reset();

            return byteArrayOutputStream.toByteArray();
        } catch (ParserConfigurationException | NullPointerException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
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

    private void includeLibraryToIntellij(String input, byte[] contentWithLibraryRemoved) throws IOException {
        if (module == null) {
            return;
        }

        String relPath = module.getModuleFilePath().substring(0, module.getModuleFilePath().lastIndexOf(File.separator));
        String path = relPath + File.separator + module.getName() + ".iml";
        VirtualFile intellijProjectFile = module.getProject().getBaseDir().getFileSystem().findFileByPath(path);

        if (intellijProjectFile == null) {
            Messages.showInfoMessage(path, "Intellij Project file not found.");
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
            XPathExpression expression = xpath.compile("/module/component");
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

            os = new FileOutputStream(new File(path), false);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            t.transform(new DOMSource(doc), new StreamResult(byteArrayOutputStream));
            t.reset();

            os.write(byteArrayOutputStream.toByteArray());
            os.flush();

        } catch (ParserConfigurationException | NullPointerException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        } finally {
            if (os != null) try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
