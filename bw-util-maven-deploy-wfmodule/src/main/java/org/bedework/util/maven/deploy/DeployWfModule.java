/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.bedework.util.deployment.SplitName;
import org.bedework.util.deployment.Utils;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.String.format;

/**
 * User: mike Date: 12/18/15 Time: 00:15
 */
@Mojo(name = "bw-deploy-wfmodule",
        defaultPhase = LifecyclePhase.INSTALL,
        requiresDependencyCollection = ResolutionScope.RUNTIME,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class DeployWfModule extends AbstractMojo {
  @Parameter(defaultValue = "${project.build.directory}", readonly = true)
  private String target;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${settings.localRepository}")
  private String localRepository;

  /**
   * Location of the wildfly instance
   */
  @Parameter(required = true,
          defaultValue = "${org.bedework.deployment.basedir}/wildfly")
  private String wildflyPath;

  @Parameter
  private boolean debug;

  @Parameter(defaultValue = "${org.bedework.deploy.modules}")
  private boolean deployModules;

  @Parameter
  private List<FileInfo> jarResources;

  @Parameter
  private List<JarDependency> jarDependencies;

  @Parameter
  private List<String> moduleDependencies;

  @Parameter(required = true)
  private String moduleName;

  @Parameter
  private FileInfo artifact;

  /**
   * Set true if we are just building modules from jar dependencies
   */
  @Parameter(defaultValue = "false")
  private boolean noArtifact;

  private Utils utils;

  public DeployWfModule() {

  }

  public void execute() throws MojoFailureException {
    if (!deployModules) {
      return;
    }

    final List<FileArtifact> fileArtifacts = new ArrayList<>();

    // Build a list of all dependencies
    // artifact.getFile()) needs requiresDependencyResolution
    for (final Artifact artifact: project.getArtifacts()) {
      fileArtifacts.add(FileArtifact.fromMavenArtifact(artifact));
    }

    final var logger = getLog();
    utils = new Utils(logger);

    debug = logger.isDebugEnabled();
    if (debug) {
      utils.setDebug(true);
    }

    final var model = project.getModel();

    if (noArtifact) {
      utils.info(format("Deploy module %s with no artifact",
                        moduleName));
    } else {
      if (artifact == null) {
        artifact = new FileInfo(model.getGroupId(),
                                model.getArtifactId(),
                                model.getVersion(),
                                "jar",
                                null);
      }

      utils.info(format("Deploy module %s with artifact %s",
                        moduleName,
                        project.getModel().getArtifactId()));
    }

    try {
      if (moduleDependencies == null) {
        moduleDependencies = new ArrayList<>();
      }

      // First deploy any jar dependencies

      if (isEmpty(jarDependencies)) {
        utils.debug("No jar dependencies");
      } else {
        for (final var jd: jarDependencies) {
          utils.debug("Deploy jar dependency " + jd);
          moduleDependencies.add(jd.getModuleName());

          // Find the repo

          jd.setRepository(localRepository);
          deployModule(jd,
                       jd.getRepoDir(),
                       null);
          removeArtifact(fileArtifacts, jd);
        }
      }

      if (!noArtifact) {
        // Now deploy the module
        deployModule(JarDependency.fromFileInfo(moduleName,
                                                artifact,
                                                target,
                                                moduleDependencies),
                     Paths.get(target),
                     jarResources);
        removeArtifact(fileArtifacts, artifact);
      }

      if (!fileArtifacts.isEmpty()) {
        utils.warn("Unsatisfied dependencies:");
        for (final var fa: fileArtifacts) {
          utils.warn(fa.toString());
        }
      }
    } catch (final MojoFailureException mfe) {
      mfe.printStackTrace();
      throw mfe;
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new MojoFailureException(t.getMessage());
    }
  }

  private void removeArtifact(final List<FileArtifact> artifacts,
                              final FileInfo val) {
    for (final var fa: artifacts) {
      if (fa.sameAs(val)) {
        if (fa.laterThan(val)) {
          utils.warn("Project has later dependency for " + fa);
        }
      }

      artifacts.remove(fa);
      return;
    }
  }

  private void deployModule(final JarDependency fileInfo,
                            final Path pathToFile,
                            final List<FileInfo> jarResources)
          throws MojoFailureException {
    try {
      final Path pathToModuleMain =
              Paths.get(wildflyPath)
                   .resolve("modules")
                   .resolve(fileInfo.getModuleName().replace('.', '/'))
                   .resolve("main")
                   .toAbsolutePath();
      utils.debug("Try to create " + pathToModuleMain);
      Files.createDirectories(pathToModuleMain);

      final List<SplitName> resourceFiles =
              utils.getFiles(pathToModuleMain);
      final SplitName fn = deployFile(resourceFiles,
                                      pathToModuleMain,
                                      pathToFile,
                                      fileInfo);

      // Copy in the module.xml template
      final Path xmlPath = pathToModuleMain.resolve("module.xml");

      Files.writeString(xmlPath, ModuleXml.moduleXmlStr);

      final var moduleXml =
              new ModuleXml(utils,
                            xmlPath,
                            fileInfo.getModuleName());
      moduleXml.addResource(fn.getName());

      if (jarResources != null) {
        for (final var jr: jarResources) {
          jr.setRepository(localRepository);
          final SplitName jrFn = deployFile(resourceFiles,
                                            pathToModuleMain,
                                            jr.getRepoDir(),
                                            jr);
          moduleXml.addResource(jrFn.getName());
        }
      }

      if (fileInfo.getModuleDependencies() != null) {
        for (final var m: fileInfo.getModuleDependencies()) {
          moduleXml.addDependency(m);
        }
      }

      moduleXml.output();
    } catch (final MojoFailureException mfe) {
      throw mfe;
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new MojoFailureException(t.getMessage());
    }
  }

  /*
     returns the SplitName for
   */
  private SplitName deployFile(final List<SplitName> resourceFiles,
                               final Path pathToModuleMain,
                               final Path pathToFile,
                               final FileInfo fileInfo)
          throws MojoFailureException {
    try {
      //final File fileDir = utils.directory(pathToFile);
      final SplitName fn = matchFile(pathToFile, fileInfo);

      if (fn == null) {
        throw new MojoFailureException(
                format("Exactly one deployable module is required. " +
                               "None found at %s with name %s and type %s",
                       pathToFile,
                       fileInfo.getArtifactId(),
                       fileInfo.getType()));
      }

      utils.debug("Found file " + fn);

      // Find deployed file
      final SplitName mfn = matchFile(resourceFiles, fileInfo);
      final File mainDir = pathToModuleMain.toFile();

      if (mfn != null) {
        utils.debug("Found module file " + mfn);

        // Is the deployed version later?
        // Deploy if same to allow for updates.
        if (mfn.laterThan(fn)) {
          utils.info(format("%s version %s later than %s: skipping",
                            fn.getName(),
                            mfn.getVersion(),
                            fn.getVersion()));
          return fn;
        }

        // Delete the current deployed version
        final File f = utils.fileOrDir(mainDir, mfn.getName());
        utils.info("Delete file " + f);
        utils.deleteAll(f.toPath());
        resourceFiles.remove(mfn);
      }

      // copy in the file.
      utils.info(format("Deploy file %s to module %s",
                        fn.getName(), moduleName));
      final File theFile = utils.file(pathToFile.toFile(),
                                      fn.getName(), true);
      Files.copy(theFile.toPath(),
                 mainDir.toPath().resolve(fn.getName()));

      return fn;
    } catch (final MojoFailureException mfe) {
      throw mfe;
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new MojoFailureException(t.getMessage());
    }
  }

  /** Look for a file with the same prefix and type as the param
   *
   * @param pathToFile where we look
   * @param fileInfo for name and/or type
   * @return SplitName of single matching file or null
   * @throws MojoFailureException on fatal error
   */
  private SplitName matchFile(final Path pathToFile,
                              final FileInfo fileInfo)
          throws MojoFailureException {
    return matchFile(utils.getFiles(pathToFile), fileInfo);
  }

  /** Look for a file with the same prefix and type as the param
   *
   * @param files list of files to check
   * @param fileInfo for name and/or type
   * @return SplitName of single matching file or null
   * @throws MojoFailureException on fatal error
   */
  private SplitName matchFile(final List<SplitName> files,
                              final FileInfo fileInfo)
          throws MojoFailureException {
    utils.debug("Match " + fileInfo);
    if (files == null) {
      return null;
    }

    SplitName file = null;

    for (final SplitName sn: files) {
      utils.debug("Try " + sn);
      if ((fileInfo.getArtifactId() != null) &&
         !fileInfo.getArtifactId().equals(sn.getArtifactId())) {
        continue;
      }

      if ((fileInfo.getType() != null) &&
              !fileInfo.getType().equals(sn.getType())) {
        continue;
      }

      if (file != null) {
        throw new MojoFailureException(
                "Exactly one deployable module resource of given name is required");
      }

      file = sn;
    }

    return file;
  }

  public static boolean isEmpty(final Collection<?> val) {
    if (val == null) {
      return true;
    }

    return val.isEmpty();
  }
}