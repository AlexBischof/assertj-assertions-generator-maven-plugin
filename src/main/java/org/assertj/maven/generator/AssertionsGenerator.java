package org.assertj.maven.generator;

import static com.google.common.collect.Sets.newLinkedHashSet;
import static org.apache.commons.collections.CollectionUtils.subtract;
import static org.apache.commons.lang3.ArrayUtils.addAll;
import static org.assertj.assertions.generator.util.ClassUtil.collectClasses;
import static org.assertj.core.util.Arrays.isNullOrEmpty;
import static org.assertj.core.util.Sets.newHashSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.logging.Log;
import org.assertj.assertions.generator.AssertionsEntryPointType;
import org.assertj.assertions.generator.BaseAssertionGenerator;
import org.assertj.assertions.generator.Template;
import org.assertj.assertions.generator.description.ClassDescription;
import org.assertj.assertions.generator.description.converter.ClassToClassDescriptionConverter;
import org.assertj.core.util.VisibleForTesting;

/**
 * Is able to generate AssertJ assertions classes from packages.
 */
public class AssertionsGenerator {

  private static final Pattern INCLUDE_EVERYTHING = Pattern.compile(".*");
  private ClassToClassDescriptionConverter converter;
  private ClassLoader classLoader;
  private BaseAssertionGenerator generator;
  private Pattern[] includePatterns;
  private Pattern[] excludePatterns;
  private Log log;
  private Set<AssertionsEntryPointType> assertionsEntryPointToGenerate;

  public AssertionsGenerator(ClassLoader classLoader) throws FileNotFoundException, IOException {
	this.generator = new BaseAssertionGenerator();
	this.converter = new ClassToClassDescriptionConverter();
	this.classLoader = classLoader;
	this.includePatterns = new Pattern[] { INCLUDE_EVERYTHING };
	this.excludePatterns = new Pattern[0];
	this.assertionsEntryPointToGenerate = newHashSet();
  }

  public void setIncludePatterns(String[] includeRegexs) {
	if (isNullOrEmpty(includeRegexs)) {
	  includePatterns = new Pattern[] { INCLUDE_EVERYTHING };
	  return;
	}
	includePatterns = new Pattern[includeRegexs.length];
	for (int i = 0; i < includeRegexs.length; i++) {
	  includePatterns[i] = Pattern.compile(includeRegexs[i]);
	}
  }

  public void setExcludePatterns(String[] excludeRegexs) {
	if (isNullOrEmpty(excludeRegexs)) {
	  return;
	}
	excludePatterns = new Pattern[excludeRegexs.length];
	for (int i = 0; i < excludeRegexs.length; i++) {
	  excludePatterns[i] = Pattern.compile(excludeRegexs[i]);
	}
  }

