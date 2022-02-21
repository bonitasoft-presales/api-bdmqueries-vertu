SELECT
	sampledateold,
	sampledateonly,
	sampledatetime,
	sampledatetimenotimezone,
	sampledouble,
	samplefloat,
	sampleinteger,
	samplelong,
	samplestring,
	sampletext
FROM
	SAMPLE 
WHERE /* not support for deprecated date */
sampledateonly = :dateOnly
AND   sampledatetime = :dateTime 
AND	  sampledatetimenotimezone = :dateTimeNoTimeZone 
AND	  sampledouble = :doubleValue 
AND	  samplefloat >= :floatValue 
AND	  sampleinteger = :integerValue
AND	  samplelong = :longValue 
AND	  samplestring = :stringValue
/* not yet supported = clob and blob parameters */
