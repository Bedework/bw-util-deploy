package org.bedework.util.deployment;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.bedework.util.deployment.NetUtil.DavChild;

/** Process a ear for deployment. The ear is in its exploded form -
 * nothing zipped. This app will use the wars inside as patterns
 * and update them to include virtual host and security domains.
 *
 * <p>Some wars may be duplicated e.g. to provide a calendar suite</p>
 *
 * <p>War names are of the form <br/>
 * &lt;name-part&gt;-&lt;version&gt;.war<br/>
 * where the name part identifies the war and can be used as a
 * template name for duplication. It is also used as the key to
 * properties.</p>
 *
 * @author douglm
 */
public class Process extends AbstractMojo {
  /** The path of directory containing the appserver
   * This is the value of the baseDir parameter
   */
  public static final String propBaseDir =
          "org.bedework.global.baseDir";

  /** True if we are processing wars rather than ear files.
   * In this case the war will probably have its own lib */
  public static final String propWarsOnly =
          "org.bedework.global.warsonly";

  private String errorMsg;

  private String baseDirPath;

  private String inUrl;

  private String inDirPath;

  private String outDirPath;

  private String deployDirPath;

  private boolean argDebug;

  private boolean noversion;

  private boolean checkonly;

  private boolean warsonly;

  private boolean delete;

  private boolean cleanup = true;

  private String warName;

  private String earName;

  private String propsPath;

  private Properties props;

  private Utils utils;

  private final PropertiesChain pc = new PropertiesChain();

  private final List<Path> tempDirs = new ArrayList<>();

  private void loadProperties() throws Throwable {
    utils.info("*********************************************" +
                       "Properties file: " + propsPath);
    final File f = utils.file(propsPath);

    final FileReader fr = new FileReader(f);

    props = new Properties();

    props.load(fr);

    props.setProperty(propWarsOnly, String.valueOf(warsonly));

    props.setProperty(propBaseDir, baseDirPath);
  }

  public void setBaseDirPath(final String val) {
    baseDirPath = val;
  }

  public String getBaseDirPath() {
    return baseDirPath;
  }

  public void setInUrl(final String val) {
    inUrl = val;
  }

  public String getInUrl() {
    return inUrl;
  }

  public void setInDirPath(final String val) {
    inDirPath = val;
  }

  public String getInDirPath() {
    return inDirPath;
  }

  public void setOutDirPath(final String val) {
    outDirPath = val;
  }

  public void setDeployDirPath(final String val) {
    deployDirPath = val;
  }

  public void setArgDebug(final boolean val) {
    argDebug = val;
  }

  public void setWarsOnly(final boolean val) {
    warsonly = val;
  }

  public void setNoversion(final boolean val) {
    noversion = val;
  }

  public void setCheckonly(final boolean val) {
    checkonly = val;
  }

  public void setDelete(final boolean val) {
    delete = val;
  }

  public void setCleanup(final boolean val) {
    cleanup = val;
  }

  public void setEarName(final String val) {
    earName = val;
  }

  public void setWarName(final String val) {
    warName = val;
    setWarsOnly(warName != null);
  }

  public void setPropsPath(final String val) {
    propsPath = val;
  }

  public String getPropsPath() {
    return propsPath;
  }

