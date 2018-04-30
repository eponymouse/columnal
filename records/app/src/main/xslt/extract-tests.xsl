<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:param name="myOutputDir"/>
    <xsl:output method="text" omit-xml-declaration="yes" indent="no"/>
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
        <#error TODO process exampleGroup as well
        <xsl:for-each select="example">
            <xsl:if test="output='error'">!!! </xsl:if>
            <xsl:choose>
                <xsl:when test="inputParse"> <xsl:call-template name="bracketed"><xsl:with-param name="expression" select="inputParse"/></xsl:call-template></xsl:when>
                <xsl:otherwise> @call @function "<xsl:value-of select="$functionName"/>"<xsl:call-template name="bracketed"><xsl:with-param name="expression" select="input"/></xsl:call-template></xsl:otherwise>
            </xsl:choose>
            <xsl:if test="not(output='error')"> = <xsl:choose>
                    <xsl:when test="outputParse">
                        <xsl:call-template name="bracketed"><xsl:with-param name="expression" select="outputParse"/></xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
    <xsl:value-of select="output"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:if>
            <xsl:text>&#xa;</xsl:text>
        </xsl:for-each>
    </xsl:template>
    <xsl:template match="/functionDocumentation">
        <xsl:variable name="namespace" select="@namespace"/>
    
        <xsl:for-each select="functionGroup">
            <xsl:variable name="groupName" select="@id"/>
            <xsl:for-each select="function">
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
    </xsl:template>
</xsl:stylesheet>