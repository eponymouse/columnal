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
                        <title><xsl:value-of select="$namespace"/>/<xsl:value-of select="@name"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                        <link rel="stylesheet" href="web.css"/>
                    </head>
                    <body class="indiv">
                        <xsl:apply-templates select="."/>
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
                        <xsl:apply-templates select="."/>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
        <xsl:for-each select="variable">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/variable-{replace(@name, ' ', '-')}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="@id"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                        <link rel="stylesheet" href="web.css"/>
                    </head>
                    <body class="indiv">
                        <xsl:apply-templates select="."/>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
        <xsl:for-each select="literal">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/literal-{@name}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="@name"/>{}</title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                        <link rel="stylesheet" href="web.css"/>
                    </head>
                    <body class="indiv">
                        <xsl:apply-templates select="."/>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
        <xsl:for-each select="binaryOperator">
            <xsl:variable name="operator" select="operator"/>
            <xsl:result-document method="html" href="file:///{$myOutputDir}/operator-{string-join(string-to-codepoints($operator), '-')}.html">
                <html>
                    <head>
                        <title>Operator <xsl:value-of select="$operator"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                        <link rel="stylesheet" href="web.css"/>
                    </head>
                    <body class="indiv">
                        <xsl:apply-templates select="."/>
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
                            <title>Operator <xsl:value-of select="$operator"/></title>
                            <link rel="stylesheet" href="funcdoc.css"/>
                            <link rel="stylesheet" href="web.css"/>
                        </head>
                        <body class="indiv">
                            <xsl:apply-templates select=".."/>
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
                        <xsl:apply-templates select="."/>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="//guides">
        <xsl:for-each select="guide">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/guide-{@id}.html">
                <html>
                    <head>
                        <title><xsl:value-of select="@id"/></title>
                        <link rel="stylesheet" href="funcdoc.css"/>
                        <link rel="stylesheet" href="web.css"/>
                    </head>
                    <body class="indiv">
                        <xsl:apply-templates select="."/>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="section">
        <div class="section" id="{@id}">
            <xsl:apply-templates select="child::node()[not(name()='example')]"/>
            
            <xsl:if test="example">
                <div class="examples">
                    <span class="examples-header">Examples</span>
                    <xsl:for-each select="example">
                        <div class="example"><span class="example-call"><xsl:call-template
                                name="processExpression"><xsl:with-param name="expression" select="input"/></xsl:call-template> <span class="example-arrow"/> <xsl:call-template
                                name="processExpression"><xsl:with-param name="expression"><xsl:value-of select="output"/><xsl:value-of select="outputPattern"/></xsl:with-param></xsl:call-template></span></div>
                    </xsl:for-each>
                </div>
            </xsl:if>
        </div>
    </xsl:template>
    
    <xsl:template match="title">
        <span class="title"><xsl:value-of select="child::node()"/></span>
    </xsl:template>

    <xsl:template match="link">
        <xsl:choose>
            <xsl:when test="@function"><a class="internal-link" href="function-{@namespace}-{@function}.html"><xsl:value-of select="@function"/>(..)</a></xsl:when>
            <xsl:when test="@namespace"><a class="internal-link" href="{@namespace}.html"><xsl:value-of select="@namespace"/></a></xsl:when>
            <xsl:when test="@type='typevar' or @type='List' or @type='unitvar'"><a class="internal-link" href="type-{@type}.html"><xsl:copy-of select="child::node()"/></a></xsl:when>
            <xsl:when test="@type"><a class="internal-link" alt="Type {@type}" href="type-{@type}.html"><xsl:value-of select="@type"/></a></xsl:when>
            <xsl:when test="@operator"><a class="internal-link" href="operator-{string-join(string-to-codepoints(@operator), '-')}.html">operator <xsl:value-of select="@operator"/></a></xsl:when>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>