  public void execute() throws MojoFailureException {
    utils = new Utils(getLog());

    if (argDebug) {
      utils.setDebug(true);
    }

    try {
      loadProperties();

      pc.push(props);

      inUrl = defaultVal(inUrl,
                         "org.bedework.postdeploy.inurl");

      if (inUrl == null) {
        inDirPath = defaultVal(inDirPath,
                               "org.bedework.postdeploy.in",
                               "--in");
      } else {
        inDirPath = getRemoteFiles(inUrl);
        if (inDirPath == null) {
          return;
        }
      }

      outDirPath = defaultVal(outDirPath,
                              "org.bedework.postdeploy.out",
                              "--out");

      deployDirPath = defaultVal(deployDirPath,
                                 "org.bedework.postdeploy.deploy");

      if (errorMsg != null) {
        throw new MojoFailureException(errorMsg);
      }

      utils.info("input: " + inDirPath);
      utils.info("output: " + outDirPath);
      if (deployDirPath != null) {
        utils.info("deploy: " + deployDirPath);
      }

      //utils.info("resources: " + resourcesBase);

      cleanOut(outDirPath);

      for (final SplitName sn: getDeployedNames(deployDirPath,
                                                null)) {
        if (!"war".equals(sn.getType()) && !"ear".equals(
                sn.getType())) {
          continue;
        }

        // Add version to properties for dependencies
        if (utils.debug()) {
          utils.debug("adding org.bedework.global.versions." +
                              sn.getArtifactId() + "=" +
                              sn.getVersion());
        }
        utils.setVersionsProp("org.bedework.global.versions." + sn
                                      .getArtifactId(),
                              sn.getVersion());
      }

      if (!warsonly) {
        processEars();
      } else {
        processWars();
      }

    } catch (final Throwable t) {
      t.printStackTrace();
    }

    if (cleanup) {
      // Try to delete any temp directories
      for (final Path tempPath: tempDirs) {
        try {
          utils.deleteAll(tempPath);
        } catch (final Throwable t) {
          utils.warn("Error trying to delete " + tempPath);
        }
      }
    }
  }

  private void processEars() throws Throwable {
    if (earName != null) {
      utils.info("earName: " + earName);
    }

    final List<String> earNames =
            pc.listProperty("org.bedework.ear.names");

    final List<PathAndName> toProcess = buildUpdateableList(earName,
                                                            earNames,
                                                            "ear");
    if (toProcess == null) {
      return;
    }

    final List<Updateable> toUpdate = new ArrayList<>();

    for (final PathAndName pan: toProcess) {
      toUpdate.add(new Ear(utils, pan.getPath(), pan.getSplitName(), pc));
    }

    if (checkonly) {
      return;
    }

    for (final Updateable upd: toUpdate) {
      upd.update();
    }

    deployFiles("ear");
  }

  private void processWars() throws Throwable {
    if (warName != null) {
      utils.info("warName: " + warName);
    }

    final List<String> warNames =
            pc.listProperty("org.bedework.war.names");

    final List<PathAndName> toProcess = buildUpdateableList(warName,
                                                            warNames,
                                                            "war");
    if (toProcess == null) {
      return;
    }

    final List<Updateable> toUpdate = new ArrayList<>();

    for (final PathAndName pan: toProcess) {
      toUpdate.add(new War(utils,
                           pan.getPath(),
                           pan.getSplitName(), null, pc,
                           "org.bedework.app."));
    }

    if (checkonly) {
      return;
    }

    for (final Updateable upd: toUpdate) {
      upd.update();
    }

    deployFiles("war");
  }

  private List<PathAndName> buildUpdateableList(
          final String specificNames,
          final List<String> allowedNames,
          final String suffix) throws Throwable {
    utils.info("Specific names = " + specificNames);
    final Set<String> names = new TreeSet<>(
            Arrays.asList(specificNames.split(",")));

    utils.info("List of names = " + String.join(",", names));

    final List<SplitName> splitNames = getInFiles(inDirPath,
                                                  allowedNames,
                                                  suffix);
    if (splitNames == null) {
      utils.error("No names available. Terminating");
      return null;
    }

    final List<SplitName> deployed = getDeployedNames(deployDirPath,
                                                      suffix);
    if (deployed == null) {
      utils.error("No deploy directory available. Terminating");
      return null;
    }

    final List<PathAndName> files = new ArrayList<>();

    for (final SplitName sn: splitNames) {
      if (!names.contains(sn.getArtifactId())) {
        // We were given a specific name and this isn't it
        utils.warn("Prefix " + sn.getArtifactId() +
                           " for file " + sn.getName() +
                           " not in properties as deployable file. Skipping");
        continue;
      }

      if (!noversion) {
        // See if this is a later version than the deployed file
        if (!sn.laterThan(deployed)) {
          utils.warn("File " + sn.getName() + " not later than deployed file. Skipping");
          continue;
        }
      }

      if (!allowedNames.contains(sn.getArtifactId())) {
        utils.warn(sn.getName() + " is not in the list of supported files. Skipped");
        continue;
      }

      if (checkonly) {
        utils.info("File " + sn.getName() + " is deployable");
        continue;
      }

      utils.info("Processing " + sn.getName());

      final Path inPath = Paths.get(inDirPath, sn.getName());
      final Path outPath = Paths.get(outDirPath, sn.getName());

      if (delete) {
        final File outFile = outPath.toFile();

        if (outFile.exists()) {
          utils.deleteAll(outPath);
        }
      }

      if (inPath.toFile().isFile()) {
        // Need to unzip it
        unzip(inPath.toString(), outPath.toString());
      } else {
        utils.copy(inPath, outPath, false, null);
      }

      files.add(new PathAndName(outDirPath, sn));
    }

    return files;
  }

