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

#define BUFFER_SIZE 65536
#define HOST "127.0.0.1"
#define PORT 4726
#define MESSAGE "message\n"
#define NUM_CLIENTS 10

struct client_s {
    int sock_fd;
    const char *name;
    pthread_t recv_thread;
    pthread_t send_thread;
};

typedef struct client_s * client_ref;

void *send_loop(void *arg) {
    client_ref client = (client_ref)arg;
    int sock_fd = client->sock_fd;
    const char *name = client->name;
    free(arg);

    char *msg = MESSAGE;
    size_t msg_size = strlen(msg);

    while (true) {
        ssize_t send_size = send(sock_fd, msg, msg_size, 0);
        if (send_size <= 0) {
            if(send_size < 0) {
                fprintf(stderr, "%s send error: %s", name, strerror(errno));
            }
            close(sock_fd);
            return NULL;
        }
    }
}

void *recv_loop(void *arg) {
    client_ref client = (client_ref)arg;
    int sock_fd = client->sock_fd;
    const char *name = client->name;

    char buffer[BUFFER_SIZE];
    
    time_t last_tick = time(NULL);
    size_t msg_size = strlen(MESSAGE);
    long count = 0;

    while (true) {
        ssize_t recv_size = recv(sock_fd, buffer, BUFFER_SIZE, 0);
        if (recv_size <= 0) {
            if(recv_size < 0 && errno != EAGAIN) {
                fprintf(stderr, "%s recv error: %s", name, strerror(errno));
            }
            close(sock_fd);
            return NULL;
        }
        
        time_t now = time(NULL);
        count += recv_size;
        
        if (now > last_tick) {
            last_tick = now;
            printf("%s: %ld\n", name, count / msg_size);
            count = 0;
        }
    }
}

client_ref client_new(const char* name) {
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
    
    status = pthread_create(&client->send_thread, NULL, &send_loop, client);
    if (status != 0) {
        fprintf(stderr, "%s send thread creation: %s", client->name, strerror(errno));
        close(sock_fd);
        return -1;
    }
    
    pthread_t recv_thread;
    status = pthread_create(&client->recv_thread, NULL, &recv_loop, client);
    if (status != 0) {
        fprintf(stderr, "%s recv thread creation: %s", client->name, strerror(errno));
        close(sock_fd);
        return -1;
    }

    return 0;
}

void client_wait(client_ref client) {
    pthread_join(client->send_thread, NULL);
    pthread_join(client->recv_thread, NULL);
}

int main (int argc, char* argv[]) {
    struct sockaddr_in remote_addr;
    remote_addr.sin_family = AF_INET;
    remote_addr.sin_addr.s_addr = inet_addr(HOST);
    remote_addr.sin_port = htons(PORT);
    
    client_ref clients[NUM_CLIENTS];
    
    for(int i=0; i<NUM_CLIENTS; ++i) {
        char *name = calloc(sizeof(char), 256);
        sprintf(name, "Client %d", i);
        
        client_ref client = client_new(name);
        if(client_start(client, remote_addr) < 0) {
            exit(1);
        }

        clients[i] = client;
    }
    
    for(int i=0; i<NUM_CLIENTS; ++i) {
        client_ref client = clients[i];
        client_wait(client);
    }
}
