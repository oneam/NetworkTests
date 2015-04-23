all: bin bin/echo_client bin/echo_server bin/echo_client_nonblock bin/echo_client_halfduplex

bin:
	mkdir bin

bin/echo_client_nonblock: echo_client_nonblock.c
	cc -o bin/echo_client_nonblock echo_client_nonblock.c

bin/echo_client_halfduplex: echo_client_halfduplex.c
	cc -o bin/echo_client_halfduplex echo_client_halfduplex.c

bin/echo_client: echo_client.c
	cc -o bin/echo_client echo_client.c

bin/echo_server: echo_server.c
	cc -o bin/echo_server echo_server.c

clean:
	rm -rf bin
