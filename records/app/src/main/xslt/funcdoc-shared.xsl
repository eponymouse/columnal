<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:template name="processType">
        <xsl:param name="type" select="."/>
        <xsl:analyze-string select="replace($type, '@tagged\s+', '')" regex="\{{\*\}}">
            <xsl:matching-substring>
                <span class="wild-unit"><xsl:copy-of select="."/></span>
            </xsl:matching-substring>
            <!-- All words beginning with lower-case are assumed to be vars: -->
            <xsl:non-matching-substring>
                <xsl:analyze-string select="." regex="@(type|unit)var\s+([a-z]+)">
                    <xsl:matching-substring>
                        <span class="type-var"><xsl:copy-of select="regex-group(2)"/></span>
                    </xsl:matching-substring>
                    <xsl:non-matching-substring>
                        <xsl:copy-of select="."/>
                    </xsl:non-matching-substring>
                </xsl:analyze-string>
            </xsl:non-matching-substring>
        </xsl:analyze-string>
    </xsl:template>
    
    <xsl:template name="processExpression">
        <xsl:param name="expression" select="."/>
        <xsl:analyze-string select="replace($expression,'@call\s+|@function\s+|@tagged\s+', '')" regex="[\(\)\[\]{}]+">
            <xsl:matching-substring><span class="expression-bracket"><xsl:copy-of select="."/></span></xsl:matching-substring>
            <xsl:non-matching-substring><xsl:copy-of select="."/></xsl:non-matching-substring>
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
                <div class="example"><span class="example-call"><xsl:if test="input"><xsl:call-template
                        name="processExpression"><xsl:with-param name="expression" select="input"/></xsl:call-template></xsl:if><xsl:if test="inputArg"><xsl:value-of select="$functionName"/><xsl:call-template
                        name="processExpression"><xsl:with-param name="expression"><xsl:call-template
                            name="bracketed"><xsl:with-param name="expression" select="inputArg"/></xsl:call-template></xsl:with-param></xsl:call-template></xsl:if> <span class="function-arrow"/> <xsl:call-template
                            name="processExpression"><xsl:with-param name="expression"><xsl:value-of select="output"/></xsl:with-param></xsl:call-template></span></div>
                </xsl:for-each>
            </div>
            <xsl:for-each select="seeAlso">
                <div class="seeAlso">
                    <span class="seeAlsoHeader">See Also</span>
                    <xsl:apply-templates select="child::node()"/>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>

    
    <xsl:template name="processOperator">
        <xsl:param name="operator" select="."/>

        <div class="operator-item">
            <xsl:for-each select="operator">
                <span class="operator-name-header" id="operator-{string-join(string-to-codepoints(.), '-')}">operator <xsl:copy-of select="."/></span>
            </xsl:for-each>
            <span class="operator-type"><!-- @any <xsl:value-of select="scope"/> --><xsl:call-template
                    name="processType"><xsl:with-param name="type"><xsl:call-template name="bracketed"><xsl:with-param name="expression"><xsl:value-of select="argTypeLeft"/><xsl:value-of select="argTypeRight"/> </xsl:with-param></xsl:call-template></xsl:with-param></xsl:call-template> <span class="function-arrow"/> <xsl:call-template
                    name="processType"><xsl:with-param name="type" select="resultType"/></xsl:call-template>
            </span>
            <div class="description"><xsl:copy-of select="description"/></div>
            <div class="examples">
                <span class="examples-header">Examples</span>
                <xsl:for-each select="example">
                    <div class="example"><span class="example-call"><xsl:if test="input"><xsl:call-template
                            name="processExpression"><xsl:with-param name="expression" select="input"/></xsl:call-template></xsl:if><xsl:if test="inputArg"><xsl:call-template
                            name="processExpression"><xsl:with-param name="expression"><xsl:call-template
                            name="bracketed"><xsl:with-param name="expression" select="inputArg"/></xsl:call-template></xsl:with-param></xsl:call-template></xsl:if> <span class="function-arrow"/> <xsl:call-template
                            name="processExpression"><xsl:with-param name="expression"><xsl:value-of select="output"/></xsl:with-param></xsl:call-template></span></div>
                </xsl:for-each>
            </div>
            <xsl:for-each select="seeAlso">
                <div class="seeAlso">
                    <span class="seeAlsoHeader">See Also</span>
                    <xsl:apply-templates select="child::node()"/>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>
</xsl:stylesheet>