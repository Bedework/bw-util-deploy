/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.bedework.util.deployment.Pom;
import org.bedework.util.deployment.Utils;

import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * User: mike Date: 6/12/21 Time: 22:26
 */
public class ModulePom extends Pom {
  public ModulePom(final Utils utils,
                   final Path path,
                   final boolean nameSpaced) throws Throwable {
    super(utils, path, nameSpaced);
  }

  public static class WfModule {
    private final String moduleName;
    private final List<String> dependencies = new ArrayList<>();

    public WfModule(final String moduleName) {
      this.moduleName = moduleName;
    }

    public String getModuleName() {
      return moduleName;
    }

    public List<String> getDependencies() {
      return dependencies;
    }
  }

  public List<WfModule> getModules() {
    final List<WfModule> modules = new ArrayList<>();
    for (final Element plugin: findPlugins("org.bedework",
                                           "bw-util-maven-deploy-wfmodule")) {
      // Assume simple config
      final Element config = findElement(plugin, "configuration");
      final WfModule module =
              new WfModule(findElementContent(config,
                                              "moduleName"));
      modules.add(module);

      final Element mdepsEl = findElement(config, "moduleDependencies");
      if (mdepsEl == null) {
        continue;
      }

      for (final Element mdepEl: findElements(mdepsEl,
                                              "moduleDependency")) {
        final String name = findElementContent(mdepEl, "name");
        if (name == null) {
          utils.warn("Null name for dependency. module: " + module);
        } else {
          module.getDependencies().add(name);
        }
      }
    }

    return modules;
  }
}
