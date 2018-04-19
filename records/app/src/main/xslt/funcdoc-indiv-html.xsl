<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:param name="myOutputDir"/>
    <xsl:output method="html"/>
    <xsl:template match="/functionDocumentation">
        <xsl:for-each select="functionGroup">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/group-{@id}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="@title"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                    </head>
                    <body>
                        <div class="function-group-item">
                            <div class="description"><xsl:value-of select="description"/></div>
                        </div>
                        <xsl:for-each select="function">
                            <xsl:variable name="functionName" select="@name"/>
                            <div class="function-item" id="function-@name">
                                <span class="function-name-header"><xsl:value-of select="@name"/></span>
                                <span class="function-name-type"><xsl:value-of select="@name"/></span><span class="function-type">
                                    @any <xsl:value-of select="scope"/>
                                    (<xsl:value-of select="argType"/>) -&gt; <xsl:value-of select="returnType"/>
                                </span>
                                <div class="description"><xsl:copy-of select="description"/></div>
                                <div class="examples">
                                    <span class="examples-header">Examples</span>
                                    <xsl:for-each select="example">
                                        <div class="example"><span class="example-call"><xsl:value-of select="$functionName"/>(<xsl:value-of select="input"/>) ---&gt; <xsl:value-of select="output"/></span></div>
                                    </xsl:for-each>
                                </div>
                            </div>
                        </xsl:for-each>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
    </xsl:template>
</xsl:stylesheet>