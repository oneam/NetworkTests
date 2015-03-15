package main

import (
  "io"
  "net"
  "log"
)

func handleConn(c net.Conn) {
  log.Printf("Connected to %v", c.RemoteAddr())
  buffer := make([]byte, 1048576)

  for {
    bytesRead, err := c.Read(buffer)
    if err != nil {
      if err != io.EOF {
        log.Printf("Read error: %v", err)
      }
      closeAndLog(c)
      return
    }

    _, err = c.Write(buffer[:bytesRead])
    if err != nil {
      if err != io.EOF {
        log.Printf("Write error: %v", err)
      }
      closeAndLog(c)
      return
    }
  }
}

func closeAndLog(c net.Conn) {
  err := c.Close()
  if err != nil {
    log.Printf("Close error: %v", err)
  }
}

func main() {
  port := ":4726"
  log.Printf("Starting echo server on port %v...", port)

  listener, err := net.Listen("tcp", port)
  if err != nil {
    log.Fatalf("Listen error: %v", err)
  }

  for {
    conn, err := listener.Accept()
    if err != nil {
      log.Fatalf("Accept error: %v", err)
    }

    go handleConn(conn)
  }
}
