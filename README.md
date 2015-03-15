# JavaNetworkTest

A study in various methods of creating an echo server and client.

## Build instructions
```
$ git clone git@github.com:oneam/JavaNetworkTest.git
$ cd JavaNetworkTest/
$ ./gradlew <target>
```

There are three gradle targets available:

### clients

Starts all available clients. These clients attempt to connect to an echo server on localhost port 4726
and send a small message as fast as poosible. (The message rate is displayed for each client)

### syncServer

Starts an echo server that listens on port 4726. This server uses synchronous NIO sockets.

### asyncServer

Starts an echo server that listens on port 4726. This server uses asynchronous NIO and RxJava.



----------------------------------------------------------------------------
**Copyright (c) 2015 Sam Leitch. All rights reserved.**

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to
deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
IN THE SOFTWARE.
