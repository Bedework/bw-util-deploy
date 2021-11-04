/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

import static org.bedework.util.deployment.Utils.compareStrings;

/**
 * User: mike Date: 2/15/21 Time: 23:38
 */
public class ModuleDependency
        implements Comparable<ModuleDependency> {
  @Parameter
  private String name;

  @Parameter(defaultValue = "true")
  private boolean export = true;

  @Parameter
  private List<String> exports;

  @Parameter(defaultValue = "false")
  boolean importMeta = false;

  public ModuleDependency() {}

  public ModuleDependency(final String name,
                          final boolean export,
                          final List<String> exports,
                          final boolean importMeta) {
    this.name = name;
    this.export = export;
    this.exports = exports;
    this.importMeta = importMeta;
  }

  @Override
  public int compareTo(final ModuleDependency that) {
    return compareStrings(getName(),
                          that.getName());
  }

  public boolean equals(final Object o) {
    if (!(o instanceof ModuleDependency)) {
      return false;
    }

    return compareTo((ModuleDependency)o) == 0;
  }

  public String getName() {
    return name;
  }

  public boolean isExport() {
    return export;
  }

  public List<String> getExports() {
    return exports;
  }

  public boolean isImportMeta() {
    return importMeta;
  }
}
