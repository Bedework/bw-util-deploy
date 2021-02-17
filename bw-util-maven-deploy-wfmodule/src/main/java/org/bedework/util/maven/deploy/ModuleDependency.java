/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * User: mike Date: 2/15/21 Time: 23:38
 */
public class ModuleDependency {
  @Parameter
  String name;

  @Parameter(defaultValue = "true")
  boolean export = true;

  public ModuleDependency() {}

  public ModuleDependency(final String name, final boolean export) {
    this.name = name;
    this.export = export;
  }
}
