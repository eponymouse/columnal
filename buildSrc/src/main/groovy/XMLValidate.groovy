import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator

// Largely taken from https://stackoverflow.com/a/53238842 plus
// a couple of extra lines to make it XInclude and Namespace aware
class XmlValidate extends DefaultTask {
    @InputFiles
    private FileCollection xmlFiles

    @InputFile
    File xsd

    FileCollection getXmlFiles() {
        return xmlFiles
    }

    File getXsd() {
        return xsd
    }

    void xml(Object files) {
        FileCollection fc = project.files(files)
        this.xmlFiles = this.xmlFiles == null ?  fc : this.xmlFiles.add(fc)
    }

    @TaskAction
    public void validateXml() {
        def documentBuilderFactory = DocumentBuilderFactory.newInstance()
        documentBuilderFactory.setNamespaceAware(true)
        documentBuilderFactory.setXIncludeAware(true)
        DocumentBuilder parser = documentBuilderFactory.newDocumentBuilder()
        Validator validator = null
        if (xsd != null) {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            Schema schema = factory.newSchema(new StreamSource(xsd))
            validator = schema.newValidator()
        }
        Set<File> failures = [] as Set
        xmlFiles.forEach {
            Document document = null
            try {
                document = parser.parse(it)
            } catch (Exception e) {
                logger.error("Error parsing $it", e)
                failures << it
            }
            if (document && validator) {
                try {
                    validator.validate(new DOMSource(document))
                } catch (Exception e) {
                    logger.error("Error validating $it", e)
                    failures << it
                }
            }
        }
        if (failures) throw new BuildCancelledException("xml validation failures $failures")
    }
}
