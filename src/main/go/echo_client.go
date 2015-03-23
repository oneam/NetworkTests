package main

import (
  "net"
  "log"
  "time"
  "sync/atomic"
  "os"
  "os/signal"
  "syscall"
)

func closeAndLog(c net.Conn) {
  err := c.Close()
  if err != nil {
    log.Printf("Close error: %v", err)
  }
}

func tcpClient() {
  address := "127.0.0.1:4726"
  log.Printf("Connecting to address %v...", address)

  conn, err := net.Dial("tcp", address)
  if err != nil {
    log.Fatalf("Dial error: %v", err)
  }

  log.Printf("Connected via %v", conn.LocalAddr())
  buffer := make([]byte, 1048576)
  message := []byte("message")
  count := int64(0)
  
  update := time.Tick(time.Second)
  go func() {
    for range update {
      lastCount := atomic.SwapInt64(&count, 0)
      log.Printf("TCP: %v", lastCount)
    }
  }()

  for {
    atomic.AddInt64(&count, 1)
    _, err = conn.Write(message)
    if err != nil {
      log.Printf("Write error: %v", err)
      closeAndLog(conn)
      break
    }

    _, err := conn.Read(buffer)
    if err != nil {
      log.Printf("Read error: %v", err)
      closeAndLog(conn)
      break
    }
  }
}

func udpClient() {
  address := "127.0.0.1:4726"
  log.Printf("Connecting to UDP address %v...", address)

  conn, err := net.Dial("udp", address)
  if err != nil {
    log.Fatalf("Dial error: %v", err)
  }

  log.Printf("Connected to UDP via %v", conn.LocalAddr())
  buffer := make([]byte, 1048576)
  message := []byte("message")
  count := int64(0)
  
  update := time.Tick(time.Second)
  go func() {
    for range update {
      lastCount := atomic.SwapInt64(&count, 0)
      log.Printf("UDP: %v", lastCount)
    }
  }()

  for {
    atomic.AddInt64(&count, 1)
    _, err = conn.Write(message)
    if err != nil {
      log.Printf("Write error: %v", err)
      closeAndLog(conn)
      break
    }

    _, err := conn.Read(buffer)
    if err != nil {
      log.Printf("Read error: %v", err)
      closeAndLog(conn)
      break
    }
  }
}

func main() {
  go tcpClient()
  go udpClient()

  done := make(chan os.Signal, 1)
  signal.Notify(done, syscall.SIGINT, syscall.SIGTERM)
  <-done
}