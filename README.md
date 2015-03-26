# NetworkTests

A study in various methods of creating an echo server and client.
It was originally intended to see how ReactiveX would work with sockets
and determine what the performance characteristics of synchronous and asynchoronous
socket IO.

For reference, I've discovered that each platform running at peak performance accounts for 25-50% throughput difference. However, full duplex communication accounted for a 1000% improvment on all platforms (except Go, which fell apart after atempting full duplex).

## Build instructions
### Java
```
./gradlew clients
```
Starts all available clients. These clients attempt to connect to an echo server on localhost port 4726
and send a small message as fast as poosible. (The message rate is displayed for each client)

```
./gradlew syncServer
```
Starts an echo server that listens on port 4726. This server uses synchronous NIO sockets.

```
./gradlew asyncServer
```
Starts an echo server that listens on port 4726. This server uses asynchronous NIO and RxJava.

### Go
```
cd src/main/go
go run echo_server.go
```
Starts an echo server that listens on port 4726.

```
cd src/main/go
go run echo_client.go
```
Starts an echo client that connects to localhost port 4726.

### C# (Tested with Mono on Mac)
**src/main/csharp/TestNetwork.csproj** is a project created using Xamarin Studio that uses NuGet packages for Rx.

Starts an echo client that connects to localhost port 4726.

### C (Posix compliant)
```
cd src/main/c
make
bin/echo_server
```
Starts an echo server that listens on port 4726.

```
cd src/main/c
make
bin/echo_client
```
Starts an echo client that connects to localhost port 4726.

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
