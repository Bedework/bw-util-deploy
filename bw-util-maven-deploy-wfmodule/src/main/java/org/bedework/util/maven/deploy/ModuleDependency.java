/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.apache.maven.plugins.annotations.Parameter;

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

  public ModuleDependency() {}

  public ModuleDependency(final String name, final boolean export) {
    this.name = name;
    this.export = export;
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
}
