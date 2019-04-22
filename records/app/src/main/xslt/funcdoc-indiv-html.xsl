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
        <xsl:for-each select="syntax">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/syntax-{@id}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="@id"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                        <link rel="stylesheet" href="web.css"/>
                    </head>
                    <body class="indiv">
                        <xsl:call-template name="processSyntax">
                            <xsl:with-param name="syntax" select="."/>
                        </xsl:call-template>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
        <xsl:for-each select="binaryOperator">
            <xsl:variable name="operator" select="operator"/>
            <xsl:result-document method="html" href="file:///{$myOutputDir}/operator-{string-join(string-to-codepoints($operator), '-')}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="$operator"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                        <link rel="stylesheet" href="web.css"/>
                    </head>
                    <body class="indiv">
                        <xsl:call-template name="processOperator">
                            <xsl:with-param name="operator" select="."/>
                        </xsl:call-template>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
        <xsl:for-each select="naryOperatorGroup">
            <xsl:variable name="group" select="."/>
            <xsl:for-each select="operator">
                <xsl:variable name="operator" select="."/>
                <xsl:result-document method="html" href="file:///{$myOutputDir}/operator-{string-join(string-to-codepoints($operator), '-')}.html">
                    <html>
                        <head>
                            <title><xsl:value-of select="$operator"/></title>
                            <link rel="stylesheet" href="funcdoc.css"/>
                            <link rel="stylesheet" href="web.css"/>
                        </head>
                        <body class="indiv">
                            <xsl:call-template name="processOperator">
                                <xsl:with-param name="operator" select="$group"/>
                            </xsl:call-template>
                        </body>
                    </html>
                </xsl:result-document>
            </xsl:for-each>
        </xsl:for-each>
        <xsl:for-each select="type">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/type-{@name}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="@name"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                        <link rel="stylesheet" href="web.css"/>
                    </head>
                    <body class="indiv">
                        <xsl:call-template name="processTypeDef">
                            <xsl:with-param name="type" select="."/>
                        </xsl:call-template>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="link">
        <xsl:choose>
            <xsl:when test="@function"><a class="internal-link" href="function-{@namespace}-{@function}.html"><xsl:value-of select="@function"/>(..)</a></xsl:when>
            <xsl:when test="@namespace"></xsl:when>
            <xsl:when test="@type='typevar' or @type='List' or @type='unitvar'"><a class="internal-link" href="type-{@type}.html"><xsl:copy-of select="child::node()"/></a></xsl:when>
            <xsl:when test="@type"><a class="internal-link" alt="Type {@type}" href="type-{@type}.html"><xsl:value-of select="@type"/></a></xsl:when>
            <xsl:when test="@operator"><a class="internal-link" href="operator-{string-join(string-to-codepoints(@operator), '-')}.html">operator <xsl:value-of select="@operator"/></a></xsl:when>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>