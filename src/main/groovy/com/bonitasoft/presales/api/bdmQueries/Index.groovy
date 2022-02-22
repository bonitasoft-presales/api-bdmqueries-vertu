package com.bonitasoft.presales.api.bdmQueries

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST

import groovy.json.JsonBuilder
import groovy.sql.Sql
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import javax.naming.Context
import javax.naming.InitialContext
import javax.servlet.http.HttpServletRequest
import javax.sql.DataSource
import org.bonitasoft.web.extension.ResourceProvider
import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiController
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Index implements RestApiController {

	private static final Logger LOGGER = LoggerFactory.getLogger(Index.class)

	@Override
	RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {
		// To retrieve query parameters use the request.getParameter(..) method.
		// Be careful, parameter values are always returned as String values

		// Retrieve queryId parameter
		def queryId = request.getParameter "queryId"
		if (queryId == null) {
			return buildErrorResponse(responseBuilder, """{"error" : "the parameter queryId is missing"}""")
		}
		// Retrieve p parameter
		def p = request.getParameter("p").toInteger()
		if (p == null) {
			return buildErrorResponse(responseBuilder, """{"error" : "the parameter p is missing"}""")
		}
		// Retrieve c parameter
		def c = request.getParameter("c").toInteger()
		if (c == null) {
			return buildErrorResponse(responseBuilder, """{"error" : "the parameter c is missing"}""")
		}
		// Retrieve o parameter
		def o = request.getParameter "o"
		if (o == null) {
			return buildResponse(responseBuilder,"""{"error" : "the parameter o is missing"}""")
		}

		// Get the query SQL definition from the queries.properties file using query id.
		String query = getQuery queryId, context.resourceProvider, o
		if (query == null) {
			return buildErrorResponse(responseBuilder, "The queryId does not refer to an existing query. Check your query id and queries.properties file content.")
		}

		// Build a map will all SQL queries parameters (all REST call parameters expect "queryId").
		Map<String, String> params = getSqlParameters(request, queryId, context.resourceProvider)

		// Get the database connection using the data source declared in datasource.properties
		Sql sql = buildSql context.resourceProvider

		try {
			// Run the query with or without parameters.
			//LOGGER.error("query: ${query}" as String )
			//LOGGER.error("parameters: ${params}" as String )

			def rows = params.isEmpty() ? sql.rows(query) : sql.rows(query, params, p * c + 1, c)

			// lower case column names
			def lowerCased = rows.collect { row ->
				row.collectEntries([:]) { k, v -> [k.toLowerCase(), v] }
			}

			// Build the JSON answer with the query result
			JsonBuilder builder = new JsonBuilder(lowerCased)
			String table = builder.toPrettyString()
			return buildResponse(responseBuilder, table)
		} finally {
			sql.close()
		}
	}

	def getStringUrlParameter(HttpServletRequest request, RestApiResponseBuilder responseBuilder, String parameterName) {
		def param = request.getParameter parameterName
		if (param == null) {
			return buildResponse(responseBuilder, SC_BAD_REQUEST, """{"error" : "the parameter queryId is missing"}""")
		}

	}

	protected RestApiResponse buildErrorResponse(RestApiResponseBuilder apiResponseBuilder, String message) {
		LOGGER.error message

		Map<String, String> result = [:]
		result.put "error", message
		apiResponseBuilder.withResponseStatus(SC_BAD_REQUEST)
		buildResponse apiResponseBuilder, result
	}

	protected RestApiResponse buildResponse(RestApiResponseBuilder apiResponseBuilder, Serializable result) {
		apiResponseBuilder.with {
			withResponse(result)
			build()
		}
	}


	/**
	 * Returns a paged result like Bonita BPM REST APIs.
	 * Build a response with a content-range.
	 *
	 * @param responseBuilder the Rest API response builder
	 * @param body the response body
	 * @param p the page index
	 * @param c the number of result per page
	 * @param total the total number of results
	 * @return a RestAPIResponse
	 */
	RestApiResponse buildPagedResponse(RestApiResponseBuilder responseBuilder, Serializable body, int p, int c, long total) {
		return responseBuilder.with {
			withContentRange(p, c, total)
			withResponse(body)
			build()
		}
	}


	protected Map<String, Object> getSqlParameters(HttpServletRequest request, String queryId, ResourceProvider resourceProvider) {
		def exclusions = ["queryId", "p", "c", "o"]
		Properties queryProps = loadProperties("${queryId}.properties" as String, resourceProvider)

		Map<String, Object> params = [:]
		for (String parameterName : request.getParameterNames()) {
			if (!exclusions.contains(parameterName)) {
				def parameterType = queryProps["${parameterName}.type"]
				def parameterValue = request.getParameter(parameterName)
				if (parameterValue) {
					switch (parameterType) {
						case 'integer':
							params.put(parameterName, parameterValue.toInteger())
							break
						case 'long':
							params.put(parameterName, parameterValue.toLong())
							break
						case 'float':
							params.put(parameterName, parameterValue.toFloat())
							break
						case 'double':
							params.put(parameterName, parameterValue.toDouble())
							break
						case 'timestamp':
							def dateParsed = new Date(parameterValue.toLong() * 1000)
							def ts = Timestamp.from(dateParsed.toInstant())
							params.put(parameterName, ts)
							break
						case 'date':
							def dateParsed = new Date(parameterValue.toLong() * 1000)
							params.put(parameterName, dateParsed)
							break
						case 'localDate':
							def dateParsed = LocalDate.parse(parameterValue)
							params.put(parameterName, dateParsed)
							break
						case 'offsetDateTime':
							def dateParsed = OffsetDateTime.parse(parameterValue)
							params.put(parameterName, dateParsed)
							break
						case 'localDateTime':
							def dateParsed = LocalDateTime.parse(parameterValue)
							params.put(parameterName, dateParsed)
							break
						case 'text':
						case 'string':
							params.put(parameterName, parameterValue)
							break
						default:
							throw new IllegalArgumentException("type not supported or not found for param ${parameterName} - value: ${parameterValue}")
					}
				}
			}
		}
		params
	}

	protected Sql buildSql(ResourceProvider pageResourceProvider) {
		Properties props = loadProperties "datasource.properties", pageResourceProvider
		Context ctx = new InitialContext(props)
		DataSource dataSource = (DataSource) ctx.lookup(props["datasource.name"])
		new Sql(dataSource)
	}

	protected String getQuery(String queryId, ResourceProvider resourceProvider, String order) {
		Properties props = loadProperties "queries.properties", resourceProvider
		def sqlFile = props[queryId]
		def query = loadSqlFile(sqlFile, resourceProvider) + "order by ${order}"
		return query
	}

	protected Properties loadProperties(String fileName, ResourceProvider resourceProvider) {
		Properties props = new Properties()
		resourceProvider.getResourceAsStream(fileName).withStream { InputStream s ->
			props.load s
		}
		props
	}

	protected String loadSqlFile(String fileName, ResourceProvider resourceProvider) {
		resourceProvider.getResourceAsStream(fileName).text
	}
}
