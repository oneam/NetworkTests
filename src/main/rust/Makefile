all: bin bin/echo_client bin/echo_server

bin:
	mkdir bin

bin/echo_client: echo_client.rs
	rustc -o bin/echo_client echo_client.rs

bin/echo_server: echo_server.rs
	rustc -o bin/echo_server echo_server.rs

clean:
	rm -rf bin
