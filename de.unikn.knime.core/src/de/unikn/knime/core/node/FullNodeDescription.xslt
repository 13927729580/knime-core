<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:template match="knimeNode">
	<html>
	<head>
		<title>Node description for <xsl:value-of select="name" /></title>
	</head>
	<body style="font-family: Tahoma, Arial, Helvetica; font-size: 80%;">
		<h2 align="center"><xsl:value-of select="name" /></h2><br />
		<p style="text-align: justify">
			<xsl:apply-templates select="fullDescription/intro/node()" mode="copy" />
		</p>
		
		<xsl:if test="fullDescription/option">
			<h3>Dialog options</h3>
			<xsl:for-each select="fullDescription/option">
				<div style="font-weight: bold;"><xsl:value-of select="@name" /></div>
				<div style="margin-left: 5mm; margin-top: 1mm; margin-bottom: 3mm;">
					<xsl:apply-templates select="node()" mode="copy" />
				</div>
			</xsl:for-each>
		</xsl:if>

		<xsl:apply-templates select="ports" />
		<xsl:apply-templates select="views" />
		
	</body>
	</html>
</xsl:template>

<xsl:template match="views[view]">
	<hr width="90%" />
	<h3>Views</h3>
	<xsl:for-each select="view">
		<xsl:sort select="@index" />
		<div>
			<span style="font-weight: bold; margin-right: 3mm;"><xsl:value-of select="@name" /></span>
			<xsl:apply-templates />
		</div>
	</xsl:for-each>
</xsl:template>
	
	
<xsl:template match="ports">
	<hr width="90%" />
	<h3>Ports</h3>
	<xsl:if test="dataIn">
		<h4>Data Input</h4>
		<xsl:for-each select="dataIn">
			<xsl:sort select="@index" />
			<div>
    			<span style="font-weight: bold; margin-right: 3mm;"><xsl:value-of select="@index" /></span>
				<xsl:apply-templates />
			</div>
		</xsl:for-each>
	</xsl:if>					
	<xsl:if test="dataOut">
		<h4>Data Output</h4>
		<xsl:for-each select="dataOut">
			<xsl:sort select="@index" />
			<div>
				<span style="font-weight: bold; margin-right: 3mm;"><xsl:value-of select="@index" /></span>
				<xsl:apply-templates />
			</div>
		</xsl:for-each>
	</xsl:if>					
	<xsl:if test="predParamIn">
		<h4>Model Input</h4>
		<xsl:for-each select="predParamIn">
			<xsl:sort select="@index" />
			<div>			
				<span style="font-weight: bold; margin-right: 3mm;"><xsl:value-of select="@index" /></span>
				<xsl:apply-templates />
			</div>
		</xsl:for-each>
	</xsl:if>					
	<xsl:if test="predParamOut">
		<h4>Model Output</h4>
		<xsl:for-each select="predParamOut">
			<xsl:sort select="@index" />
			<div>
				<span style="font-weight: bold; margin-right: 3mm;"><xsl:value-of select="@index" /></span>
				<xsl:apply-templates />
			</div>
		</xsl:for-each>
	</xsl:if>					
</xsl:template>
	
<xsl:template match="@*|node()" priority="-1" mode="copy">
        <xsl:copy><xsl:apply-templates select="@*|node()" mode="copy" /></xsl:copy>
</xsl:template>

</xsl:stylesheet>