  /**
   * Generates custom assertions for classes in given packages with the Assertions class entry point in given
   * destination dir.
   * 
   * @param inputPackages the packages containing the classes we want to generate Assert classes for.
   * @param inputClassNames the packages containing the classes we want to generate Assert classes for.
   * @param destDir the base directory where the classes are going to be generated.
   * @param entryPointFilePackage the package of the assertions entry point class, may be <code>null</code>.
   * @param templates
   * @throws IOException if the files can't be generated
   */
  @SuppressWarnings("unchecked")
  public AssertionsGeneratorReport generateAssertionsFor(String[] inputPackages, String[] inputClassNames,
                                                         String destDir,
                                                         String entryPointFilePackage, boolean hierarchical,
                                                         Map<String, String> templates) {
	generator.setDirectoryWhereAssertionFilesAreGenerated(destDir);

      AssertionsGeneratorReport report = new AssertionsGeneratorReport();


      //Register templates
      if (MapUtils.isNotEmpty(templates)) {
          report.setTemplates(templates);
          for (Map.Entry<String, String> entry : templates.entrySet()) {

              String pathToFile = entry.getValue();
              File templateFile = new File(pathToFile);
              try {
                  String fileContent = FileUtils.readFileToString(templateFile);
                  generator.register(new Template(Template.Type.valueOf(entry.getKey().toUpperCase()), fileContent));
              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
      }

	Set<ClassDescription> classDescriptions = new HashSet<ClassDescription>();

	report.setInputPackages(inputPackages);
	report.setInputClasses(inputClassNames);
	try {
	  Set<Class<?>> classes = collectClasses(classLoader, addAll(inputPackages, inputClassNames));
	  report.reportInputClassesNotFound(classes, inputClassNames);
	  Set<Class<?>> filteredClasses = removeAssertClasses(classes);
	  removeClassesAccordingToIncludeAndExcludePatterns(filteredClasses);
	  report.setExcludedClassesFromAssertionGeneration(subtract(classes, filteredClasses));
	  report.setDirectoryPathWhereAssertionFilesAreGenerated(destDir);
	  if (hierarchical) {
		for (Class<?> clazz : filteredClasses) {
		  ClassDescription classDescription = converter.convertToClassDescription(clazz);
		  File[] generatedCustomAssertionFiles = generator.generateHierarchicalCustomAssertionFor(classDescription,
			                                                                                      filteredClasses);
		  report.addGeneratedAssertionFile(generatedCustomAssertionFiles[0]);
		  report.addGeneratedAssertionFile(generatedCustomAssertionFiles[1]);
		  classDescriptions.add(classDescription);
		}
	  } else {
		for (Class<?> clazz : filteredClasses) {
		  ClassDescription classDescription = converter.convertToClassDescription(clazz);
		  File generatedCustomAssertionFile = generator.generateCustomAssertionFor(classDescription);
		  report.addGeneratedAssertionFile(generatedCustomAssertionFile);
		  classDescriptions.add(classDescription);
		}
	  }

	  for (AssertionsEntryPointType assertionsEntryPointType : assertionsEntryPointToGenerate) {
		File assertionsEntryPointFile = generator.generateAssertionsEntryPointClassFor(classDescriptions,
		                                                                               assertionsEntryPointType,
		                                                                               entryPointFilePackage);
		report.reportEntryPointGeneration(assertionsEntryPointType, assertionsEntryPointFile);
	  }
	} catch (Exception e) {
	  report.setException(e);
	}
	return report;
  }

  private void removeClassesAccordingToIncludeAndExcludePatterns(Set<Class<?>> filteredClasses) {
	for (Iterator<Class<?>> it = filteredClasses.iterator(); it.hasNext();) {
	  Class<?> element = it.next();
	  if (!isIncluded(element) || isExcluded(element)) it.remove();
	}
  }

  private boolean isIncluded(Class<?> element) {
	String className = element.getName();
	for (Pattern includePattern : includePatterns) {
	  if (includePattern.matcher(className).matches()) return true;
	}
	log.debug("Won't generate assertions for " + className + " as it does not match any include regex.");
	return false;
  }

  private boolean isExcluded(Class<?> element) {
	String className = element.getName();
	for (Pattern excludePattern : excludePatterns) {
	  if (excludePattern.matcher(className).matches()) {
		log.debug("Won't generate assertions for " + className + " as it matches exclude regex : " + excludePattern);
		return true;
	  }
	}
	return false;
  }

  private Set<Class<?>> removeAssertClasses(Set<Class<?>> classList) {
	Set<Class<?>> filteredClassList = newLinkedHashSet();
	for (Class<?> clazz : classList) {
	  String classSimpleName = clazz.getSimpleName();
	  if (!classSimpleName.endsWith("Assert") && !classSimpleName.endsWith("Assertions")) {
		filteredClassList.add(clazz);
	  }
	}
	return filteredClassList;
  }

  @VisibleForTesting
  public void setBaseGenerator(BaseAssertionGenerator generator) {
	this.generator = generator;
  }

  public void setLog(Log log) {
	this.log = log;
  }

  public void enableEntryPointClassesGenerationFor(AssertionsEntryPointType type) {
	this.assertionsEntryPointToGenerate.add(type);
  }

}
