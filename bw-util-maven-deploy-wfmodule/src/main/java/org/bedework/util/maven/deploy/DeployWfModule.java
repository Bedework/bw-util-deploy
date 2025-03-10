/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.bedework.util.deployment.SplitName;
import org.bedework.util.deployment.Utils;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * User: mike Date: 12/18/15 Time: 00:15
        defaultPhase = LifecyclePhase.INSTALL,
 */
@Mojo(name = "bw-deploy-wfmodule",
        requiresDependencyCollection = ResolutionScope.RUNTIME,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class DeployWfModule extends AbstractMojo {
  @Parameter(defaultValue = "${project.build.directory}")
  private String target;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${settings.localRepository}")
  private String localRepository;

  /**
   * Location of the module parent
   */
  @Parameter(required = true,
          defaultValue = "${org.bedework.deployment.basedir}/wildfly")
  private String modulesParentPath;

  /* So we can override from the command line. */
  @Parameter(defaultValue = "${org.bedework.modules.parent.path}")
  private String overrideModulesParentPath;

  @Parameter
  private boolean debug;

  @Parameter(defaultValue = "${org.bedework.deploy.modules}")
  private boolean deployModules;

  @Parameter(property="org.bedework.modulesRootDir",
          defaultValue = "modules")
  private String modulesRootDir;

  @Parameter(defaultValue = "${org.bedework.thin.modules}")
  protected boolean buildThin;

  @Parameter
  private List<FileInfo> jarResources;

  @Parameter
  private List<JarDependency> jarDependencies;

  @Parameter
  private List<ModuleDependency> moduleDependencies;

  @Parameter
  private String moduleName;

  @Parameter
  private String mainClass;

  @Parameter
  private FileInfo artifact;

  /**
   * Set true if we are just building modules from jar dependencies
   */
  @Parameter(defaultValue = "false")
  private boolean noArtifact;

  /**
   * If true will add "javax.api" to module inclusion
   */
  @Parameter(defaultValue = "true")
  private boolean includeJavax;

  private Utils utils;

  public DeployWfModule() {

  }

  public void execute() throws MojoFailureException {
    if (!deployModules) {
      return;
    }

    if (moduleName == null) {
      throw new MojoFailureException("moduleName is required");
    }

    final Log logger = getLog();
    utils = new Utils(logger);

    debug = logger.isDebugEnabled();
    if (debug) {
      utils.setDebug(true);
    }

    if (overrideModulesParentPath != null) {
      modulesParentPath = overrideModulesParentPath;
    }

    if (buildThin) {
      noArtifact = true;
    }

    final List<FileArtifact> fileArtifacts = new ArrayList<>();

    // Build a list of all dependencies
    // artifact.getFile()) needs requiresDependencyResolution
    for (final Dependency artifact: project.getDependencies()) {
      final FileArtifact fa = FileArtifact.from(artifact);
      utils.debug(format("WfModules: Adding dependency %s", fa));
      fileArtifacts.add(fa);
    }

    final Model model = project.getModel();

    if (noArtifact) {
      utils.info(format("WfModules: Deploy module %s with no artifact",
                        moduleName));
    } else {
      if (artifact == null) {
        artifact = new FileInfo(model.getGroupId(),
                                model.getArtifactId(),
                                null,
                                model.getVersion(),
                                "jar",
                                null);
      }

      utils.info(format("WfModules: Deploy module %s with artifact %s",
                        moduleName,
                        project.getModel().getArtifactId()));
    }

    try {
      if (moduleDependencies == null) {
        moduleDependencies = new ArrayList<>();
      } else {
        // Remove any project dependencies satisfied by modules.
        for (final ModuleDependency md: moduleDependencies) {
          removeModuleArtifacts(md.getName(), fileArtifacts);
        }
      }

      if (!noArtifact) {
        // Now deploy the module
        deployModule(JarDependency.fromFileInfo(moduleName,
                                                artifact,
                                                target,
                                                jarDependencies,
                                                moduleDependencies),
                     Paths.get(target),
                     jarResources,
                     fileArtifacts,
                     mainClass);
        removeArtifact(fileArtifacts, artifact);
      } else {
        deployModule(JarDependency.forNoArtifact(moduleName,
                                                jarDependencies,
                                                moduleDependencies),
                     null,
                     jarResources,
                     fileArtifacts,
                     mainClass);
      }

      /* This will only work if we determine what the jar
         dependencies are for module dependencies and delete
         them from the fileArtifacts list

      if (!fileArtifacts.isEmpty()) {
        utils.warn("WfModules: Unsatisfied dependencies:");
        for (final FileArtifact fa: fileArtifacts) {
          utils.warn(format("WfModules: %s", fa.toString()));
        }
      }
       */

      /*
      utils.info("project output: " + project.getBuild().getDirectory());
      utils.info("project name: " + project.getName());
      utils.info("project parent: " + project.getParent().getName());
      utils.info("module name: " + moduleName);
      utils.info("depends on: ");
      for (final ModuleDependency md: moduleDependencies) {
        utils.info("   -->" + md.getName());
      }
      */

      final Path xmlPath = Paths.get(
              project.getBuild().getDirectory()).resolve("moduleInfo.xml");

      try (final FileWriter fw = new FileWriter(xmlPath.toFile());
           final BufferedWriter bw = new BufferedWriter(fw)) {
        bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        bw.write("<module>\n");
        bw.write("  <project-name>" + project.getName() + "</project-name>\n");
        final MavenProject parent = project.getParent();
        if (parent != null) {
          bw.write("  <project-parent>" + parent.getName() + "</project-parent>\n");
        }

        bw.write("  <module-name>" + moduleName + "</module-name>\n");

        if (!moduleDependencies.isEmpty()) {
          bw.write("  <dependencies>\n");

          for (final ModuleDependency md: moduleDependencies) {
            bw.write("    <module>" + md.getName() + "</module>\n");
            utils.info("   -->" + md.getName());
          }
          bw.write("  </dependencies>\n");
        }

        bw.write("</module>\n");
      }


    } catch (final MojoFailureException mfe) {
      mfe.printStackTrace();
      throw mfe;
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new MojoFailureException(t.getMessage());
    }
  }

  private FileArtifact findArtifact(final List<FileArtifact> artifacts,
                                    final FileInfo val) {
    FileArtifact theArtifact = null;

    for (final FileArtifact fa: artifacts) {
      if (fa.sameAs(val)) {
        if (fa.laterThan(val)) {
          utils.warn("Project has later dependency for " + fa);
        }

        if (theArtifact != null) {
          utils.warn("Project has multiple dependencies on " + fa);
        }
        theArtifact = fa;
      }
    }

    return theArtifact;
  }

  private void removeArtifact(final List<FileArtifact> artifacts,
                              final FileInfo val) {
    final ListIterator<FileArtifact> it = artifacts.listIterator();

    while (it.hasNext()) {
      final FileArtifact fa = it.next();
      if (fa.sameAs(val)) {
        if (fa.laterThan(val)) {
          utils.warn("Project has later dependency for " + fa);
        }

        utils.debug("WfModules: Remove dependency: " + fa);
        it.remove();
      }
    }
  }

  private void removeModuleArtifacts(final String moduleName,
                                     final List<FileArtifact> artifacts)
          throws MojoFailureException {
    List<SplitName> moduleFiles =
            utils.getFiles(getPathToModuleMain(moduleName));

    if (moduleFiles == null) {
      moduleFiles =
              utils.getFiles(getPathToSystemModuleMain(moduleName));
    }

    if (moduleFiles == null) {
      return;
    }

    for (final SplitName sn: moduleFiles) {
      final FileArtifact fa = FileArtifact.from(sn);
      utils.debug("WfModule: Remove dependency " + fa);
      artifacts.remove(fa);
    }
  }

  private Path getPathToModuleMain(final String moduleName) {
    return Paths.get(project.getBuild().getDirectory())
                .resolve(modulesRootDir)
                .resolve(moduleName.replace('.', '/'))
                .resolve("main")
                .toAbsolutePath();
  }

  private Path getPathToSystemModuleMain(final String moduleName) {
    return Paths.get(modulesParentPath)
                .resolve("modules")
                .resolve("system")
                .resolve("layers")
                .resolve("base")
                .resolve(moduleName.replace('.', '/'))
                .resolve("main")
                .toAbsolutePath();
  }

  private void deployModule(final JarDependency fileInfo,
                            final Path pathToFile,
                            final List<FileInfo> jarResources,
                            final List<FileArtifact> artifacts,
                            final String mainClass)
          throws MojoFailureException {
    try {
      final Path pathToModuleMain = getPathToModuleMain(
              fileInfo.getModuleName());

      utils.debug("Try to create " + pathToModuleMain);
      Files.createDirectories(pathToModuleMain);

      final List<SplitName> resourceFiles =
              utils.getFiles(pathToModuleMain);

      // Copy in the module.xml template
      final Path xmlPath = pathToModuleMain.resolve("module.xml");

      try (final FileWriter fw = new FileWriter(xmlPath.toFile());
           final BufferedWriter bw = new BufferedWriter(fw)) {
        bw.write(ModuleXml.moduleXmlStr);
      }

      final ModuleXml moduleXml =
              new ModuleXml(utils,
                            xmlPath,
                            fileInfo.getModuleName());

      if (mainClass != null) {
        moduleXml.addMainClass(mainClass);
      }

      final FileArtifact theArtifact = findArtifact(artifacts, fileInfo);

      if (theArtifact != null) {
        utils.debug(format("Found artifact %s",
                           theArtifact));
      }

      if (!buildThin && (pathToFile != null)) {
        final List<SplitName> fns =
                deployFiles(resourceFiles,
                            pathToModuleMain,
                            pathToFile,
                            fileInfo);
        for (final var fn: fns) {
          moduleXml.addResource(fn.getName());
        }
      }

      // First deploy any jar dependencies

      final List<ModuleDependency> moduleDependencies;

      if (fileInfo.getModuleDependencies() != null) {
        moduleDependencies = new ArrayList<>(
                fileInfo.getModuleDependencies());
      } else {
        moduleDependencies = new ArrayList<>();
      }

      if (isEmpty(fileInfo.getJarDependencies())) {
        utils.debug("No jar dependencies");
      } else {
        for (final JarDependency jd: fileInfo.getJarDependencies()) {
          utils.debug("Deploy jar dependency " + jd);
          moduleDependencies.add(
                  new ModuleDependency(jd.getModuleName(),
                                       jd.isExport(),
                                       jd.getExports(),
                                       jd.importMeta));

          // Find the repo

          jd.setRepository(localRepository);
          deployModule(jd,
                       jd.getRepoDir(),
                       null,
                       artifacts,
                       null);
          removeArtifact(artifacts, jd);
        }
      }

      if (jarResources != null) {
        for (final FileInfo jr: jarResources) {
          utils.info("buildThin=" + buildThin +
                  " pmm=" + pathToModuleMain);
          if (buildThin) {
            moduleXml.addResourceArtifact(jr);
            continue;
          }

          jr.setRepository(localRepository);
          final List<SplitName> jrFns =
                  deployFiles(resourceFiles,
                              pathToModuleMain,
                              jr.getRepoDir(),
                              jr);
          for (final var jrFn: jrFns) {
            moduleXml.addResource(jrFn.getName());
          }
        }
      }

      if (includeJavax) {
        final ModuleDependency md =
                new ModuleDependency("javax.api", true,
                                     null, false);

        if (!moduleDependencies.contains(md)) {
          moduleDependencies.add(md);
        }
      }

      for (final ModuleDependency m: moduleDependencies) {
        moduleXml.addDependency(m);
      }

      moduleXml.output();

      final Path modulesPath = Paths.get(modulesParentPath)
                                    .resolve(modulesRootDir);
      utils.makeDir(modulesPath.toString());

      // Copy out of target/ into modules directory
      utils.copy(Paths.get(project.getBuild().getDirectory())
                      .resolve(modulesRootDir),
                 modulesPath,
                 true, null);
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
  private List<SplitName> deployFiles(
          final List<SplitName> resourceFiles,
          final Path pathToModuleMain,
          final Path pathToFile,
          final FileInfo fileInfo)
          throws MojoFailureException {
    try {
      //final File fileDir = utils.directory(pathToFile);

      final List<SplitName> fns = matchFiles(pathToFile, fileInfo);

      if (fns.isEmpty()) {
        throw new MojoFailureException(
                format("Deployable jars are required. " +
                               "None found at %s with name %s " +
                               "classifier %s and type %s",
                       pathToFile,
                       fileInfo.getArtifactId(),
                       fileInfo.getClassifier(),
                       fileInfo.getType()));
      }

      if (utils.debug()) {
        utils.debug("Found file(s) " +
                            fns.stream()
                               .map(String::valueOf)
                               .collect(Collectors.joining("\n", "{", "}")));
      }

      // Find deployed file
      final List<SplitName> mfns =
              matchFiles(resourceFiles, fileInfo);
      final File mainDir = pathToModuleMain.toFile();

      if (!mfns.isEmpty()) {
        if (utils.debug()) {
          utils.debug("Found module file(s) " +
                              mfns.stream()
                                 .map(String::valueOf)
                                 .collect(Collectors.joining("\n", "{", "}")));
        }

        /* All files in the lists are the same version.
           Just check the first one
         */

        final var mfn = mfns.get(0);
        final var fn = fns.get(0);

        // Is the deployed version later?
        // Deploy if same to allow for updates.
        if (mfn.laterThan(fn)) {
          utils.info(format("%s version %s later than %s: skipping",
                            fn.getName(),
                            mfn.getVersion(),
                            fn.getVersion()));
          return mfns;
        }

        // Delete the current deployed version

        for (final var del: mfns) {
          final File f = utils.fileOrDir(mainDir, del.getName());
          utils.info("Delete file " + f);
          utils.deleteAll(f.toPath());
          resourceFiles.remove(del);
        }
      }

      // copy in the files.
      for (final var fn: fns) {
        utils.info(format("Deploy file %s to %s",
                          fn.getName(), mainDir));
        final File theFile = utils.file(pathToFile.toFile(),
                                        fn.getName(), true);
        Files.copy(theFile.toPath(),
                   mainDir.toPath().resolve(fn.getName()));
      }

      return fns;
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
  private List<SplitName> matchFiles(final Path pathToFile,
                                     final FileInfo fileInfo)
          throws MojoFailureException {
    return matchFiles(utils.getFiles(pathToFile,
                                     fileInfo.getArtifactId(),
                                     fileInfo.getClassifier()),
                     fileInfo);
  }

  private static String cachedSnapshotVersionPattern =
          ".*?-\\d*\\.\\d*.*";

  private static Pattern compiledPattern =
          Pattern.compile(cachedSnapshotVersionPattern);

  /** Look for file with the same prefix and type as the param
   * Multiple files are valid if version matches after splitting off
   * classifier.
   *
   * @param files list of files to check
   * @param fileInfo for name and/or type
   * @return SplitName of matching files or null
   * @throws MojoFailureException on fatal error
   */
  private List<SplitName> matchFiles(final List<SplitName> files,
                                     final FileInfo fileInfo)
          throws MojoFailureException {
    utils.debug("Match " + fileInfo);
    final List<SplitName> res = new ArrayList<>();

    if (files == null) {
      return res;
    }

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

      if ((fileInfo.getType() == null) &&
              !"jar".equals(sn.getType())) {
        continue;
      }

      if ("tests".equals(sn.getClassifier())) {
        continue;
      }

      if (compiledPattern.matcher(sn.getVersion()).find()) {
        continue;
      }

      for (final var file: res) {
        if (!file.equals(sn)) {
          throw new MojoFailureException(
                  "One deployable module resource of given name " +
                          "and version is required. Already found: " + file);
        }
      }

      res.add(sn);
    }

    return res;
  }

  public static boolean isEmpty(final Collection<?> val) {
    if (val == null) {
      return true;
    }

    return val.isEmpty();
  }
}