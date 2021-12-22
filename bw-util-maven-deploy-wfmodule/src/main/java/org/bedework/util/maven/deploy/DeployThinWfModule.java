/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * User: mike Date: 12/21/21 Time: 15:00
 */
@Mojo(name = "bw-deploy-thin-wfmodule",
        requiresDependencyCollection = ResolutionScope.RUNTIME,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class DeployThinWfModule extends DeployWfModule {
  public void execute() throws MojoFailureException {
    buildThin = true;
    super.execute();
  }
}
