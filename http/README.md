# HTTP Action
HTTP Action is an [Action](https://github.com/Knotx/knotx-fragments/tree/master/handler/api#action) 
that connects to the external WEB endpoint that responds with JSON and saves the response into the 
[Fragment's](https://github.com/Knotx/knotx-fragments/tree/master/api#knotx-fragment-api) `payload`.

## How does it work
HTTP Action uses HTTP Client to connect to the external WEB endpoint. It expects HTTP response with
JSON body. To configure endpoint you will have to provide `domain` and `port` and additionally
a request `path` to the WEB endpoint. The `path` parameter could contain placeholders that will be
resolved with the logic described above.
After the result from the external WEB endpoint is obtained, it is merged with processed
[Fragment's](https://github.com/Knotx/knotx-fragments/tree/master/api#knotx-fragment-api) `payload`
and returned in the [`FragmentResult`](https://github.com/Knotx/knotx-fragments/blob/master/handler/api/docs/asciidoc/dataobjects.adoc#FragmentResult)
together with Transition.

#### Parametrized services calls
Http Action supports request parameters, `@payload` and `@configuration` resolving for the `path` parameter which defines the final request URI.
Read more about placeholders in the [Knot.x Server Common Placeholders](https://github.com/Knotx/knotx-server-http/tree/master/common/placeholders#available-request-placeholders-support).

The `@payload` and `@configuration` are values stored in [Fragment](https://github.com/Knotx/knotx-fragments/tree/master/api#knotx-fragment-api).
For this structures use corresponding prefixes: `payload` and `config` 

## How to use
Define HTTP Action using `http` factory and providing configs `endpointOptions` and `responseOptions` in the Fragment's Handler
`actions` section.

```hocon
actions {
  book {
    factory = http
    config {
      endpointOptions {
        path = /service/mock/book.json
        domain = localhost
        port = 3000
        allowedRequestHeaders = ["Content-Type"]
      }
      responseOptions {
        predicates = [JSON]
        forceJson = false
      }       
    }
  }
}
```

- `endpointOptions` config describes quite basic request parameters as `path`, `domain`, `port` and `allowedRequestHeaders` which
are necessary to perform http request 
- `responseOptions` config is responsible for handling incoming request properly. Here we can specify `predicates` - it's array
containing Vert.x response predicates, to get more familiar with it, please visit [this page](https://vertx.io/blog/http-response-validation-with-the-vert-x-web-client/).
You may find all available predicates [here](https://vertx.io/docs/apidocs/io/vertx/ext/web/client/predicate/ResponsePredicate.html).
Providing `JSON` predicate causes `Content-Type` check - when `Content-Type` won't be equal to `application/json` we'll get
error transition. We can also specify `forceJson` param. When `Content-Type` won't be equal to `application/json` and `forceJson`
is true, response will be processed as json. If it won't be json, request ends with error transition.

Table below shows the behaviour of HttpAction depending on provided `responseOptions` config and response:

| Content-Type     | forceJSON | JSON predicate | Body | Transition | Response |
| ---------------- |:---------:| --------------:|  ---:|  ---------:| --------:|
| application/json | false     | -              | JSON | _success   | JSON     |
| application/text | true      | -              | JSON | _success   | JSON     |
| application/json | false     | JSON           | JSON | _success   | JSON     |
| application/text | false     | -              | JSON | _success   | text     |
| application/text | false     | -              | RAW  | _success   | text     |
| application/json | false     | -              | RAW  | _error     | -        |
| application/text | true      | -              | RAW  | _error     | -        |
| application/json | false     | JSON           | RAW  | _error     | -        |
| application/text | false     | JSON           | JSON | _error     | -        |
| application/text | true      | JSON           | JSON | _error     | -        |

### Node log
HTTP Action adds details about the request, response and occurred errors to [node log](https://github.com/Knotx/knotx-fragments/tree/master/handler/engine#node-log). 
If the log level is `ERROR`, then only failing situations are logged: exception occurs during processing, response predicate is not valid, or status code is not between 200 and 300. 
For the `INFO` log level all items are logged.

Node log is presented as `JSON` and has the following structure:
```json
{
  "request": REQUEST_DATA,
  "response": RESPONSE_DATA,
  "responseBody": RESPONSE_BODY,
  "errors": LIST_OF_ERRORS
}
```
`REQUEST_DATA` contains the following entries:
 ```json
{
  "path": "/api/endpoint",
  "requestHeaders": {
    "Content-Type": "application/json"
  }
}
```
`RESPONSE_DATA` contains the following entries:
```json
{
  "httpVersion": "HTTP_1_1",
  "statusCode": "200",
  "statusMessage": "OK",
  "headers": {
    "Content-Type": "application/json"
  },
  "trailers": {
  },
  "httpMethod": "GET",
  "requestPath": "http://localhost/api/endpoint"
}
```
`RESPONSE_BODY` contains the response received from service, it's text value.

`LIST_OF_ERRORS` looks like the following:
```json
[
  {
    "className": "io.vertx.core.eventbus.ReplyException",
    "message": "Expect content type application/text to be one of application/json"
  },
  {
    "className": "some other exception...",
    "message": "some other message..."
  }
]
```
The table below presents expected entries in node log on particular log levels depending on service response:

| Response                                   | Log level  | Log entries   |
| ------------------------------------------ | ---------- | ------------------------------------------ |
| `_success`                                 | INFO       | REQUEST_DATA, RESPONSE_DATA, RESPONSE_BODY |
| `_success`                                 | ERROR      |                                            |
| exception occurs and `_error`              | INFO       | REQUEST_DATA, LIST_OF_ERRORS               |
| exception occurs and `_error`              | ERROR      | REQUEST_DATA, LIST_OF_ERRORS               |
| `_error` (e.g service responds with `500`) | INFO       | REQUEST_DATA, RESPONSE_DATA, RESPONSE_BODY |
| `_error` (e.g service responds with `500`) | INFO       | REQUEST_DATA, RESPONSE_DATA                |

### Detailed configuration
All configuration options are explained in details in the [Config Options Cheetsheet](https://github.com/Knotx/knotx-data-bridge/tree/master/http/action/docs/asciidoc/dataobjects.adoc).
