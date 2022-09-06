<?xml version="1.0"?>
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