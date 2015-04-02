#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <unistd.h>
#include <pthread.h>

#define BUFFER_SIZE 65536
#define HOST "0.0.0.0"
#define PORT 4726

struct client_s {
    int sock_fd;
};

typedef struct client_s *client_ref;

client_ref client_new(int client_sock_fd) {
    client_ref client = malloc(sizeof(struct client_s));
    client->sock_fd = client_sock_fd;
    return client;
}

void client_close(client_ref client) {
    close(client->sock_fd);
    free(client);
}

void *client_loop(void *arg) {
    client_ref client = (client_ref)arg;
    char buffer[BUFFER_SIZE];
    int sock_fd = client->sock_fd;
    
    while (true) {
        ssize_t recv_size = recv(sock_fd, buffer, BUFFER_SIZE, 0);
        if (recv_size <= 0) {
            if(recv_size < 0) {
                perror("recv error");
            }
            client_close(client);
            return NULL;
        }
        
        ssize_t send_size = send(sock_fd, buffer, recv_size, 0);
        if (send_size <= 0) {
            if(send_size < 0) {
                perror("send error");
            }
            client_close(client);
            return NULL;
        }
    }
}

int client_start(client_ref client) {
    int status;
    pthread_t client_thread;
    
    status = pthread_create(&client_thread, NULL, &client_loop, client);
    if (status != 0) {
        perror("client thread creation error");
        return -1;
    }
    
    status = pthread_detach(client_thread);
    if (status != 0) {
        perror("client thread detach error");
        return -1;
    }
    
    return 0;
}

void server_start() {
    struct sockaddr_in local_addr;
    local_addr.sin_family = AF_INET;
    local_addr.sin_addr.s_addr = inet_addr(HOST);
    local_addr.sin_port = htons(PORT);
    
    int server_sock_fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (server_sock_fd == -1) {
        perror("socket creation");
        exit(1);
    }
    
    int status;

    status = bind(server_sock_fd, (struct sockaddr*)&local_addr, sizeof(local_addr));
    if (status != 0) {
        perror("bind error");
        close(server_sock_fd);
        exit(1);
    }
    
    status = listen(server_sock_fd, 10);
    if (status != 0) {
        perror("listen error");
        close(server_sock_fd);
        exit(1);
    }
    
    printf("listening on port %d\n", PORT);
    
    while (true) {
        int client_sock_fd;
        struct sockaddr_in remote_addr;
        socklen_t remote_addr_len = sizeof(remote_addr);
        
        client_sock_fd = accept(server_sock_fd, (struct sockaddr *)&remote_addr, &remote_addr_len);
        if (client_sock_fd == -1) {
            perror("accept error");
            close(client_sock_fd);
            exit(1);
        }
        
        printf("Connected to %s:%d\n", inet_ntoa(remote_addr.sin_addr), remote_addr.sin_port);
        
        client_ref client = client_new(client_sock_fd);
        status = client_start(client);
        if (status != 0) {
            fprintf(stderr, "client start error");
            exit(1);
        }
    }
}

int main(int argc, char* argv[]) {
    server_start();
}
