<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:param name="myOutputDir"/>
    <xsl:output method="html"/>
    
    <xsl:include href="funcdoc-shared.xsl"/>
    
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
                            <link rel="stylesheet" href="web.css"/>
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
                        <link rel="stylesheet" href="web.css"/>
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