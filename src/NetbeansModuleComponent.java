import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.packaging.impl.artifacts.ArtifactManagerImpl;
import com.intellij.packaging.impl.artifacts.ArtifactVirtualFileListener;
import converter.ConverterFactory;
import converter.ProjectFileConverter;
import org.jetbrains.annotations.NotNull;
import util.NotificationUtil;

import java.io.File;

/**
 * Created by trangdp on 17/05/2017.
 */
public class NetbeansModuleComponent implements ModuleComponent {
    private final Logger logger = Logger.getInstance(NetbeansModuleComponent.class);

    private Module myModule;

    public NetbeansModuleComponent(Module module) {
        MyModuleListener myModuleListener = new MyModuleListener();
        this.myModule = module;
        this.myModule.getProject().getMessageBus().connect().subscribe(ProjectTopics.MODULES, myModuleListener);

        VirtualFileListener f = new MyArtifactVirtualFileListener(myModule.getProject(), new ArtifactManagerImpl(myModule.getProject()));
        LocalFileSystem.getInstance().addVirtualFileListener(f);
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
    public void projectOpened() {
        // called when project is opened
    }

    @Override
    public void projectClosed() {
        // called when project is being closed
    }

    @Override
    public void moduleAdded() {
        // Invoked when the module corresponding to this component instance has been completely
        // loaded and added to the project.
        NotificationUtil.notify("module component >>> event triggered");

        if (myModule.getModuleFile() != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                Project project = myModule.getProject();

                try {
                    LocalFileSystem.getInstance().addVirtualFileListener(new MyArtifactVirtualFileListener(project, new ArtifactManagerImpl(project)));

                    String relPath = myModule.getModuleFilePath().substring(0, myModule.getModuleFilePath().lastIndexOf(File.separator));
                    VirtualFile moduleRoot = project.getBaseDir().getFileSystem().findFileByPath(relPath);
                    if (moduleRoot.isDirectory()) {
                        String netbeansPropertiesPath = String.format("%s/nbproject/project.properties", moduleRoot.getCanonicalPath());

                        VirtualFile netbeansPropertiesVfLocal = LocalFileSystem.getInstance().findFileByPath(netbeansPropertiesPath);

                        if(netbeansPropertiesVfLocal != null) {
                            String content = new String(netbeansPropertiesVfLocal.contentsToByteArray());
                            ProjectFileConverter netbeans = new ConverterFactory().getConverter("netbeans");
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

        public MyArtifactVirtualFileListener(Project project, ArtifactManagerImpl artifactManager) {
            super(project, artifactManager);
        }

        @Override
        public void fileCreated(@NotNull VirtualFileEvent event) {
            super.fileCreated(event);
            logger.debug(event.getFileName());

            if(myModule != null && event.getFileName().equals(myModule.getName() + ".iml")) {
                NotificationUtil.notify("file created=" + event.getFileName());

                ApplicationManager.getApplication().invokeLater(() -> {
                    Project project = myModule.getProject();

                    if(project.isDisposed()) {
                        logger.warn("Project " + project.getName() + " is disposed");
                        return;
                    }

                    try {
                        VirtualFileListener f = new MyArtifactVirtualFileListener(project, new ArtifactManagerImpl(project));
                        LocalFileSystem.getInstance().addVirtualFileListener(f);

                        String relPath = myModule.getModuleFilePath().substring(0, myModule.getModuleFilePath().lastIndexOf(File.separator));
                        VirtualFile moduleRoot = project.getBaseDir().getFileSystem().findFileByPath(relPath);
                        if (moduleRoot.isDirectory()) {
                            String netbeansPropertiesPath = String.format("%s/nbproject/project.properties", moduleRoot.getCanonicalPath());

                            VirtualFile netbeansPropertiesVfLocal = LocalFileSystem.getInstance().findFileByPath(netbeansPropertiesPath);

                            if(netbeansPropertiesVfLocal != null) {
                                String content = new String(netbeansPropertiesVfLocal.contentsToByteArray());
                                ProjectFileConverter netbeans = new ConverterFactory().getConverter("netbeans");
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

        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
            super.contentsChanged(event);

            if(myModule != null && event.getFileName().equals(myModule.getName() + ".iml")) {
                NotificationUtil.notify("file " + event.getFileName() + " content has changed");
            }
        }
    }
}
