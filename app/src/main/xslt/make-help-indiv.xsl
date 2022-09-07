<?xml version="1.0"?>
<!--
  ~ Columnal: Safer, smoother data table processing.
  ~ Copyright (c) Neil Brown, 2016-2020, 2022.
  ~
  ~ This file is part of Columnal.
  ~
  ~ Columnal is free software: you can redistribute it and/or modify it under
  ~ the terms of the GNU General Public License as published by the Free
  ~ Software Foundation, either version 3 of the License, or (at your option)
  ~ any later version.
  ~
  ~ Columnal is distributed in the hope that it will be useful, but WITHOUT 
  ~ ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
  ~ FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
  ~ more details.
  ~
  ~ You should have received a copy of the GNU General Public License along 
  ~ with Columnal. If not, see <https://www.gnu.org/licenses/>.
  -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:strip-space elements="*"/>
    <xsl:param name="myOutputDir"/>
    <xsl:template match="/dialog">
        <xsl:variable name="rootId" select="@id"/>
        <xsl:for-each select="help">
            <xsl:result-document method="html" href="file:///{$myOutputDir}/{$rootId}-{@id}.html">
                <html>
                    <head>
                        <link rel="stylesheet" href="help.css"/>
                    </head>
                    <body>
                        <p><b><xsl:value-of select="short"/></b></p>
                        <p><xsl:value-of select="full"/></p>
                    </body>
                </html>
            </xsl:result-document>
        </xsl:for-each>
        <xsl:result-document method="text" href="file:///{$myOutputDir}/{$rootId}_en.properties">
            <xsl:for-each select="help">
                <xsl:value-of select="@id"/>=<xsl:value-of select="short"/><xsl:text>&#xa;</xsl:text>
                <xsl:value-of select="@id"/>.title=<xsl:value-of select="@title"/><xsl:text>&#xa;</xsl:text>
                <xsl:value-of select="@id"/>.full=<xsl:for-each select="full/*"><xsl:value-of select="replace(text(), '\n', ' ')"/>£££££</xsl:for-each><xsl:text>&#xa;</xsl:text>
            </xsl:for-each>
        </xsl:result-document>
    </xsl:template>
</xsl:stylesheet>
