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

struct client_info_s {
    int sockd;
    const char *name;
};

typedef struct client_info_s client_info_t;

void *send_loop(void *arg) {
    client_info_t *client_info = (client_info_t *)arg;
    int sockd = client_info->sockd;
    const char *name = client_info->name;
    free(arg);

    char buffer[BUFFER_SIZE];
    size_t msg_size = strlen(MESSAGE);
    strncpy(buffer, MESSAGE, BUFFER_SIZE);

    time_t last_tick = time(NULL);
    long count = 0;

    while (true) {
        
        ssize_t send_size = send(sockd, buffer, msg_size, 0);
        if (send_size <= 0) {
            if(send_size < 0) {
                printf("%s send error: %s", name, strerror(errno));
            }
            close(sockd);
            return NULL;
        }
    }
}

void *recv_loop(void *arg) {
    client_info_t *client_info = (client_info_t *)arg;
    int sockd = client_info->sockd;
    const char *name = client_info->name;
    free(arg);

    char buffer[BUFFER_SIZE];
    size_t msg_size = strlen(MESSAGE);
    time_t last_tick = time(NULL);
    long count = 0;

    while (true) {
        ssize_t recv_size = recv(sockd, buffer, BUFFER_SIZE, 0);
        if (recv_size <= 0) {
            if(recv_size < 0 && errno != EAGAIN) {
                printf("%s recv error: %s", name, strerror(errno));
            }
            close(sockd);
            return NULL;
        }
        
        time_t now = time(NULL);
        count += recv_size;
        
        if (now > last_tick) {
            last_tick = now;
            printf("%s: %ld\n\n", name, count / msg_size);
            count = 0;
        }
    }
}

pthread_t start_client_loop(int sockd, const char *name) {
    int status;
    client_info_t *send_info = malloc(sizeof(client_info_t));
    send_info->sockd = sockd;
    send_info->name = name;
    
    pthread_t send_thread;
    status = pthread_create(&send_thread, NULL, &send_loop, send_info);
    if (status != 0) {
        printf("%s send thread creation: %s", name, strerror(errno));
        exit(1);
    }
    
    client_info_t *recv_info = malloc(sizeof(client_info_t));
    recv_info->sockd = sockd;
    recv_info->name = name;
    
    pthread_t recv_thread;
    status = pthread_create(&recv_thread, NULL, &recv_loop, recv_info);
    if (status != 0) {
        printf("%s recv thread creation: %s", name, strerror(errno));
        exit(1);
    }
    
    return recv_thread;
}

pthread_t start_tcp_client(struct sockaddr_in remote_addr) {
    int sockd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sockd == -1) {
        perror("TCP socket creation");
        exit(1);
    }
    
    int status = connect(sockd, (struct sockaddr*)&remote_addr, sizeof(remote_addr));
    if (status == -1) {
        perror("TCP connect error");
        close(sockd);
        exit(1);
    }
    
    return start_client_loop(sockd, "TCP");
}

pthread_t start_udp_client(struct sockaddr_in remote_addr) {
    int sockd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (sockd == -1) {
        perror("UDP socket creation");
        exit(1);
    }
    
    int status = connect(sockd, (struct sockaddr*)&remote_addr, sizeof(remote_addr));
    if (status == -1) {
        perror("UDP connect error");
        close(sockd);
        exit(1);
    }
    
    struct timeval timeout;
    timeout.tv_sec = 1;
    timeout.tv_usec = 0;
    
    setsockopt(sockd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    
    return start_client_loop(sockd, "UDP");
}

int main (int argc, char* argv[]) {
    struct sockaddr_in remote_addr;
    remote_addr.sin_family = AF_INET;
    remote_addr.sin_addr.s_addr = inet_addr(HOST);
    remote_addr.sin_port = htons(PORT);
    
    pthread_t tcp_thread = start_tcp_client(remote_addr);
    // pthread_t udp_thread = start_udp_client(remote_addr);
    
    pthread_join(tcp_thread, NULL);
    // pthread_join(udp_thread, NULL);
}
