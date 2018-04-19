<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:param name="myOutputDir"/>
    <xsl:output method="html"/>
    <xsl:template name="processType">
        <xsl:param name="type" select="."/>
        <xsl:analyze-string select="$type" regex="\{{\*\}}">
            <xsl:matching-substring>
                <span class="wild-unit"><xsl:value-of select="."/></span>
            </xsl:matching-substring>
            <xsl:non-matching-substring>
                <xsl:value-of select="."/>
            </xsl:non-matching-substring>
        </xsl:analyze-string>
    </xsl:template>
    
    <xsl:template match="/functionDocumentation">
        <xsl:variable name="namespace" select="@namespace"/>
        <xsl:for-each select="functionGroup">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/group-{$namespace}-{@id}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="@title"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                    </head>
                    <body>
                        <div class="function-group-item">
                            <span class="function-group-title"><xsl:value-of select="@title"/></span>
                            <div class="description"><xsl:value-of select="description"/></div>
                        </div>
                        <xsl:for-each select="function">
                            <xsl:variable name="functionName" select="@name"/>
                            <div class="function-item" id="function-{@name}">
                                <span class="function-name-header"><xsl:value-of select="@name"/></span>
                                <span class="function-name-type"><xsl:value-of select="@name"/></span>
                                <span class="function-type"><!-- @any <xsl:value-of select="scope"/> -->(<xsl:call-template
                                        name="processType"><xsl:with-param name="type" select="argType"/></xsl:call-template>) <span class="function-arrow"/> <xsl:call-template
                                        name="processType"><xsl:with-param name="type" select="returnType"/></xsl:call-template>
                                </span>
                                <div class="description"><xsl:copy-of select="description"/></div>
                                <div class="examples">
                                    <span class="examples-header">Examples</span>
                                    <xsl:for-each select="example">
                                        <div class="example"><span class="example-call"><xsl:value-of select="$functionName"/>(<xsl:value-of select="input"/>) <span class="function-arrow"/> <xsl:value-of select="output"/></span></div>
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