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

func startTcpClient() {
  address := "127.0.0.1:4726"
  log.Printf("Connecting to address %v...", address)

  conn, err := net.Dial("tcp", address)
  if err != nil {
    log.Fatalf("Dial error: %v", err)
  }

  log.Printf("Connected via %v", conn.LocalAddr())
  buffer := make([]byte, 1048576)
  message := []byte("message\n")
  count := int64(0)
  
  update := time.Tick(time.Second)
  go func() {
    for range update {
      lastCount := atomic.SwapInt64(&count, 0)
      log.Printf("TCP: %v", lastCount / int64(len(message)))
    }
  }()

  go func() {
    for {
      _, writeErr := conn.Write(message)
      if writeErr != nil {
        log.Printf("Write error: %v", writeErr)
        closeAndLog(conn)
        break
      }
    }
  }()

  go func() {
    for {
      bytesRead, readErr := conn.Read(buffer)
      if err != nil {
        log.Printf("Read error: %v", readErr)
        closeAndLog(conn)
        break
      }

      atomic.AddInt64(&count, int64(bytesRead))
    }
  }()
}

func startUdpClient() {
  address := "127.0.0.1:4726"
  log.Printf("Connecting to UDP address %v...", address)

  conn, err := net.Dial("udp", address)
  if err != nil {
    log.Fatalf("Dial error: %v", err)
  }

  log.Printf("Connected to UDP via %v", conn.LocalAddr())
  buffer := make([]byte, 1048576)
  message := []byte("message\n")
  count := int64(0)
  
  update := time.Tick(time.Second)
  go func() {
    for range update {
      lastCount := atomic.SwapInt64(&count, 0)
      log.Printf("UDP: %v", lastCount / int64(len(message)))
    }
  }()

  go func() {
    for {
      _, writeErr := conn.Write(message)
      if writeErr != nil {
        log.Printf("Write error: %v", writeErr)
        closeAndLog(conn)
        break
      }
    }
  }()

  go func() {
    for {
      bytesRead, readErr := conn.Read(buffer)
      if err != nil {
        log.Printf("Read error: %v", readErr)
        closeAndLog(conn)
        break
      }

      atomic.AddInt64(&count, int64(bytesRead))
    }
  }()
}

func main() {
  startTcpClient()
  // startUdpClient()

  done := make(chan os.Signal, 1)
  signal.Notify(done, syscall.SIGINT, syscall.SIGTERM)
  <-done
}