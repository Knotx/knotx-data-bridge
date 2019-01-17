# Knot.x Data Bridge
Knot.x Data Bridge module is a "bridge" between Knot.x rendering mechanism and external data sources.
Its main responsibility is to collect data from external data sources (these can be flat files, web services or any other kind of data sources)
and expose it in a common structure. Such structure is to be digested by further processing modules, such as handlebars.

## How does it work?
In order to use data from external data sources you need to create an Knot.x [Fragments](https://github.com/Cognifide/knotx/wiki/Splitter).
The fragments caries out a set of data source names to be used in your markup.
These names are to be used as variables under which you can find your data.

Data bridge binds those data source names with the Knot.x [Data Dource Adapters](#data-source-adapters) (that retrieves data from the any kind of sources).

Data source binding consists of unique name, parameters and a data source adapter address.:
```hocon
dataDefinitions = [
  {
    name = employees-rest-service
    params = "{ \"path\": \"/path/employees.json\" }"
    # params.path = /path/employees.json
    adapter = rest-http-adapter
  },
  {
    name = transactions-soap-service
    adapter = soap-http-adapter
  },
  {
    name = salaries-db-source
    params.query = "SELECT * FROM salaries;"
    adapter = postgres-db-adapter
  },
  {
    name = jobs-solr
    params.query = "query phrase"
    params.page = 1
    params.facet = "/jobs/it"
    adapter = solr-adapter
  }
]
```
The unique name is the data source identifier that can be used in the HTML template. Then we can
specify default parameters that are used during the data source integration. The `params` attribute is a
JSON object which can be easily split into key-value pairs with [HOCON syntax](https://github.com/lightbend/config/blob/master/HOCON.md#array-and-object-concatenation).
The last definition entry, the `adapter`, specifies the module deployed within Knot.x that calls the
data source(s) and provides the data in the JSON format.

Let's see the configuration above. There are four data source definitions:
- `employees-rest-service` - employees from the HTTP REST service
- `transactions-soap-service` - transactions from the HTTP SOAP service
- `cities-db-source` - cities from the PostgreSQL database
- `jobs-solr` - search results connected from the search engine

You can easily define your own business logic that for example fetches employees coming from the REST service,
then for all directors checks their transactions using the SOAP service and finally validates salaries based
on data from the PostgreSQL database. It can be easily hidden in the custom Adapter, see
more details in the [Data Dource Adapters](#data-source-adapters) section.

Let's concentrate for now on the first simplest case - fetching employees from the HTTP REST service. The
data source definition looks like:

```hocon
{
  name = employees-rest-service
  params = "{ \"path\": \"/path/employees.json\" }"
  # params.path = /path/employees.json
  adapter = rest-http-adapter
}
```

The data source definition is communication protocol agnostic so it does not contain HTTP details
like SSL, HTTP/2 or pipelining support. All those details are hidden in the Data Source Adapter
configuration that can map the `params` JSON object (the path in this case) to the HTTP specifics.
The `adapter` entry whose value is `rest-http-adapter` specifies the event bus address on which the
module capable of calling the HTTP service listens.

Now let's see how the `employees-rest-service` data source in the HTML markup is configured. The
`<knotx:snippet>` definition (Fragment) looks like:

```html
<knotx:snippet knots="databridge"
  databridge-name="employees-rest-service" databridge-params='{"path":"/overridden/path"}'
  databridge-name-mysalaries="salaries-db-source"
  type="text/knotx-snippet">
  ...
</knotx:snippet>
```

Data Bridge filters Fragments containing the `databridge` entry in the `knots` attribute
(the list of Knots). Then for all filtered fragments it collects responses from data sources specified in
`databridge-name-{NAMESPACE}`=[data source definition name] attributes through subsequent
asynchronous Adapters calls. All collected JSONs (see Data Source Adapter contract) are saved
in the [Fragment Context](https://github.com/Cognifide/knotx/wiki/Splitter#fragment).
The `NAMESPACE` is optional and specifies the key under which the response from the data
source is saved. The default namespace is `_result`.

The final Fragment Context for our example looks like:
```json
{
  "_result": { EMPLOYEES_JSON },
  "mysalaries": { SALARIES_JSON }
}
```

The data source parameters can be also configured in Fragment and merged with default ones using
the `databridge-params-{NAMESPACE}={JSON DATA}` attribute. The attribute is matched with the data
source definition based on a namespace.

<span id="fallback-mechanism"></span>
## Fallback mechanism
The fallback mechanism allows you to handle errors when fragment processing. The Data Bridge module retrieves data from various data sources using [Data Source Adapters](#data-source-adapters). 
If any adapter throws an exception or responds with status code `500` or higher, then Knot.x Bridge marks the entire fragment as unsuccessful.
    
See the example below:
```html
<knotx:snippet knots="databridge" 
  databridge-name="employees-rest-service"
  databridge-name-mysalaries="salaries-db-source"
  fallback="my_fallback_id"
  type="text/knotx-snippet">
   ... some content ...
</knotx:snippet>
```
The Data Bridge asynchronously invokes `employees-rest-service` and `salaries-db-source` adapters asynchronously. If the `salaries-db-source` adapter responds with error *500*, then the entire fragment  will be marked as unsuccessful (even if the `employees-rest-service` adapter responds with status code 200).

## Data Source Calls Caching

Template might consists of more than one Data Source Adapter call. 
It's also possible that there are multiple fragments on the page, each using same call. 
Knot.x Data Bridge does caching results of Adapter calls to avoid multiple calls for the same data.

By default adapter call is cached by data source `name` and `params` key, but you can also define a `cacheKey` value in configuration

```hocon
{
  name = employees-rest-service
  params = "{ \"path\": \"/path/employees.json\" }"
  cacheKey = first
  adapter = rest-http-adapter
}
``` 
Caching is performed within page request scope, this means another request will not get cached data.

<span id="data-source-adapters"></span>
## Data Sources Adapters

Data Sources Adapter is Component of a system, that mediate communication between Knot.x Data Bridge Knot and external 
services that deliver data into [Knot.x Context](https://github.com/Cognifide/knotx/wiki/Knot#knot-context). In short, Data Sources Adapter acts as a element 
translating messages between external services and Knots.

Data Sources Adapter accepts message of type `DataSourceAdapterRequest` with the following data:

 - request - object with all data of an original client http request,
 - params - all additional params defined in configuration or passed in template.
 
Result generated by Adapter must be of type `DataSourceAdapterResponse` with `clientResponse` field set:

 - body - field is supposed to carry on the actual response
 from the service in JSON format. Can be a single object or an array of objects 
 - status code - http status code of processing. It is available under key `_response.statusCode` on Fragment Context
 
 ```
 {
 "_result": BODY_JSON,
 "_response":{"statusCode":"200"}
 }
 
```
For details how to implement Data Sources Adapters please check:

 - [How to extend](https://github.com/Knotx/knotx-data-bridge/tree/master/how-to-extend)
 - [Adapt Service without Web API](http://knotx.io/tutorials/adapt-service-without-webapi)
 
### HTTP Data Source Adapter

Http Data Source Adapter is an example of Adapter implementation embedded in Knot.x Data Bridge . 
It enables communication between Data Bridge Knot and external services via HTTP.

It uses [Vert.x Web Client](https://vertx.io/docs/vertx-web-client/java/)

#### Contract
It requires mandatory parameter `path` when the definition of service is created


#### How to configure 

 * clientOptions - all values from [HttpClientOptions](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpClientOptions.html) available
 
 *  services - List of datasource services that are supported by HTTP Data Source Adapter in your project. You can specify additional configuration depends on reg exp for service `path` 
      - `path` - regexp to distinguish each data source
      - `domain` - A domain or IP of actual HTTP service the adapter will talk to
      - `port` - HTTP port of the service
      - `allowedRequestHeaders` - List of request headers that will be send to the given service endpoint.
                                Each header can be use wildcards '*' to generalize list
                                By default all requests headers are denied. If you want to enable a desired headers you can
                                express it as an array of regexp expressions, e.g. "X-.*" to allow all headers starting
                                with "X-", or simply ".*" to allow all headers.
                                Be careful what headers you're trying to pass through as some of them might affect the
                                communication with the service.
                                E.g.
                                      
            allowedRequestHeaders = ["X-.*"]

      - `queryParams` -  Additional request query parameters to be send in each request.
                          E.g. 
            
            queryParams { type = books
                          query = java
            }

      - `additionalHeaders` - Additional headers to be send in each request to the service
                              E.g:
                              
            additionalHeaders {
                 someToken = abxcdsafsfdfes
             }
          
 * customRequestHeader - Statically defined HTTP request header sent in every request to any datasource

Please see [Getting Started with Knot.x Stack](http://knotx.io/tutorials/getting-started-with-knotx-stack)
where in details the configuration is described.

#### Parametrized services calls
When found a placeholder within the `path` parameter it will be replaced with a dynamic value based on the current http request (check `DataSourceAdapterRequest`). Available placeholders are:

- {header.x} - is the client requests header value where x is the header name
- {param.x} - is the client requests query parameter value. For x = q from /a/b/c.html?q=knot it will produce knot
- {uri.path} - is the client requests sling path. From /a/b/c.sel.it.html/suffix.html?query it will produce /a/b/c.sel.it.html/suffix.html
- {uri.pathpart[x]} - is the client requests xth sling path part. For x = 2 from /a/b/c.sel.it.html/suffix.html?query it will produce c.sel.it.html
- {uri.extension} - is the client requests sling extension. From /a/b/c.sel.it.html/suffix.xml?query it will produce xml
- {slingUri.path} - is the client requests sling path. From /a/b/c.sel.it.html/suffix.html?query it will produce /a/b/c
- {slingUri.pathpart[x]} - is the client requests xth sling path part. For x = 1 from /a/b/c.sel.it.html/suffix.html?query it will produce b
- {slingUri.selectorstring} - is the client requests sling selector string. From /a/b/c.sel.it.html/suffix.html?query it will produce sel.it
- {slingUri.selector[x]} - is the client requests xth sling selector. For x = 1 from /a/b/c.sel.it.html/suffix.html?query it will produce it
- {slingUri.extension} - is the client requests sling extension. From /a/b/c.sel.it.html/suffix.xml?query it will produce html
- {slingUri.suffix} - is the client requests sling suffix. From /a/b/c.sel.it.html/suffix.html?query it will produce /suffix.html
- All placeholders are always substituted with encoded values according to the RFC standard. However, there are two exceptions:

Space character is substituted by %20 instead of +.
Slash character / remains as it is.

## Building
To build this module use gradle with following settings:
```gradle
 org.gradle.daemon=false
 org.gradle.parallel=false
```
This is temporary and will be changed as soon as tests that runs on the same ports will be fixed.

## Community
Knot.x gives one communication channel that is described [here](https://github.com/Cognifide/knotx#community).

## Bugs
All feature requests and bugs can be filed as issues on [Gitub](https://github.com/Knotx/knotx-data-bridge/issues).
Do not use Github issues to ask questions, post them on the [User Group](https://groups.google.com/forum/#!forum/knotx) or [Gitter Chat](https://gitter.im/Knotx/Lobby).

## Licence
**Knot.x modules** are licensed under the [Apache License, Version 2.0 (the "License")](https://www.apache.org/licenses/LICENSE-2.0.txt)
