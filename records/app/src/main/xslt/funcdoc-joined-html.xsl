<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:output method="html"/>
    
    <xsl:include href="funcdoc-shared.xsl"/>
    
    <xsl:template match="/">
        <html>
            <head>
                <title>Function Documentation</title>
                <link rel="stylesheet" href="funcdoc.css"/>
                <link rel="stylesheet" href="web.css"/>
            </head>
            <body>
                <xsl:for-each select=".//functionDocumentation">
                    <xsl:variable name="namespace" select="@namespace"/>
                    <div class="namespace">
                        <xsl:for-each select="functionGroup">
                            <xsl:variable name="groupName" select="@id"/>
                            <xsl:for-each select="function">
                                <div class="function-group-item">
                                    <span class="function-group-title"><xsl:value-of select="@title"/></span>
                                    <div class="description"><xsl:value-of select="description"/></div>
                                </div>
                                
                                <xsl:call-template name="processFunction">
                                    <xsl:with-param name="function" select="."/>
                                </xsl:call-template>
                            </xsl:for-each>
                        </xsl:for-each>
                
                        <!-- Functions without groups -->
                        <xsl:for-each select="function">
                            <xsl:call-template name="processFunction">
                                <xsl:with-param name="function" select="."/>
                            </xsl:call-template>
                        </xsl:for-each>
                    </div>
                    
                </xsl:for-each>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>