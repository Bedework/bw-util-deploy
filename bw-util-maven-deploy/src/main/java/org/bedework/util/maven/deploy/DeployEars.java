/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.bedework.util.deployment.Process;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

/**
 * User: mike Date: 12/18/15 Time: 00:15
 */
@Mojo(name = "bw-deploy")
public class DeployEars extends AbstractMojo {
  @Parameter(defaultValue = "${project.build.directory}", readonly = true)
  private File target;

  @Parameter(required = true)
  private String baseDirPath;

  // Set to deploy from remote resource (untested)
  @Parameter
  private String inUrl;

  @Parameter
  private String deployDirPath;

  @Parameter
  private boolean debug;

  @Parameter
  private boolean noversion;

  @Parameter
  private boolean checkonly;

  @Parameter(defaultValue = "true")
  private boolean delete;

  @Parameter(defaultValue = "true")
  private boolean forWildfly;

  @Parameter(defaultValue = "true")
  private boolean cleanup;

  @Parameter
  private String earName;

  @Parameter
  private String warName;

  @Parameter
  private String sarName;

  @Parameter
  private String resourcesBase;

  public DeployEars() {

  }

  public void execute() throws MojoFailureException {
    final Process pe = new Process();

    int numFound = 0;
    if (earName != null) {
      numFound++;
    }
    if (warName != null) {
      numFound++;
    }
    if (sarName != null) {
      numFound++;
    }
    if (numFound != 1) {
      throw new MojoFailureException("Exactly one of earName, sarName or warName is required");
    }

    pe.setBaseDirPath(baseDirPath);
    pe.setInUrl(inUrl);
    pe.setInDirPath(target.getAbsolutePath());
    pe.setOutDirPath(target.toPath().resolve("modified").toString());
    pe.setDeployDirPath(deployDirPath);
    pe.setArgDebug(debug);
    pe.setForWildfly(forWildfly);
    pe.setNoversion(noversion);
    pe.setCheckonly(checkonly);
    pe.setDelete(delete);
    pe.setCleanup(cleanup);
    pe.setEarName(earName);
    pe.setSarName(sarName);
    pe.setWarName(warName);

    pe.execute();
  }
}
