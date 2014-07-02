package ru.waveaccess.gradlemaven

import org.apache.commons.lang3.SystemUtils
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.FileSystemManager
import org.apache.commons.vfs2.Selectors
import org.apache.commons.vfs2.VFS
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * @author Alexander Bondarev
 * @since July 2, 2014
 */
class MavenPlugin implements Plugin<Project> {
  
  private void addTask(Project project, String target) {
    Task task = project.task("mvn-${target}") << {
      project.mavenWrapper.configure()
      String mavenFolder = makeMavenPath(project.mavenWrapper.version)
      String extension = SystemUtils.IS_OS_WINDOWS ? '.bat' : ''
      List<String> args = collectArguments(project)
      List<String> systemProperties = collectSystemProperties(project)
      List<String> command = [[mavenFolder, 'bin', "mvn${extension}"].join(File.separator)]
      command.addAll(systemProperties.collect { "-D${it}" })
      command.add(target)
      command.addAll(args)
      Map<String, String> env = new HashMap(System.getenv())
      env['M2_HOME'] = mavenFolder
      project.exec {
        commandLine command
        environment env
        standardInput = System.in
      }
    }
    task.configure {
      group 'Maven Wrapper'
      description "Run Maven ${target}"
    }
    task.dependsOn('install-maven')
  }
  
  private List<String> collectArguments(Project project) {
    collectProperties(project, 'arg')
  }
  
  private List<String> collectProperties(Project project, String prefix) {
    List<String> result = []
    int index = 0
    try {
      while (true) {
        result << project."${prefix}${index++}"
      }
    }
    catch (MissingPropertyException ex) {
    }
    return result
  }
  
  private List<String> collectSystemProperties(Project project) {
    collectProperties(project, 'd')
  }
  
  @Override
  public void apply(Project project) {
    configure(project)
    project.task('install-maven') << {
      project.mavenWrapper.configure()
      download(project.mavenWrapper.version)
    }
    [
      'install',
    ].each { addTask(project, it) }
  }
  
  private void configure(Project project) {
    project.extensions.create('mavenWrapper', Maven)
  }
  
  private String download(String version) {
    FileSystemManager manager = VFS.manager
    String homePath = [SystemUtils.userHome.path, '.gradlemaven'].join(File.separator)
    String destinationPath = [homePath, 'maven', version].join(File.separator)
    FileObject destination = manager.resolveFile(destinationPath)
    if (destination.exists()) {
      return destination.name.path
    }
    File zipFile = downloadZip(homePath, version)
    FileObject zip = manager.resolveFile("zip:file://${zipFile.path}")
    unzipMaven(homePath, zip, destinationPath)
    makeMavenExecutable(destination.name.path)
    return destination.name.path
  }
  
  private File downloadZip(String homePath, String version) {
    println "Downloading Maven ${version}"
    File zipFile = new File([homePath, 'archive', "${version}.zip"].join(File.separator))
    if (!zipFile.parentFile.exists() && !zipFile.parentFile.mkdirs()) {
      throw new IllegalStateException("Could not create ${zipFile.path}")
    }
    OutputStream file = new FileOutputStream(zipFile)
    OutputStream out = new BufferedOutputStream(file)
    out << new URL(
        "http://apache-mirror.rbc.ru/pub/apache/maven/maven-3/$version/binaries/apache-maven-$version-bin.zip").
        openStream()
    out.close()
    return zipFile
  }
  
  private void makeMavenExecutable(String path) {
    if (SystemUtils.IS_OS_WINDOWS) {
      return
    }
    File file = new File([path, 'bin', 'mvn'].join(File.separator))
    file.executable = true
  }
  
  private String makeMavenPath(String version) {
    [SystemUtils.userHome.path, '.gradlemaven', 'maven', version].join(File.separator)
  }
  
  private void unzipMaven(String homePath, FileObject zip, String destinationPath) {
    FileSystemManager manager = VFS.manager
    FileObject temporary = manager.resolveFile([homePath, 'tmp'].join(File.separator))
    temporary.copyFrom(zip, Selectors.SELECT_ALL)
    FileObject mavenHome = temporary
    while (!mavenHome.children.any { it.name.baseName == 'bin' }) {
      mavenHome = mavenHome.children[0]
    }
    for (child in mavenHome.children) {
      manager.resolveFile([destinationPath, child.name.baseName].join(File.separator)).copyFrom(child, Selectors.SELECT_ALL)
    }
    temporary.delete(Selectors.SELECT_ALL)
  }
}
