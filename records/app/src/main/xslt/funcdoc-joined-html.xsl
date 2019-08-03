<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:output method="html"/>
    
    <xsl:include href="funcdoc-shared.xsl"/>
    
    <xsl:template match="//all|//functionDocumentation">
        <html>
            <head>
                <title>Function Documentation</title>
                <link rel="stylesheet" href="funcdoc.css"/>
                <link rel="stylesheet" href="web.css"/>
            </head>
            <body>
                <!-- Index -->
                <div class="index">
                    <xsl:for-each select=".//functionDocumentation|//functionDocumentation">
                        <xsl:variable name="namespace" select="@namespace"/>
                        <div class="index-namespace">
                            <xsl:for-each select="function">
                                <a class="index-entry" href="#function-{@name}"><xsl:value-of select="@name"/></a>
                            </xsl:for-each>
                        </div>
                    </xsl:for-each>
                </div>
                
                <!-- Body -->
                <xsl:for-each select=".//functionDocumentation|//functionDocumentation">
                    <xsl:variable name="namespace" select="@namespace"/>
                    <div class="namespace" id="namespace-{@namespace}">
                        <xsl:apply-templates select="function"/>
                    </div>
                    <xsl:apply-templates select="binaryOperator|naryOperatorGroup|type"/>
                </xsl:for-each>
            </body>
        </html>
    </xsl:template>
    
    <xsl:template match="link">
        <xsl:choose>
            <xsl:when test="@function"><a class="internal-link" href="#function-{@function}"><xsl:value-of select="@function"/>(..)</a></xsl:when>
            <xsl:when test="@namespace"><a class="internal-link" href="#namespace-{@namespace}"><xsl:value-of select="@namespace"/></a></xsl:when>
            <xsl:when test="@type='typevar' or @type='List' or @type='unitvar'"><a class="internal-link" href="#type-{@type}"><xsl:copy-of select="child::node()"/></a></xsl:when>
            <xsl:when test="@type"><a class="internal-link" alt="Type {@type}" href="#type-{@type}"><xsl:value-of select="@type"/></a></xsl:when>
            <xsl:when test="@operator"><a class="internal-link" href="#operator-{string-join(string-to-codepoints(@operator), '-')}">operator <xsl:value-of select="@operator"/></a></xsl:when>
            <xsl:when test="@syntax"><a class="internal-link" href="#syntax-{@syntax}"><xsl:value-of select="@syntax"/></a></xsl:when>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>
