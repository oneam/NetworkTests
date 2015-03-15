package main

import (
  "net"
  "log"
  "time"
  "sync/atomic"
)

func closeAndLog(c net.Conn) {
  err := c.Close()
  if err != nil {
    log.Printf("Close error: %v", err)
  }
}

func main() {
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
      log.Printf("Reads: %v", lastCount)
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
