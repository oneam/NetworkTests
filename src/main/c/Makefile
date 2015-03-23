all: bin/echo_client bin/echo_server

bin:
	mkdir bin

bin/echo_client: bin echo_client.c
	cc -o bin/echo_client echo_client.c

bin/echo_server: bin echo_server.c
	cc -o bin/echo_server echo_server.c

clean:
	rm -rf bin
