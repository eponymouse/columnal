/**
 * This is a simplified and patched (to allow XInclude) fork
 * of https://github.com/eerohele/saxon-gradle 
 * 
 * This is the original license (which was shipped without year or name):
 * 
 MIT License

 Copyright (c) [year] [fullname]

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.file.FileCollection
import org.gradle.api.InvalidUserDataException

import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject
import java.nio.file.Path
import java.util.stream.Collectors

@SuppressWarnings('MethodCount')
class SaxonXsltTask extends DefaultTask {
    protected static final String PERIOD = '.'
    protected static final String XSLT_NAMESPACE = 'http://www.w3.org/1999/XSL/Transform'
    
    protected final Map<String, Object> options = [xi: 'on', output: project.buildDir]

    protected final Map<String, Object> pluginOptions = [outputDirectoryLayout: 'flat']
    
    @Classpath
    final ConfigurableFileCollection classpath

    protected Map<String, String> stylesheetParams = [:]

    private final XmlSlurper xmlSlurper = new XmlSlurper()
    private GPathResult xslt

    // Saxon command-line options take 'on' and 'off', but it's best to let the
    // users use booleans as well.
    //
    // Example:
    //
    // xslt {
    //    dtd: true
    // }
    protected static final Map<String, String> ON_OFF = [
            'true' : 'on',
            'false': 'off'
    ]

    private final WorkerExecutor workerExecutor

    @Inject
    SaxonXsltTask(WorkerExecutor workerExecutor) {
        super()
        this.workerExecutor = workerExecutor
        this.classpath = project.objects.fileCollection()
        xmlSlurper.setFeature('http://apache.org/xml/features/disallow-doctype-decl', false)
        xmlSlurper.setFeature('http://apache.org/xml/features/nonvalidating/load-external-dtd', false)
    }

    // START COMMON OPTIONS
    void input(Object input) {
        this.options.input = input
    }
    
    void output(Object output) {
        this.options.output = project.file(output)
    }

    @SuppressWarnings('CatchException')
    void stylesheet(Object stylesheet) {
        this.options.xsl = project.file(stylesheet)
        if (this.options.xsl.exists()) {
            try {
                this.xslt = this.xmlSlurper
                        .parse(stylesheet)
                        .declareNamespace(xsl: XSLT_NAMESPACE)
            } catch (Exception ex) {
                logger.warn("Failed to parse: ${this.options.xsl}")
                logger.warn("  ${ex.getMessage()}")
            }
        }
    }

    void outputFileExtension(String extension) {
        this.pluginOptions.outputFileExtension = extension
    }

    void outputDirectoryLayout(String layout) {
        this.pluginOptions.outputDirectoryLayout = layout
    }
    
    void parameters(Map<String, String> parameters) {
        this.stylesheetParams = parameters
    }

    @Internal
    protected String getDefaultOutputExtension() {
        if (this.pluginOptions.outputFileExtension != null) {
            return this.pluginOptions.outputFileExtension
        }

        // Read output file extension from the <xsl:output> element of the
        // stylesheet.
        String method = this.xslt.output.@method
        return method ? method : 'xml'
    }

    protected URI resolveUri(String path) {
        return new URI(path)
    }

    @SuppressWarnings('CatchException')
    protected FileCollection getIncludedStylesheets(File stylesheet) {
        def newCollection = []

        if (stylesheet != null) {
            newCollection += stylesheet

            try {
                GPathResult xslt = this.xmlSlurper.parse(stylesheet).declareNamespace(xsl: XSLT_NAMESPACE)

                (xslt.include + xslt.import).each { elem ->
                    URI href = resolveUri(elem.@href[0].toString())
                    URI uri = stylesheet.toURI().resolve(href)
                    if (uri.getScheme() == "file") {
                        newCollection += getIncludedStylesheets(new File(uri))
                    }
                }
            } catch (FileNotFoundException ex) {
                // nevermind
            } catch (Exception ex) {
                logger.warn("Failed to parse: ${stylesheet}")
                logger.warn("  ${ex.getMessage()}")
            }

            return project.files(newCollection)
        }
    }

    String getOutputFileName(File file) {
        String name = file.getName()
        String basename = name.substring(0, name.lastIndexOf(PERIOD))
        String extension = getDefaultOutputExtension()
        [basename, extension].join(PERIOD)
    }

    // Get the default output file name.
    //
    // The default output file name is the basename of the input file followed
    // by the extension extracted from the <xsl:output> element of the input
    // stylesheet.
    //
    // Would love to use commons-io for this, but I don't really want to because
    // adding into the plugin classpath causes clashes with the Gradle runtime
    // classpath.
    protected File getOutputFile(File file) {
        Path filePath = file.toPath()
        Path rootPath = project.rootDir.toPath()
        Path outputPath = project.file(this.options.output).toPath()

        if (this.pluginOptions.outputDirectoryLayout == 'nested' && filePath.startsWith(rootPath) && outputPath.startsWith(rootPath)) {
            Path dir = rootPath.relativize(filePath).parent
            outputPath.resolve(dir).resolve(getOutputFileName(file)).toFile()

            // Using outputDirectoryLayout == 'nested' in scenarios where the
            // input or output path is *not* under the project base directory is
            // currently undefined behavior. I currently don't have the time to
            // enumerate all the edge cases, of which there are numerous.
            //
            // Similarly, this approach likely won't work with symlinks, because
            // to resolve symlinks, we would need to call .toRealPath(), but
            // that presumes that the target file or directory exists, which
            // is often not necessarily the case with this plugin. Another thing
            // I don't have the time to figure out.
            //
            // Anyway, this approach likely covers a large chunk of how people
            // would conceivably use this feature anyway.
        } else if (project.files(this.options.input).size() == 1 && project.buildDir.getAbsolutePath() != this.options.output.getAbsolutePath()) {
            this.options.output
        } else {
            new File(this.options.output, getOutputFileName(file))
        }
    }

    @OutputFiles
    FileCollection getOutputFiles() {
        if (this.options.input != null) {
            project.files(project.files(this.options.input).collect { getOutputFile(it) })
        } else {
            project.files(this.options.output)
        }
    }

    @InputFiles
    @SkipWhenEmpty
    FileCollection getInputFiles() {
        FileCollection stylesheets = project.files() + getIncludedStylesheets(this.options.xsl)

        if (this.options.input != null) {
            project.files(this.options.input) + stylesheets
        } else {
            stylesheets
        }
    }

    // Turn a key-value pair into a Saxon command line argument.
    //
    // Examples:
    //
    //   makeSaxonArgument('-', 'foo', 'bar')
    //   ===> '-foo:bar'
    //
    //   makeSaxonArgument('-', 'dtd', true)
    //   ===> '-dtd:on'
    protected static String makeSaxonArgument(prefix, key, value) {
        [prefix + key, ON_OFF.get(value.toString(), value)].join(':')
    }

    protected static String makeSingleHyphenArgument(key, value) {
        makeSaxonArgument('-', key, value)
    }

    protected static String makeDoubleHyphenArgument(key, value) {
        makeSaxonArgument('--', key, value)
    }

    // Convert the stylesheet parameters supplied by the user to KEY=VALUE
    // pairs, which is what Saxon understands.
    //
    // TODO: Investigate how to improve support for XPath data types.
    @Internal
    protected List<String> getStylesheetParameters() {
        this.stylesheetParams.collect { name, value ->
            [name, value].join('=')
        }.asImmutable()
    }

    // Set options common to all transformations: everything except input
    // and output.
    @Internal
    protected List<String> getCommonArguments() {
        Map<String, String> commonOptions = this.options.findAll { name, value ->
            !['input', 'output'].contains(name)
        }.asImmutable()
        
        return commonOptions.entrySet().stream().map(e -> makeSingleHyphenArgument(e.getKey(), e.getValue())).collect(Collectors.toList())
    }

    protected List<String> getFileSpecificArguments(File file) {
        String inputPath = file.getPath()
        String outputPath = getOutputFile(file).getPath()

        [makeSingleHyphenArgument('s', inputPath),
         makeSingleHyphenArgument('o', outputPath)].asImmutable()
    }

    @TaskAction
    void run() {
        WorkQueue workQueue = workerExecutor.classLoaderIsolation() {
            it.getClasspath().from(this.classpath);
        };

        List<String> commonArguments = getCommonArguments() + getStylesheetParameters()

        if (this.options.input != null) {
            project.files(this.options.input).each {
                List<String> arguments = getFileSpecificArguments(it) + commonArguments

                workQueue.submit(XsltTransformation) {
                    it.arguments.set(arguments)
                }
            }
        } else {
            String output = this.options.output.getPath()
            List<String> arguments = commonArguments + [makeSingleHyphenArgument('o', output)]

            workQueue.submit(XsltTransformation) {
                it.arguments.set(arguments)
            }
        }
    }
}
