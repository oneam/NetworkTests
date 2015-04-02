#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <unistd.h>
#include <time.h>
#include <pthread.h>
#include <errno.h>
#include <fcntl.h>

#define NUM_CLIENTS 10
#define BUFFER_SIZE 65536
#define HOST "127.0.0.1"
#define PORT 4726
#define MESSAGE "message\n"

struct client_s {
    char* name;
    int sock_fd;
    char buffer[BUFFER_SIZE];
    int recv_count;
};

typedef struct client_s * client_ref;

pthread_t client_thread;
client_ref clients[NUM_CLIENTS];

void* client_loop(void *arg) {
    time_t last_tick = time(NULL);
    char *msg = MESSAGE;
    size_t msg_size = strlen(msg);

    while(true) {        
        for(int i=0; i<NUM_CLIENTS; ++i) {
            client_ref client = clients[i];
            int sock_fd = client->sock_fd;
            char *buffer = client->buffer;
            
            ssize_t send_size = send(sock_fd, msg, msg_size, 0);
            if (send_size < 0 && errno != EAGAIN) {
                printf("%s send error: %s\n", client->name, strerror(errno));
                close(sock_fd);
                return NULL;
            }
            
            ssize_t recv_size = recv(sock_fd, buffer, BUFFER_SIZE, 0);
            if (recv_size < 0 && errno != EAGAIN) {
                printf("%s recv error: %s\n", client->name, strerror(errno));
                close(sock_fd);
                return NULL;
            }
            
            if(recv_size > 0) {
                client->recv_count += recv_size;
            }
        }
        
        time_t now = time(NULL);
        
        if (now > last_tick) {
            last_tick = now;
            for(int i=0; i<NUM_CLIENTS; ++i) {
                client_ref client = clients[i];
                printf("%s: %ld\n", client->name, client->recv_count / msg_size);
                client->recv_count = 0;
            }
        }
    }
}

void client_loop_start() {
    int status = pthread_create(&client_thread, NULL, &client_loop, &clients);
    if (status != 0) {
        fprintf(stderr, "Client thread creation: %s\n", strerror(errno));
        exit(1);
    }
}

void client_loop_wait() {
    pthread_join(client_thread, NULL);
}

client_ref client_new(char* name) {
    client_ref client = (client_ref)malloc(sizeof(struct client_s));
    client->name = name;
    return client;
}

int client_start(client_ref client, struct sockaddr_in remote_addr) {
    int status;

    int sock_fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock_fd == -1) {
        perror("TCP socket creation");
        return -1;
    }

    client->sock_fd = sock_fd;

    status = connect(sock_fd, (struct sockaddr*)&remote_addr, sizeof(remote_addr));
    if (status == -1) {
        perror("TCP connect error");
        close(sock_fd);
        return -1;
    }
    
    status = fcntl(sock_fd, F_SETFL, O_NONBLOCK);
    if (status == -1) {
        perror("Failed to set socket to non-block mode");
        close(sock_fd);
        return -1;
    }
    
    return 0;
}

int main (int argc, char* argv[]) {
    char *host = (argc > 1) ? argv[1] : HOST;
    int port = (argc > 2) ? atoi(argv[2]) : PORT;

    struct sockaddr_in remote_addr;
    remote_addr.sin_family = AF_INET;
    remote_addr.sin_addr.s_addr = inet_addr(host);
    remote_addr.sin_port = htons(port);
    
    char* name_prefix = "Client";
    size_t name_len = strlen(name_prefix) + sizeof(char) * 8;

    for(int i=0; i<NUM_CLIENTS; ++i) {
        char *name = malloc(name_len);
        sprintf(name, "%s %d", name_prefix, i);
        client_ref client = client_new(name);
        int status = client_start(client, remote_addr);
        if(status == -1) {
            exit(1);
        }
        clients[i] = client;
    }
    
    client_loop_start();
    client_loop_wait();
}