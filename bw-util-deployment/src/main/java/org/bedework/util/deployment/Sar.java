package org.bedework.util.deployment;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Represent a war for deployment.
 *
 * @author douglm
 */
public class Sar extends DeployableResource implements Updateable {
  private final ApplicationXml appXml;

  private final boolean noEars;

  public Sar(final Utils utils,
             final String path,
             final SplitName sn,
             final ApplicationXml appXml,
             final boolean noEars,
             final PropertiesChain props,
             final String filterPrefix) throws Throwable {
    super(utils, path, sn, props, filterPrefix + sn.getArtifactId() + ".");

    this.appXml = appXml;
    this.noEars = noEars;

    final File meta = utils.subDirectory(theFile, "META-INF", false);
    if (!meta.exists()) {
      utils.debug("Create " + meta);
      meta.mkdir();
    }

    if (noEars) {
      final File webMeta =
              utils.subDirectory(theFile, "META-INF", true);
      doDependecies(webMeta);
    }
  }

  @Override
  public void update() throws Throwable {
    utils.debug("Update war " + getSplitName());

    copyDocs();

    updateLib(noEars);
  }

  private void copyDocs() throws Throwable {
    final String fromName = props.get("app.moredocs");
    if (fromName == null) {
      return;
    }

    final File docs = utils.subDirectory(theFile, "docs", true);

    final Path outPath = Paths.get(docs.getAbsolutePath());
    final Path inPath = Paths.get(fromName);

    utils.debug("Copy from " + inPath + " to " + outPath);

    utils.copy(inPath, outPath, true, props);
  }
}
