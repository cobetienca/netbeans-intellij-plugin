import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.packaging.impl.artifacts.ArtifactManagerImpl;
import com.intellij.packaging.impl.artifacts.ArtifactVirtualFileListener;
import com.intellij.util.xml.Convert;
import converter.ConverterFactory;
import converter.IntellijModuleImlChecksum;
import converter.ProjectFileConverter;
import org.jetbrains.annotations.NotNull;
import util.NotificationUtil;

import java.io.File;
import java.io.IOException;

/**
 * Created by trangdp on 17/05/2017.
 */
public class NetbeansModuleComponent implements ModuleComponent {
    private final Logger logger = Logger.getInstance("com.trangdp.plugin.netbeans.intellij");

    private Module myModule;

    public NetbeansModuleComponent(Module module) {
        MyModuleListener myModuleListener = new MyModuleListener();
        module.getProject().getMessageBus().connect().subscribe(ProjectTopics.MODULES, myModuleListener);
        VirtualFileListener f = new MyArtifactVirtualFileListener(module.getProject(), new ArtifactManagerImpl(module.getProject()));
        LocalFileSystem.getInstance().addVirtualFileListener(f);

        this.myModule = module;
    }

    @Override
    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    @Override
    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "NetbeansModuleComponent";
    }

    @Override
    public void moduleAdded() {
        // Invoked when the module corresponding to this component instance has been completely
        // loaded and added to the project.
        NotificationUtil.notify("module component >>> event triggered");

        Project project = myModule.getProject();

        if (myModule.getModuleFile() != null) {
            ApplicationManager.getApplication().invokeLater(() -> {

                try {
                    LocalFileSystem.getInstance().addVirtualFileListener(new MyArtifactVirtualFileListener(project, new ArtifactManagerImpl(project)));

                    String relPath = myModule.getModuleFilePath().substring(0, myModule.getModuleFilePath().lastIndexOf(File.separator));
                    VirtualFile moduleRoot = project.getBaseDir().getFileSystem().findFileByPath(relPath);
                    if (moduleRoot.isDirectory()) {
                        String netbeansPropertiesPath = String.format("%s/nbproject/project.properties", moduleRoot.getCanonicalPath());

                        VirtualFile netbeansPropertiesVfLocal = LocalFileSystem.getInstance().findFileByPath(netbeansPropertiesPath);

                        if(netbeansPropertiesVfLocal != null) {
                            String content = new String(netbeansPropertiesVfLocal.contentsToByteArray());
                            ProjectFileConverter netbeans = new ConverterFactory().getConverter("netbeans-to-intellij");
                            netbeans.setModule(myModule);
                            netbeans.setProjectFileContent(content);
                            netbeans.convert();
                        }

                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            });
        }
    }

    private class MyModuleListener implements ModuleListener {
        @Override
        public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
            myModule = null;

            NotificationUtil.notify("Module " + module.getName() + " removed");
        }

        @Override
        public void moduleAdded(@NotNull Project project, @NotNull Module module) {
            myModule = module;

        }
    }

    private class MyArtifactVirtualFileListener extends ArtifactVirtualFileListener {
        @NotNull
        Project project;

        public MyArtifactVirtualFileListener(Project project, ArtifactManagerImpl artifactManager) {
            super(project, artifactManager);

            this.project = project;
        }

        @Override
        public void fileCreated(@NotNull VirtualFileEvent event) {
            super.fileCreated(event);
            logger.debug(event.getFileName());

            Module module = ModuleUtilCore.findModuleForFile(event.getFile(), project);

            if(module != null && event.getFileName().equals(module.getName() + ".iml")) {
                NotificationUtil.notify("file created=" + event.getFileName());

                ApplicationManager.getApplication().invokeLater(() -> {
                    Project project = module.getProject();

                    if(project.isDisposed()) {
                        logger.warn("Project " + project.getName() + " is disposed");
                        return;
                    }

                    try {
                        String relPath = module.getModuleFilePath().substring(0, module.getModuleFilePath().lastIndexOf(File.separator));
                        VirtualFile moduleRoot = project.getBaseDir().getFileSystem().findFileByPath(relPath);
                        if (moduleRoot.isDirectory()) {
                            String netbeansPropertiesPath = String.format("%s/nbproject/project.properties", moduleRoot.getCanonicalPath());

                            VirtualFile netbeansPropertiesVfLocal = LocalFileSystem.getInstance().findFileByPath(netbeansPropertiesPath);

                            if(netbeansPropertiesVfLocal != null) {
                                String content = new String(netbeansPropertiesVfLocal.contentsToByteArray());
                                ProjectFileConverter netbeans = new ConverterFactory().getConverter("netbeans-to-intellij");
                                netbeans.setModule(module);
                                netbeans.setProjectFileContent(content);
                                netbeans.convert();
                            }
                        }
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                });
            }
        }

        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
            super.contentsChanged(event);

            Module module = ModuleUtilCore.findModuleForFile(event.getFile(), project);


            //monitor content change for file <modulename>.iml
            if(module != null && event.getFileName().equals(module.getName() + ".iml")) {
                NotificationUtil.notify("file " + event.getFileName() + " content has changed");
                logger.info("Last updateLastChecksum file " + event.getFileName() + ":" + IntellijModuleImlChecksum.getInstance().getLastCheckSum(module.getName()));

                try {
                    String lastChecksum = IntellijModuleImlChecksum.getInstance().getLastCheckSum(module.getName());
                    String currentChecksum = IntellijModuleImlChecksum.getInstance().checksum(module.getModuleFilePath());
                    if(!currentChecksum.equals(lastChecksum)) {
                        ProjectFileConverter converter = new ConverterFactory().getConverter("intellij-to-netbeans");
                        converter.setModule(module);
                        converter.setProjectFileContent(new String(module.getModuleFile().contentsToByteArray()));
                        converter.convert();
                    }

                    IntellijModuleImlChecksum.getInstance().updateLastChecksum(module.getName(), module.getModuleFilePath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //TODO: monitor content change for file nbproject/project.properties
        }
    }
}
