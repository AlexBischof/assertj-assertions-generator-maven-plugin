package org.assertj.maven;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import org.assertj.maven.generator.AssertionsGenerator;


/**
 * Generates custom AssertJ assertions files for provided packages
 * 
 * @goal generate-assertions
 * @phase generate-test-sources
 * @requiresDependencyResolution compile+runtime
 */
public class AssertJAssertionsGeneratorMojo extends AbstractMojo {

  /**
   * Current maven project
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  public MavenProject project;

  /**
   * Destination dir to store generated assertion source files. Defaults to
   * 'target/generated-test-sources/assertj-assertions'.<br>
   * Your IDE should be able to pick up files from this location as sources automatically when generated.
   * 
   * @parameter default-value="${project.build.directory}/generated-test-sources/assertj-assertions"
   */
  public String targetDir;

  /**
   * List of packages to generate assertions for. Currently only packages are supported.
   * 
   * @parameter
   */
  public String[] packages;

  public void execute() throws MojoExecutionException {
    try {
      logExecutionStart();
      String assertionsEntryPointPathname = newAssertionGenerator().generateAssertionSources(packages, targetDir);
      logExecutionEnd(assertionsEntryPointPathname);
      project.addTestCompileSourceRoot(targetDir);
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage());
    }
  }

  private void logExecutionStart() {
    getLog().info("About to generate AssertJ assertions for classes in following packages and subpackages : ");
    for (String pack : packages) {
      getLog().info("- " + pack);
    }
  }
  
  private void logExecutionEnd(String assertionsEntryPointPathname) {
    getLog().info(" ");
    getLog().info("AssertJ assertions classes have been generated in: " + targetDir);
    getLog().info("Custom assertions entry point class has been generated in: " + assertionsEntryPointPathname);
  }

  private AssertionsGenerator newAssertionGenerator() throws Exception {
    return new AssertionsGenerator(getProjectClassLoader());
  }

  private ClassLoader getProjectClassLoader() throws DependencyResolutionRequiredException, MalformedURLException {
    @SuppressWarnings("unchecked")
    List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
    URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];
    for (int i = 0; i < runtimeClasspathElements.size(); i++) {
      runtimeUrls[i] = new File(runtimeClasspathElements.get(i)).toURI().toURL();
    }
    return new URLClassLoader(runtimeUrls, Thread.currentThread().getContextClassLoader());
  }

}
