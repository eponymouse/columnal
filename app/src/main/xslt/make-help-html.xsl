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
    <xsl:output method="html"/>
    <xsl:template match="/dialog">
        <html>
            <head>
                <title><xsl:value-of select="@title"/></title>
                <link rel="stylesheet" href="help.css"/>
            </head>
            <body>
                <xsl:for-each select="help">
                    <div class="help-item">
                        <h1><xsl:copy-of select="@id"/><xsl:value-of select="@title"/></h1>
                        <p><b><xsl:value-of select="short"/></b></p>
                        <p><xsl:value-of select="full"/></p>
                    </div>
                </xsl:for-each>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