  private void deployFiles(final String suffix) throws Throwable {
    if (deployDirPath == null) {
      utils.info("No deployment path specified. Terminating");
      return;
    }

    int deployed = 0;

    final boolean wildfly = Boolean.valueOf(pc.get("org.bedework.for.wildfly"));
    if (wildfly) {
      utils.info("Processing for wildfly");
    }

    for (final SplitName sn: getDeployedNames(outDirPath,
                                              suffix)) {
      utils.info("Deploying " + sn.getName());
      deployed++;

      utils.deleteMatching(deployDirPath, sn);
      final Path deployPath = Paths.get(deployDirPath, sn.getName());

      if (delete) {
        final File deployFile = deployPath.toFile();

        if (deployFile.exists()) {
          utils.deleteAll(deployPath);
        }
      }

      if (wildfly) {
        // Remove any deployment directive files
        Path thePath = Paths.get(deployDirPath, sn.getName() + ".failed");
        File theFile = thePath.toFile();

        if (theFile.exists()) {
          if (!theFile.delete()) {
            utils.warn("Unable to delete file " + theFile);
          }
        }

        thePath = Paths.get(deployDirPath, sn.getName() + ".deployed");
        theFile = thePath.toFile();

        if (theFile.exists()) {
          if (!theFile.delete()) {
            utils.warn("Unable to delete file " + theFile);
          }
        }

        thePath = Paths.get(deployDirPath, sn.getName() + ".dodeploy");
        theFile = thePath.toFile();

        if (theFile.exists()) {
          if (!theFile.delete()) {
            utils.warn("Unable to delete file " + theFile);
          }
        }

      }

      final Path outPath = Paths.get(outDirPath, sn.getName());
      utils.copy(outPath, deployPath, false, null);

      if (wildfly) {
        final File doDeploy = Paths.get(deployDirPath,
                                        sn.getName() + ".dodeploy").toFile();
        if (!doDeploy.createNewFile()) {
          utils.warn("Unable to create file " + doDeploy);
        }
      }
    }

    utils.info("Deployed " + deployed + " " +
                       suffix +
                       "s");
  }

  private Path getTempDirectory(final String prefix) throws Throwable {
    final Path tempPath = Files.createTempDirectory(prefix);

    tempDirs.add(tempPath);
    return tempPath;
  }

  /**
   *
   * @param inUrl the remote ear repository
   * @return path to directory containing downloaded files or null for errors.
   * @throws Throwable
   */
  private String getRemoteFiles(final String inUrl) throws Throwable {
    final Path downloadPath = getTempDirectory("bwdownload");
    final Path expandPath = getTempDirectory("bwexpand");
    final String sourceEars = expandPath.toAbsolutePath().toString();

    final HttpClientBuilder clb = HttpClients.custom();

    try (final CloseableHttpClient cl =
                 clb.disableRedirectHandling().build()) {

      final NetUtil du = new NetUtil();
      final URI inUri = new URI(inUrl);

      final List<DavChild> dcs = du.getChildrenUrls(cl, inUri);

      if (dcs.isEmpty()) {
        utils.warn("No files at " + inUrl);
        return null;
      }

      for (final DavChild dc: dcs) {
        URI dcUri = new URI(dc.uri);
        if (dcUri.getHost() == null) {
          dcUri = inUri.resolve(dc.uri);
        }

        if (getLog().isDebugEnabled()) {
          utils.info("Found url " + dcUri);
        }

        final String zipName = dc.displayName + ".zip";

        final Path zipPath = downloadPath.resolve(zipName);
        final OutputStream zipOut = new FileOutputStream(zipPath.toFile());

        if (!NetUtil.getBinary(cl,
                               dcUri,
                               zipOut)) {
          utils.warn("Unable to fetch " + dcUri);
          return null;
        }

        unzip(zipPath.toAbsolutePath().toString(),
              sourceEars);
      }
    }

    return sourceEars;
  }

