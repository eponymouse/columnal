<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ext="http://exslt.org/common" exclude-result-prefixes="#all"
                version="2.0">
    <xsl:template name="processType">
        <xsl:param name="type" select="."/>
        <xsl:analyze-string select="replace($type, '@tagged\s+', '')" regex="Number|Text|Boolean|DateTimeZoned|DateTime|DateYM|Date|Time">
            <xsl:matching-substring>
                <xsl:variable name="newLink">
                    <link type="{.}"/>
                </xsl:variable>
                <xsl:apply-templates select="ext:node-set($newLink)"/>
            </xsl:matching-substring>
            <!-- All words beginning with lower-case are assumed to be vars: -->
            <xsl:non-matching-substring>
                <xsl:analyze-string select="." regex="@(type|unit)var\s+([a-z]+)">
                    <xsl:matching-substring>
                        <xsl:variable name="typevar">
                            <link type="{regex-group(1)}var"><xsl:copy-of select="regex-group(2)"/></link>
                        </xsl:variable>
                        <span class="{regex-group(1)}-var"><xsl:apply-templates select="ext:node-set($typevar)"/></span>
                    </xsl:matching-substring>
                    <xsl:non-matching-substring>
                        <xsl:analyze-string select="." regex="\[">
                            <xsl:matching-substring>
                                <xsl:variable name="newLink">
                                    <link type="List">[</link>
                                </xsl:variable>
                                <xsl:apply-templates select="ext:node-set($newLink)"/>
                            </xsl:matching-substring>
                            <xsl:non-matching-substring>
                                <xsl:copy-of select="."/>
                            </xsl:non-matching-substring>
                        </xsl:analyze-string>
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
            <xsl:if test="typeArg">
                <span class="function-type-args">For any types <xsl:for-each select="typeArg"><span class="type-var"><xsl:value-of select="."/></span><xsl:if test="position() != last()">, </xsl:if></xsl:for-each><xsl:if test="typeConstraint"> where <xsl:value-of select="string-join(typeConstraint, ', ')"/></xsl:if></span>
            </xsl:if>
            <xsl:if test="unitArg">
                <span class="function-type-args">For any units <xsl:for-each select="unitArg"><span class="unit-var"><xsl:value-of select="."/></span><xsl:if test="position() != last()">, </xsl:if></xsl:for-each></span>
            </xsl:if>
            <span class="function-name-header"><xsl:value-of select="@name"/></span>
            <span class="function-name-type"><xsl:value-of select="@name"/></span>
            <span class="function-type"><!-- @any <xsl:value-of select="scope"/> --><xsl:call-template
                    name="processType"><xsl:with-param name="type"><xsl:call-template name="bracketed"><xsl:with-param name="expression"><xsl:value-of select="argType" separator =","/></xsl:with-param></xsl:call-template></xsl:with-param></xsl:call-template> <span class="function-arrow"/> <xsl:call-template
                    name="processType"><xsl:with-param name="type" select="returnType"/></xsl:call-template>
            </span>
            <div class="description"><xsl:apply-templates select="$function/description"/></div>
            <div class="examples">
                <span class="examples-header">Examples</span>
                <xsl:for-each select="example">
                <div class="example"><span class="example-call"><xsl:if test="input"><xsl:call-template
                        name="processExpression"><xsl:with-param name="expression" select="input"/></xsl:call-template></xsl:if><xsl:if test="inputArg"><xsl:value-of select="$functionName"/><xsl:call-template
                        name="processExpression"><xsl:with-param name="expression"><xsl:call-template
                            name="bracketed"><xsl:with-param name="expression" select="inputArg"/></xsl:call-template></xsl:with-param></xsl:call-template></xsl:if> <span class="example-arrow"/> <xsl:call-template
                            name="processExpression"><xsl:with-param name="expression"><xsl:value-of select="output"/><xsl:value-of select="outputPattern"/></xsl:with-param></xsl:call-template></span></div>
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

    
    <xsl:template match="binaryOperator|naryOperatorGroup">
        <xsl:param name="operator" select="."/>
        <xsl:variable name="op" select="operator"/>

        <div class="operator-item">
            <xsl:for-each select="$operator/operator">
                <span class="operator-name-header" id="operator-{string-join(string-to-codepoints(.), '-')}">operator <xsl:copy-of select="."/></span>
            </xsl:for-each>
            <span class="operator-type">
                <xsl:if test="local-name($operator)='binaryOperator'">
                <!-- @any <xsl:value-of select="scope"/> --><xsl:call-template
                    name="processType"><xsl:with-param name="type"><xsl:call-template name="bracketed"><xsl:with-param name="expression"><xsl:value-of select="$operator/argTypeLeft"/><xsl:value-of select="$op"/><xsl:value-of select="$operator/argTypeRight"/> </xsl:with-param></xsl:call-template></xsl:with-param></xsl:call-template>
                </xsl:if>
                <xsl:if test="local-name($operator)='naryOperatorGroup'">
                    <!-- @any <xsl:value-of select="scope"/> --><xsl:call-template
                        name="processType"><xsl:with-param name="type"><xsl:call-template name="bracketed"><xsl:with-param name="expression"><xsl:value-of select="$operator/argType"/><xsl:value-of select="$op"/><xsl:value-of select="$operator/argType"/><xsl:value-of select="$op"/>...</xsl:with-param></xsl:call-template></xsl:with-param></xsl:call-template>
                </xsl:if>
            <span class="function-arrow"/> <xsl:call-template
                    name="processType"><xsl:with-param name="type" select="$operator/resultType"/></xsl:call-template>
                
            </span>
            <div class="description">
                <xsl:apply-templates select="$operator/description"/>
            </div>
            <div class="examples">
                <span class="examples-header">Examples</span>
                <xsl:for-each select="$operator/example">
                    <div class="example"><span class="example-call"><xsl:if test="input"><xsl:call-template
                            name="processExpression"><xsl:with-param name="expression" select="input"/></xsl:call-template></xsl:if><xsl:if test="inputArg"><xsl:call-template
                            name="processExpression"><xsl:with-param name="expression"><xsl:call-template
                            name="bracketed"><xsl:with-param name="expression" select="inputArg"/></xsl:call-template></xsl:with-param></xsl:call-template></xsl:if> <span class="example-arrow"/> <xsl:call-template
                            name="processExpression"><xsl:with-param name="expression"><xsl:value-of select="output"/><xsl:value-of select="outputPattern"/></xsl:with-param></xsl:call-template></span></div>
                </xsl:for-each>
            </div>
            <xsl:for-each select="$operator/seeAlso">
                <div class="seeAlso">
                    <span class="seeAlsoHeader">See Also</span>
                    <xsl:apply-templates select="child::node()"/>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>

    <xsl:template match="type">
        <div class="type-item">
            <span class="type-name-header" id="type-{@name}"><xsl:value-of select="@name"/></span>
            <div class="description"><xsl:apply-templates select="description"/></div>
            <xsl:for-each select="seeAlso">
                <div class="seeAlso">
                    <span class="seeAlsoHeader">See Also</span>
                    <xsl:apply-templates select="child::node()"/>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>

    <xsl:template name="processSyntax">
        <xsl:param name="syntax" select="."/>

        <div class="syntax-item"  id="syntax-{@id}">
            <xsl:if test="typeArg">
                <span class="syntax-type-args">For any types <xsl:for-each select="typeArg"><span class="type-var"><xsl:value-of select="."/></span><xsl:if test="position() != last()">, </xsl:if></xsl:for-each><xsl:if test="typeConstraint"> where <xsl:value-of select="string-join(typeConstraint, ', ')"/></xsl:if></span>
            </xsl:if>
            <span class="syntax-name-header">
                <xsl:for-each select="syntaxElements/(keyword|type)">
                    <xsl:if test="name()='keyword'">
                        <span class="syntax-keyword" xml:space="preserve"> <xsl:value-of select="."/> </span>
                    </xsl:if>
                    <xsl:if test="name()='type'">
                        <span class="syntax-type">
                            <xsl:call-template name="processType">
                                <xsl:with-param name="type" select="."/>
                            </xsl:call-template>
                        </span>
                    </xsl:if>
                </xsl:for-each>
            </span>
            <div class="description"><xsl:apply-templates select="$syntax/description"/></div>
            <xsl:for-each select="seeAlso">
                <div class="seeAlso">
                    <span class="seeAlsoHeader">See Also</span>
                    <xsl:apply-templates select="child::node()"/>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>

    <!-- Copy all p, ul, li as-is: -->
    <xsl:template match="p|ul|li">
         <xsl:copy>
               <xsl:apply-templates select="@* | node()" />
         </xsl:copy>
    </xsl:template>

</xsl:stylesheet>