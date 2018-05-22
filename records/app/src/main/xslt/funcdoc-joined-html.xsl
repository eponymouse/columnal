<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:output method="html"/>
    
    <xsl:include href="funcdoc-shared.xsl"/>
    
    <xsl:template match="/all">
        <html>
            <head>
                <title>Function Documentation</title>
                <link rel="stylesheet" href="funcdoc.css"/>
                <link rel="stylesheet" href="web.css"/>
            </head>
            <body>
                <!-- Index -->
                <div class="index">
                    <xsl:for-each select=".//functionDocumentation">
                        <xsl:variable name="namespace" select="@namespace"/>
                        <div class="index-namespace">
                            <xsl:for-each select="function">
                                <a class="index-entry" href="#function-{@name}"><xsl:value-of select="@name"/></a>
                            </xsl:for-each>
                        </div>
                    </xsl:for-each>
                </div>
                
                <!-- Body -->
                <xsl:for-each select=".//functionDocumentation">
                    <xsl:variable name="namespace" select="@namespace"/>
                    <div class="namespace">
                        <xsl:for-each select="function">
                            <xsl:call-template name="processFunction">
                                <xsl:with-param name="function" select="."/>
                            </xsl:call-template>
                        </xsl:for-each>
                    </div>
                </xsl:for-each>
                
                <xsl:for-each select=".//binaryOperator">
                    <xsl:call-template name="processOperator">
                        <xsl:with-param name="operator" select="."/>
                    </xsl:call-template>
                </xsl:for-each>
                <xsl:for-each select=".//naryOperatorGroup">
                    <xsl:call-template name="processOperator">
                        <xsl:with-param name="operator" select="."/>
                    </xsl:call-template>
                </xsl:for-each>

                <xsl:for-each select=".//type">
                    <xsl:call-template name="processTypeDef">
                        <xsl:with-param name="type" select="."/>
                    </xsl:call-template>
                </xsl:for-each>
            </body>
        </html>
    </xsl:template>
    
    <xsl:template match="link">
        <xsl:choose>
            <xsl:when test="@function"><a class="internal-link" href="#function-{@function}"><xsl:value-of select="@function"/>(..)</a></xsl:when>
            <xsl:when test="@type='typevar' or @type='List' or @type='unitvar'"><a class="internal-link" href="#type-{@type}"><xsl:copy-of select="child::node()"/></a></xsl:when>
            <xsl:when test="@type"><a class="internal-link" alt="Type {@type}" href="#type-{@type}"><xsl:value-of select="@type"/></a></xsl:when>
            <xsl:when test="@operator"><a class="internal-link" href="#operator-{string-join(string-to-codepoints(@operator), '-')}">operator <xsl:value-of select="@operator"/></a></xsl:when>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>