  private void unzip(final String zipPath,
                     final String destDir) throws Throwable {
    final byte[] buffer = new byte[4096];

    final FileInputStream fis = new FileInputStream(zipPath);
    final ZipInputStream zis = new ZipInputStream(fis);
    ZipEntry ze = zis.getNextEntry();
    while(ze != null){
      final File newFile = new File(destDir + File.separator +
                                            ze.getName());

      if (ze.isDirectory()) {
        if (getLog().isDebugEnabled()) {
          utils.info("Directory entry " + newFile.getAbsolutePath());
        }

        zis.closeEntry();
        ze = zis.getNextEntry();
        continue;
      }

      if (getLog().isDebugEnabled()) {
        utils.info("Unzip " + newFile.getAbsolutePath());
      }

      /* Zip entry has relative path which may require sub directories
       */
      //noinspection ResultOfMethodCallIgnored
      new File(newFile.getParent()).mkdirs();
      final FileOutputStream fos = new FileOutputStream(newFile);
      int len;
      while ((len = zis.read(buffer)) > 0) {
        fos.write(buffer, 0, len);
      }
      fos.close();
      //close this ZipEntry
      zis.closeEntry();
      ze = zis.getNextEntry();
    }

    //close last ZipEntry
    zis.closeEntry();
    zis.close();
    fis.close();
  }

  private void cleanOut(final String outDirPath) throws Throwable {
    final Path outPath = Paths.get(outDirPath);

    if (outPath.toFile().exists()) {
      utils.deleteAll(outPath);
    }

    if (utils.makeDir(outDirPath)) {
      utils.debug("created " + outDirPath);
    }
  }

  /** Return list of files in given directory that have the supplied
   * suffix and whose name part is in the supplied list. If no list
   * all files are returned.
   *
   * @param dirPath the directory
   * @param allowedNames names must be in this list
   * @return split names of located files.
   * @throws Throwable
   */
  private List<SplitName> getInFiles(final String dirPath,
                                     final List<String> allowedNames,
                                     final String suffix) throws Throwable {
    final File inDir = utils.directory(dirPath);

    final String[] names = inDir.list();

    if (names == null) {
      utils.info("No names found. Exiting");
      return null;
    }

    final List<SplitName> splitNames = new ArrayList<>();

    for (final String nm: names) {
      utils.debug("Name: " + nm);
      final SplitName sn = SplitName.testName(nm, allowedNames);

      // Allow for a generated ear without a suffix (maven plugin)
      utils.debug("Split name: " + sn);

      if ((sn == null) ||
              ((sn.getType() != null) && (!suffix.equals(
                      sn.getType())))) {
        continue;
      }

      splitNames.add(sn);
    }

    utils.info("Found " + splitNames.size() + " " + suffix +
                       "s");

    return splitNames;
  }

  private List<SplitName> getDeployedNames(final String dirPath,
                                           final String suffix) throws Throwable {
    final File outDir = utils.directory(dirPath);

    final String[] deployedNames = outDir.list();

    final List<SplitName> splitNames = new ArrayList<>();

    if (deployedNames == null) {
      return splitNames;
    }

    for (final String nm: deployedNames) {
      final SplitName sn = SplitName.testName(nm);

      if (sn == null) {
        //utils.warn("Unable to process " + nm);
        continue;
      }

      if ((suffix != null) && !suffix.equals(sn.getType())) {
        continue;
      }

      splitNames.add(sn);
    }

    return splitNames;
  }

  private String defaultVal(final String val,
                            final String pname) {
    if (val != null) {
      return val;
    }

    return pc.get(pname);
  }

  private String defaultVal(final String val,
                                   final String pname,
                                   final String argName) {
    final String nval = defaultVal(val, pname);
    if (nval != null) {
      return nval;
    }

    errorMsg = "Must specify " + argName +
                        " or provide the value in the properties with the" +
                        " '" + pname + "' property";
    utils.error(errorMsg);

    return null;
  }
}
