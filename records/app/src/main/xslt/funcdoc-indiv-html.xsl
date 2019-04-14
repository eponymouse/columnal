<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:param name="myOutputDir"/>
    <xsl:output method="html"/>
    
    <xsl:include href="funcdoc-shared.xsl"/>
    
    <xsl:template match="//functionDocumentation">
        <xsl:variable name="namespace" select="@namespace"/>
        <xsl:for-each select="function">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/function-{$namespace}-{@name}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="$namespace"/>/<xsl:value-of select="@id"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                        <link rel="stylesheet" href="web.css"/>
                    </head>
                    <body class="indiv">
                        <xsl:call-template name="processFunction">
                            <xsl:with-param name="function" select="."/>
                        </xsl:call-template>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="link">
        <xsl:choose>
            <xsl:when test="@function"><xsl:value-of select="@function"/>(..)</xsl:when>
            <xsl:when test="@namespace"><xsl:value-of select="@namespace"/></xsl:when>
            <xsl:when test="@type='typevar' or @type='unitvar'"><xsl:copy-of select="child::node()"/></xsl:when>
            <xsl:when test="@type='List'"><xsl:copy-of select="child::node()"/></xsl:when>
            <xsl:when test="@type"><xsl:value-of select="@type"/></xsl:when>
            <xsl:when test="@operator"><xsl:value-of select="@operator"/></xsl:when>
        </xsl:choose>
        <!--
        <xsl:choose>
            <xsl:when test="@function"><a class="internal-link" href="#function-{@function}"><xsl:value-of select="@function"/>(..)</a></xsl:when>
            <xsl:when test="@namespace"><a class="internal-link" href="#namespace-{@namespace}"><xsl:value-of select="@namespace"/></a></xsl:when>
            <xsl:when test="@type='typevar' or @type='List' or @type='unitvar'"><a class="internal-link" href="#type-{@type}"><xsl:copy-of select="child::node()"/></a></xsl:when>
            <xsl:when test="@type"><a class="internal-link" alt="Type {@type}" href="#type-{@type}"><xsl:value-of select="@type"/></a></xsl:when>
            <xsl:when test="@operator"><a class="internal-link" href="#operator-{string-join(string-to-codepoints(@operator), '-')}">operator <xsl:value-of select="@operator"/></a></xsl:when>
        </xsl:choose>
        -->
    </xsl:template>
</xsl:stylesheet>