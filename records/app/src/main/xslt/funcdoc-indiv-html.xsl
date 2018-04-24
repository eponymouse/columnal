<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:param name="myOutputDir"/>
    <xsl:output method="html"/>
    
    <xsl:template name="processType">
        <xsl:param name="type" select="."/>
        <xsl:analyze-string select="$type" regex="\{{\*\}}">
            <xsl:matching-substring>
                <span class="wild-unit"><xsl:copy-of select="."/></span>
            </xsl:matching-substring>
            <!-- All words beginning with lower-case are assumed to be vars: -->
            <xsl:non-matching-substring>
                <xsl:analyze-string select="." regex="[a-z][a-zA-Z]*">
                    <xsl:matching-substring>
                        <span class="type-var"><xsl:copy-of select="."/></span>
                    </xsl:matching-substring>
                    <xsl:non-matching-substring>
                        <xsl:copy-of select="."/>
                    </xsl:non-matching-substring>
                </xsl:analyze-string>
            </xsl:non-matching-substring>
        </xsl:analyze-string>
    </xsl:template>
    
    <xsl:template name="bracketed">
        <xsl:param name="expression" select="."/>
        <xsl:analyze-string select="$expression" regex="^\(.*\)$">
            <xsl:matching-substring><xsl:value-of select="."/></xsl:matching-substring>
            <xsl:non-matching-substring>(<xsl:value-of select="."/>)</xsl:non-matching-substring>
        </xsl:analyze-string>
    </xsl:template>
    <xsl:template name="processFunction">
        <xsl:param name="function" select="."/>

        <xsl:variable name="functionName" select="@name"/>
        <div class="function-item" id="function-{@name}">
            <span class="function-name-header"><xsl:value-of select="@name"/></span>
            <span class="function-name-type"><xsl:value-of select="@name"/></span>
            <span class="function-type"><!-- @any <xsl:value-of select="scope"/> --><xsl:call-template
                    name="processType"><xsl:with-param name="type"><xsl:call-template name="bracketed"><xsl:with-param name="expression" select="argType"/></xsl:call-template></xsl:with-param></xsl:call-template> <span class="function-arrow"/> <xsl:call-template
                    name="processType"><xsl:with-param name="type" select="returnType"/></xsl:call-template>
            </span>
            <div class="description"><xsl:copy-of select="description"/></div>
            <div class="examples">
                <span class="examples-header">Examples</span>
                <xsl:for-each select="example">
                    <div class="example"><span class="example-call"><xsl:value-of select="$functionName"/><xsl:call-template
                            name="bracketed"><xsl:with-param name="expression" select="input"/></xsl:call-template> <span class="function-arrow"/> <xsl:value-of select="output"/></span></div>
                </xsl:for-each>
            </div>
        </div>
    </xsl:template>
    
    <xsl:template match="/functionDocumentation">
        <xsl:variable name="namespace" select="@namespace"/>
        <xsl:for-each select="functionGroup">
            <xsl:variable name="groupName" select="@id"/>
            <xsl:for-each select="function">
                <xsl:result-document method="html" href="file:///{$myOutputDir}/function-{$namespace}-{@name}.html">
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
                            
                            <xsl:call-template name="processFunction">
                                <xsl:with-param name="function" select="."/>
                            </xsl:call-template>
                        </body>
                    </html>
                </xsl:result-document>
            </xsl:for-each>
        </xsl:for-each>

        <!-- Functions without groups -->
        <xsl:for-each select="function">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/function-{$namespace}-{@name}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="$namespace"/>/<xsl:value-of select="@id"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                    </head>
                    <body>
                        <xsl:call-template name="processFunction">
                            <xsl:with-param name="function" select="."/>
                        </xsl:call-template>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
    </xsl:template>
</xsl:stylesheet>