<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:param name="myOutputDir"/>
    <xsl:output method="text"/>

    <xsl:template name="processFunctionMini">
        <xsl:param name="namespace" select="."/>
        <xsl:value-of select="$namespace"/>\:<xsl:value-of select="replace(@name,' ','\\ ')"/>=<xsl:value-of select="mini"/>
        <xsl:text>&#xa;</xsl:text>
    </xsl:template>
    
    <xsl:template name="processFunctionTypeArgs">
        <xsl:param name="namespace" select="."/>

        <xsl:value-of select="$namespace"/>\:<xsl:value-of select="replace(@name,' ','\\ ')"/>=<xsl:value-of select="typeArg" separator=";"/>
        <xsl:text>&#xa;</xsl:text>
    </xsl:template>

    <xsl:template name="processFunctionSynonyms">
        <xsl:param name="namespace" select="."/>

        <xsl:value-of select="$namespace"/>\:<xsl:value-of select="replace(@name,' ','\\ ')"/>=<xsl:value-of select="synonym" separator=";"/>
        <xsl:text>&#xa;</xsl:text>
    </xsl:template>
    
    <xsl:template name="processFunctionConstraints">
        <xsl:param name="namespace" select="."/>

        <xsl:value-of select="$namespace"/>\:<xsl:value-of select="replace(@name,' ','\\ ')"/>=<xsl:value-of select="typeConstraint" separator=";"/>
        <xsl:text>&#xa;</xsl:text>
    </xsl:template>

    <xsl:template name="processFunctionTypes">
        <xsl:param name="namespace" select="."/>

        <xsl:value-of select="$namespace"/>\:<xsl:value-of select="replace(@name,' ','\\ ')"/>=((<xsl:value-of select="argType" separator=","/>) -&gt; <xsl:value-of select="returnType"/>)  
        <xsl:text>&#xa;</xsl:text>
    </xsl:template>

    <xsl:template name="processFunctionSignature">
        <xsl:param name="namespace" select="."/>

        <xsl:value-of select="$namespace"/>\:<xsl:value-of select="replace(@name,' ','\\ ')"/>\:sig=<xsl:value-of select="argType/@name" separator=";"/>
        <xsl:text>&#xa;</xsl:text>
    </xsl:template>

    <xsl:template name="processFunctionUnitArgs">
        <xsl:param name="namespace" select="."/>

        <xsl:value-of select="$namespace"/>\:<xsl:value-of select="replace(@name,' ','\\ ')"/>=<xsl:value-of select="unitArg" separator=";"/>
        <xsl:text>&#xa;</xsl:text>
    </xsl:template>
    
    <xsl:template match="/all">
        <xsl:text>DUMMY OUTPUT</xsl:text>
        
        <xsl:result-document method="text" href="file:///{$myOutputDir}/function_minis_en.properties">
            <xsl:for-each select=".//functionDocumentation">
                <xsl:variable name="namespace" select="@namespace"/>
                <xsl:for-each select=".//function">
                    <xsl:call-template name="processFunctionMini">
                        <xsl:with-param name="namespace" select="$namespace"/>
                    </xsl:call-template>
                </xsl:for-each>
            </xsl:for-each>
        </xsl:result-document>
        <xsl:result-document method="text" href="file:///{$myOutputDir}/function_unitargs_en.properties">
            <xsl:for-each select=".//functionDocumentation">
                <xsl:variable name="namespace" select="@namespace"/>
                <xsl:for-each select=".//function">
                    <xsl:call-template name="processFunctionUnitArgs">
                        <xsl:with-param name="namespace" select="$namespace"/>
                    </xsl:call-template>
                </xsl:for-each>
            </xsl:for-each>
        </xsl:result-document>
        <xsl:result-document method="text" href="file:///{$myOutputDir}/function_typeargs_en.properties">
            <xsl:for-each select=".//functionDocumentation">
                <xsl:variable name="namespace" select="@namespace"/>
                <xsl:for-each select=".//function">
                    <xsl:call-template name="processFunctionTypeArgs">
                        <xsl:with-param name="namespace" select="$namespace"/>
                    </xsl:call-template>
                </xsl:for-each>
            </xsl:for-each>
        </xsl:result-document>
        <xsl:result-document method="text" href="file:///{$myOutputDir}/function_synonyms_en.properties">
            <xsl:for-each select=".//functionDocumentation">
                <xsl:variable name="namespace" select="@namespace"/>
                <xsl:for-each select=".//function">
                    <xsl:call-template name="processFunctionSynonyms">
                        <xsl:with-param name="namespace" select="$namespace"/>
                    </xsl:call-template>
                </xsl:for-each>
            </xsl:for-each>
        </xsl:result-document>
        <xsl:result-document method="text" href="file:///{$myOutputDir}/function_constraints_en.properties">
            <xsl:for-each select=".//functionDocumentation">
                <xsl:variable name="namespace" select="@namespace"/>
                <xsl:for-each select=".//function">
                    <xsl:call-template name="processFunctionConstraints">
                        <xsl:with-param name="namespace" select="$namespace"/>
                    </xsl:call-template>
                </xsl:for-each>
            </xsl:for-each>
        </xsl:result-document>
        <xsl:result-document method="text" href="file:///{$myOutputDir}/function_types_en.properties">
            <xsl:for-each select=".//functionDocumentation">
                <xsl:variable name="namespace" select="@namespace"/>
                <xsl:for-each select=".//function">
                    <xsl:call-template name="processFunctionTypes">
                        <xsl:with-param name="namespace" select="$namespace"/>
                    </xsl:call-template>
                    <xsl:call-template name="processFunctionSignature">
                        <xsl:with-param name="namespace" select="$namespace"/>
                    </xsl:call-template>
                </xsl:for-each>
            </xsl:for-each>
        </xsl:result-document>
    </xsl:template>
</xsl:stylesheet>