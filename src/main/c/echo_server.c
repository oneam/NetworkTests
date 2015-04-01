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

void *client_loop(void *arg) {
    char buffer[BUFFER_SIZE];
    int sockd = *(int *)arg;
    free(arg);
    
    while (true) {
        ssize_t recv_size = recv(sockd, buffer, BUFFER_SIZE, 0);
        if (recv_size <= 0) {
            if(recv_size < 0) {
                perror("TCP recv error");
            }
            close(sockd);
            return NULL;
        }
        
        ssize_t send_size = send(sockd, buffer, recv_size, 0);
        if (send_size <= 0) {
            if(send_size < 0) {
                perror("TCP send error");
            }
            close(sockd);
            return NULL;
        }
    }
}

void *server_loop(void *arg) {
    int status;
    int server_sockd = *(int *)arg;
    free(arg);

    while (true) {
        int client_sockd;
        struct sockaddr_in remote_addr;
        socklen_t remote_addr_len = sizeof(remote_addr);
        
        client_sockd = accept(server_sockd, (struct sockaddr *)&remote_addr, &remote_addr_len);
        if (client_sockd == -1) {
            perror("TCP accept error");
            close(client_sockd);
            return NULL;
        }
        
        printf("Connected to %s:%d\n", inet_ntoa(remote_addr.sin_addr), remote_addr.sin_port);
        
        pthread_t client_thread;
        int *client_sockd_ref = malloc(sizeof(int));
        *client_sockd_ref = client_sockd;
        
        status = pthread_create(&client_thread, NULL, &client_loop, client_sockd_ref);
        if (status != 0) {
            perror("TCP client thread creation error");
            return NULL;
        }
    }
}

pthread_t start_server(struct sockaddr_in local_addr) {
    int status;
    int server_sockd;
    
    server_sockd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (server_sockd == -1) {
        perror("TCP socket creation");
        exit(1);
    }
    
    status = bind(server_sockd, (struct sockaddr*)&local_addr, sizeof(local_addr));
    if (status != 0) {
        perror("TCP bind error");
        close(server_sockd);
        exit(1);
    }
    
    status = listen(server_sockd, 10);
    if (status != 0) {
        perror("TCP listen error");
        close(server_sockd);
        exit(1);
    }
    
    pthread_t server_thread;
    int *server_sockd_ref = malloc(sizeof(int));
    *server_sockd_ref = server_sockd;
    
    status = pthread_create(&server_thread, NULL, &server_loop, server_sockd_ref);
    if (status != 0) {
        perror("TCP server thread creation");
        exit(1);
    }
    
    printf("TCP listening on port %d\n", PORT);
    
    return server_thread;
}

int main(int argc, char* argv[]) {
    struct sockaddr_in local_addr;
    local_addr.sin_family = AF_INET;
    local_addr.sin_addr.s_addr = inet_addr(HOST);
    local_addr.sin_port = htons(PORT);
    
    pthread_t tcp_server_thread = start_server(local_addr);
    pthread_join(tcp_server_thread, NULL);
}
