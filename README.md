[![License](https://img.shields.io/github/license/toolarium/toolarium-icap-client)](https://github.com/toolarium/toolarium-icap-client/blob/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.toolarium/toolarium-icap-client/1.0.0)](https://search.maven.org/artifact/com.github.toolarium/toolarium-icap-client/1.0.0/jar)
[![javadoc](https://javadoc.io/badge2/com.github.toolarium/toolarium-icap-client/javadoc.svg)](https://javadoc.io/doc/com.github.toolarium/toolarium-icap-client)

# toolarium-icap-client

Implements the an ICAP client compliant with [RFC 3507](https://www.ietf.org/rfc/rfc3507.txt)


## Built With

* [cb](https://github.com/toolarium/common-build) - The toolarium common build

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository]. 


## Usage

```java
// the ICAP-Server information
String hostName = "localhost";
int port = 1344;
String serviceName = "..."; // e.g. srv_clamav

// the user, request source and the resource
String username = "user";
String requestSource = "file";
File file = new File("build/test-file.com");

try {
    InputStream resourceInputStream = ....;
    ICAPClientFactory.getInstance().getICAPClient(hostName, port, serviceName)
         .validateResource(ICAPMode.REQMOD, new ICAPRequestInformation(username, requestSource), new ICAPResource(file.getName(), resourceInputStream, file.length()));
    
    // If no exception is thrown the resource can be used and is valid. 

} catch (IOException ioe) {  // I/O error

    LOG.warn("Resource could not be accessed: " + ioe.getMessage(), ioe);

} catch (ContentBlockedException e) { // !!! The resource has to be blocked !!! 
    
    // The e.getMessage() gives technical the proper information. It's already logged by the library.

    // The ICAP header contains structured information about virus.
    ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
    icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
    icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);
    
    // The e.getContent() contains the returned error information from the ICAP-Server. 
    // It can be ignored as long as the resource is blocked; otherwise it gives a well structured response.
}
```

### Log output of a valid resource (log level INFO):
```
DD8DEE46 - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
30AC31B0 - Validate resource (username: user, source: file, resource: test-file.com, length: 71)
30AC31B0 - Valid resource (username: user, source: file, resource: test-file.com, length: 71, http-status: 204).
```

### Log output of an invalid resource which should be blocked (log level INFO):
```
E1C57BCF - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
DA054425 - Validate resource (username: user, source: file, resource: test-virus-file.com, length: 70)
DA054425 - Thread found in resource (username: user, source: file, resource: test-virus-file.com, length: 70, http-status: 200):
- X-Infection-Found: [Type=0, Resolution=2, Threat=Eicar-Signature]
- X-Violations-Found: [1, -, Eicar-Signature, 0, 0]
- X-Request-Message-Digest: [{SHA-256}8b3f191819931d1f2cef7289239b5f77c00b079847b9c2636e56854d1e5eff71]
- X-Response-Message-Digest: [{SHA-256}2e124ff42640aafcc7e267269dd495f35411ce469ec2a64c9af56ccd74bed32f]
- X-Resource-Identical-Content: [false]
```


## Test 
```
# start service - after start you can use the java library
docker run --rm --name icap-server -p 1344:1344 toolarium/toolarium-icap-calmav-docker:0.0.1

# optional you can login into the container
docker exec -it icap-server /bin/bash

# the configuration you will see under
more /etc/c-icap/c-icap.conf

# view / tail access-log
tail -f /var/log/c-icap/access.log

# view / tail server-log
tail -f /var/log/c-icap/server.log

# test with c-icap client inside the container
c-icap-client -v -f entrypoint.sh -s "srv_clamav" -w 1024 -req http://request -d 5

# stop service
docker stop icap-server
```
