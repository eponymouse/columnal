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
    <xsl:template name="processExamples">
        <xsl:param name="functionName" select="."/>
        <xsl:for-each select="example">
            <xsl:if test="output='error'">!!! </xsl:if>
            <xsl:choose>
                <xsl:when test="input"> <xsl:call-template name="bracketed"><xsl:with-param name="expression" select="input"/></xsl:call-template></xsl:when>
                <xsl:otherwise> @call @function <xsl:value-of select="$functionName"/><xsl:call-template name="bracketed"><xsl:with-param name="expression" select="inputArg"/></xsl:call-template></xsl:otherwise>
            </xsl:choose>
            <xsl:if test="not(output='error')"> = <xsl:call-template name="bracketed"><xsl:with-param name="expression" select="output"/></xsl:call-template>
            </xsl:if>
            <xsl:text>&#xa;</xsl:text>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:template name="processFunction">
        <xsl:param name="function" select="."/>
        <xsl:variable name="functionName" select="@name"/>
        <xsl:call-template name="processExamples">
            <xsl:with-param name="functionName" select="$functionName"/>
        </xsl:call-template>
        <xsl:for-each select="exampleGroup">
            <xsl:for-each select="table">
## <xsl:value-of select="@name"/>
## <xsl:value-of select="columns/column/@name" separator="//"/>
## <xsl:value-of select="columns/column/@type" separator="//"/>
                <xsl:for-each select="data/row">
#### <xsl:value-of select="d" separator="//"/>
                </xsl:for-each>
                <xsl:text>&#xa;</xsl:text>
            </xsl:for-each>
            <xsl:call-template name="processExamples">
                <xsl:with-param name="functionName" select="$functionName"/>
            </xsl:call-template>
        </xsl:for-each>
        <xsl:for-each select="equivalence">
            <xsl:for-each select="foranytype">
==* <xsl:value-of select="@name"/>//<xsl:value-of select="typeConstraint" separator="//"/>
            </xsl:for-each>
            <xsl:for-each select="forany">
== <xsl:value-of select="@name"/>//<xsl:value-of select="."/>                
            </xsl:for-each>
==== <xsl:value-of select="lhs"/>
==== <xsl:value-of select="rhs"/>
            <xsl:text>&#xa;</xsl:text>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:template name="processBinaryOperator">
        <xsl:call-template name="processExamples">
            <xsl:with-param name="functionName" select="OPERATOR_NO_INPUTARG_ALLOWED"/>
        </xsl:call-template>        
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