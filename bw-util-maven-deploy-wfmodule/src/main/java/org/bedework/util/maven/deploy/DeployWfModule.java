/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.bedework.util.deployment.SplitName;
import org.bedework.util.deployment.Utils;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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
@Mojo(name = "bw-deploy-wfmodule")
public class DeployWfModule extends AbstractMojo {
  @Parameter(defaultValue = "${project.build.directory}", readonly = true)
  private String target;

  @Parameter(defaultValue = "${settings.localRepository}")
  private String localRepository;

  /**
   * Location of the wildfly instance
   */
  @Parameter(required = true)
  private String wildflyPath;

  @Parameter
  private boolean debug;

  @Parameter(defaultValue = "false")
  private boolean deployModules;

  public static class FileInfo {
    @Parameter
    private String groupId;

    @Parameter
    private String artifactId;

    @Parameter
    private String version;

    @Parameter(defaultValue = "jar")
    private String type;

    private String repository;

    FileInfo() {}

    FileInfo(final String groupId,
             final String artifactId,
             final String version,
             final String type,
             final String repository) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.type = type;
      this.repository = repository;
    }

    String getGroupId() {
      return groupId;
    }

    String getArtifactId() {
      return artifactId;
    }

    String getVersion() {
      return version;
    }

    public void setVersion(final String val) {
      version = val;
    }

    String getType() {
      return type;
    }

    public String getRepository() {
      return repository;
    }

    public void setRepository(final String val) {
      repository = val;
    }

    Path getRepoDir() {
      return Paths.get(getRepository())
                  .resolve(getGroupId().replace('.', '/'))
                  .resolve(getArtifactId())
                  .resolve(getVersion());
    }

    public void toStringSegment(final StringBuilder sb) {
      sb.append(", groupId=");
      sb.append(getGroupId());
      sb.append(", artifactId=");
      sb.append(getArtifactId());
      sb.append(", version=");
      sb.append(getVersion());
      sb.append(". type=");
      sb.append(getType());
    }

    public String toString() {
      final StringBuilder sb = new StringBuilder(
              this.getClass().getSimpleName());

      sb.append("{");
      toStringSegment(sb);
      sb.append("}");

      return sb.toString();
    }
  }

  public static class JarDependency extends FileInfo {
    JarDependency(final String moduleName,
                  final String groupId,
                  final String artifactId,
                  final String version,
                  final String type,
                  final String repository,
                  final List<String> moduleDependencies) {
      super(groupId, artifactId, version, type, repository);
      this.moduleName = moduleName;
      this.moduleDependencies = moduleDependencies;
    }

    @Parameter
    private String moduleName;

    @Parameter
    private List<String> moduleDependencies;

    /**
     * Name of the module - e.g com.fasterxml.jackson.core.annotations
     */
    String getModuleName() {
      return moduleName;
    }

    List<String> getModuleDependencies() {
      return moduleDependencies;
    }

    public String toString() {
      final StringBuilder sb = new StringBuilder(
              this.getClass().getSimpleName());

      sb.append("{");
      sb.append("moduleName=");
      sb.append(getModuleName());
      super.toStringSegment(sb);
      sb.append("}");

      return sb.toString();
    }
  }

  @Parameter
  private List<FileInfo> jarResources;

  @Parameter
  private List<JarDependency> jarDependencies;

  @Parameter
  private List<String> moduleDependencies;

  @Parameter(required = true)
  private String moduleName;

  /**
   * The part of the name before the version
   */
  @Parameter
  private String artifactId;

  @Parameter(defaultValue = "jar")
  private String type;

  private Utils utils;

  public DeployWfModule() {

  }

  public void execute() throws MojoFailureException {
    if (!deployModules) {
      return;
    }

    utils = new Utils(getLog());

    if (debug) {
      utils.setDebug(true);
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
          final var repoDir = jd.getRepoDir();
          deployModule(jd,
                       null);
        }
      }

      // Now deploy the module
      deployModule(new JarDependency(moduleName,
                                     null, // Groupid
                                     artifactId,
                                     null, // version
                                     type,
                                     target,
                                     moduleDependencies),
                   jarResources);
    } catch (final MojoFailureException mfe) {
      mfe.printStackTrace();
      throw mfe;
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new MojoFailureException(t.getMessage());
    }
  }

  private void deployModule(final JarDependency fileInfo,
                            final List<FileInfo> jarResources)
          throws MojoFailureException {
    try {
      final Path pathToModuleMain =
              Paths.get(wildflyPath)
                   .resolve("modules")
                   .resolve(moduleName.replace('.', '/'))
                   .resolve("main")
                   .toAbsolutePath();
      utils.debug("Try to create " + pathToModuleMain);
      Files.createDirectories(pathToModuleMain);

      final List<SplitName> resourceFiles = getFiles(pathToModuleMain);
      final SplitName fn = deployFile(resourceFiles,
                                      pathToModuleMain, fileInfo);

      // Copy in the module.xml template
      final Path xmlPath = pathToModuleMain.resolve("module.xml");

      Files.writeString(xmlPath, ModuleXml.moduleXmlStr);

      final var moduleXml =
              new ModuleXml(utils,
                            xmlPath,
                            moduleName);
      moduleXml.addResource(fn.getName());

      if (jarResources != null) {
        for (final var jr: jarResources) {
          final SplitName jrFn = deployFile(resourceFiles,
                                            pathToModuleMain,
                                            jr);
          moduleXml.addResource(jrFn.getName());
        }
      }

      if (!isEmpty(moduleDependencies)) {
        for (final var m: moduleDependencies) {
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
                               final FileInfo fileInfo)
          throws MojoFailureException {
    try {
      final Path pathToFile = fileInfo.getRepoDir();
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

        // Is the deployed version the same or later?
        if (!fn.laterThan(mfn)) {
          utils.info(format("%s version %s same as or later than %s: skipping",
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

  private List<SplitName> getFiles(final Path pathToFile)
          throws MojoFailureException {
    final var dir = pathToFile.toFile();
    utils.debug("Get names from dir " + dir);
    final String[] names;
    try {
      names = dir.list();
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new MojoFailureException(t.getMessage());
    }

    if (names == null) {
      utils.debug("No entries in list");
      return null;
    }

    final List<SplitName> files = new ArrayList<>();

    for (final String nm: names) {
      utils.debug("Found " + nm);
      final SplitName sn = SplitName.testName(nm);

      if (sn == null) {
        //utils.warn("Unable to process " + nm);
        continue;
      }

      // Should we skip?
      if (nm.endsWith("-sources.jar") ||
              nm.endsWith(".sha1") ||
              nm.endsWith(".pom")) {
        continue;
      }

      files.add(sn);
    }

    return files;
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
    return matchFile(getFiles(pathToFile), fileInfo);
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
    if (files == null) {
      return null;
    }

    SplitName file = null;

    for (final SplitName sn: files) {
      if (fileInfo.getArtifactId() != null) {
        if (!fileInfo.getArtifactId().equals(sn.getPrefix())) {
          continue;
        }

        if (file != null) {
          return null;
        }

        file = sn;
        continue;
      }

      if ((fileInfo.getType() != null) &&
              !fileInfo.getType().equals(sn.getSuffix())) {
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