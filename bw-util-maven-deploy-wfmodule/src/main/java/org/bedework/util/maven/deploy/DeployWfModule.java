/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.bedework.util.deployment.SplitName;
import org.bedework.util.deployment.Utils;
import org.bedework.util.deployment.XmlFile;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Element;

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
  private static final String moduleXmlStr =
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
          "<!--\n" +
          "  ~ JBoss, Home of Professional Open Source.\n" +
          "  ~ Copyright 2010, Red Hat, Inc., and individual contributors\n" +
          "  ~ as indicated by the @author tags. See the copyright.txt file in the\n" +
          "  ~ distribution for a full listing of individual contributors.\n" +
          "  ~\n" +
          "  ~ This is free software; you can redistribute it and/or modify it\n" +
          "  ~ under the terms of the GNU Lesser General Public License as\n" +
          "  ~ published by the Free Software Foundation; either version 2.1 of\n" +
          "  ~ the License, or (at your option) any later version.\n" +
          "  ~\n" +
          "  ~ This software is distributed in the hope that it will be useful,\n" +
          "  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
          "  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU\n" +
          "  ~ Lesser General Public License for more details.\n" +
          "  ~\n" +
          "  ~ You should have received a copy of the GNU Lesser General Public\n" +
          "  ~ License along with this software; if not, write to the Free\n" +
          "  ~ Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA\n" +
          "  ~ 02110-1301 USA, or see the FSF site: http://www.fsf.org.\n" +
          "  -->\n" +
          "<module xmlns=\"urn:jboss:module:1.7\">\n" +
          "    <resources>\n" +
          "    </resources>\n" +
          "\n" +
          "</module>\n";
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

  public static class JarDependency {
    /**
     * Name of the module - e.g com.fasterxml.jackson.core.annotations
     */
    @Parameter
    private String moduleName;

    @Parameter
    private String groupId;

    @Parameter
    private String artifactId;

    @Parameter
    private String version;
    @Parameter
    private String type;

    public String toString() {
      final StringBuilder sb = new StringBuilder(
              this.getClass().getSimpleName());

      sb.append("{");
      sb.append("moduleName");
      sb.append(moduleName);
      sb.append(", groupId");
      sb.append(groupId);
      sb.append(", artifactId");
      sb.append(artifactId);
      sb.append(", version");
      sb.append(version);
      sb.append(". type");
      sb.append(type);
      sb.append("}");

      return sb.toString();
    }
  }

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
  private String fileName;

  @Parameter(defaultValue = "jar")
  private String fileType;

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
        for (final JarDependency jd: jarDependencies) {
          utils.debug("Deploy jar dependency " + jd);
          moduleDependencies.add(jd.moduleName);

          // Find the repo

          final var repoDir = Paths.get(localRepository).
                  resolve(jd.groupId.replace('.', '/')).
                  resolve(jd.artifactId).
                  resolve(jd.version);
          deployModule(jd.moduleName,
                       repoDir.toAbsolutePath().toString(),
                       jd.artifactId, jd.type, null);
        }
      }

      // Now deploy the module
      deployModule(moduleName,
                   target,
                   fileName,
                   fileType,
                   moduleDependencies);
    } catch (final MojoFailureException mfe) {
      mfe.printStackTrace();
      throw mfe;
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new MojoFailureException(t.getMessage());
    }
  }

  private void deployModule(final String moduleName,
                            final String pathToFile,
                            final String fileName,
                            final String fileType,
                            final List<String> moduleDependencies) throws MojoFailureException {
    try {
      final File fileDir = utils.directory(pathToFile);
      final SplitName fn = getFileName(fileDir.toPath(),
                                       fileName, fileType);

      if (fn == null) {
        throw new MojoFailureException(
                format("Exactly one deployable module is required. " +
                               "None found at %s with name %s and type %s",
                       pathToFile, fileName, fileType));
      }

      utils.debug("Found file " + fn);

      // Find deployed file
      final Path pathToModule = Paths.get(wildflyPath).
              resolve("modules").
              resolve(moduleName.replace('.', '/')).
                                             resolve("main").
                                             toAbsolutePath();
      utils.debug("Try to create " + pathToModule);
      Files.createDirectories(pathToModule);

      final SplitName mfn = getFileName(pathToModule,
                                        fileName, fileType);
      final File mainDir = pathToModule.toFile();

      if (mfn != null) {
        utils.debug("Found module file " + mfn);

        // Is the deployed version the same or later?
        if (!fn.laterThan(mfn)) {
          utils.info(format("Deployed version %s same as or later than %s: skipping",
                            mfn.getVersion(), fn.getVersion()));
          return;
        }

        // Delete the current deployed version
        final String[] names = mainDir.list();
        if (names == null) {
          throw new MojoFailureException(
                  "Unable to get list of names in " + mainDir);
        }

        for (final String name: names) {
          final File f = utils.fileOrDir(mainDir, name);
          utils.info("Delete file " + f);
          utils.deleteAll(f.toPath());
        }
      }

      // copy in the file.
      utils.info(format("Deploy file %s to module %s",
                        fn.getName(), moduleName));
      final File theFile = utils.file(fileDir, fn.getName(), true);
      Files.copy(theFile.toPath(),
                 mainDir.toPath().resolve(fn.getName()));

      // Copy in the module.xml template
      final Path xmlPath = mainDir.toPath().resolve("module.xml");

      Files.writeString(xmlPath, moduleXmlStr);

      final var moduleXml =
              new ModuleXml(utils,
                            xmlPath.toAbsolutePath().toString(),
                            moduleName);
      moduleXml.addResource(fn.getName());

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

  private static class ModuleXml extends XmlFile {
    public ModuleXml(final Utils utils,
                     final String path,
                     final String moduleName) throws Throwable {
      super(utils, path, true);

      root.setAttribute("name", moduleName);
      updated = true;
    }

    void addResource(final String name) throws Throwable {
      final Element el = findElement(root, "resources");
      if (el == null) {
        utils.error("Cannot locate element resources");
        return;
      }

      final Element resNode = doc.createElement("resource-root");
      resNode.setAttribute("path",
                           name);

      el.appendChild(resNode);
    }

    void addDependency(final String name) throws Throwable {
      Element el = findElement(root, "dependencies");
      if (el == null) {
        el = doc.createElement("dependencies");
        root.appendChild(el);
      }

      final Element mNode = doc.createElement("module");
      mNode.setAttribute("name", name);

      el.appendChild(mNode);
    }
  }

  private SplitName getFileName(final Path pathToFile,
                                final String fileName,
                                final String fileType) throws Throwable {
    final var dir = pathToFile.toFile();
    utils.debug("Get names from dir " + dir);
    final String[] names;
    try {
      names = dir.list();
    } catch (final Throwable t) {
      t.printStackTrace();
      throw t;
    }

    if (names == null) {
      utils.debug("No entries in list");
      return null;
    }

    SplitName module = null;

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

      if (fileName != null) {
        if (!fileName.equals(sn.getPrefix())) {
          continue;
        }

        if (module != null) {
          return null;
        }

        module = sn;
        continue;
      }

      if ((fileType != null) && !fileType.equals(sn.getSuffix())) {
        continue;
      }

      if (module != null) {
        throw new MojoFailureException(
                "Exactly one deployable module is required");
      }

      module = sn;
    }

    return module;
  }

  public static boolean isEmpty(final Collection<?> val) {
    if (val == null) {
      return true;
    }

    return val.isEmpty();
  }
}