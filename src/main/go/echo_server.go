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

func handlePacketConn(c net.PacketConn) {
  buffer := make([]byte, 1048576)
  seen := make(map[string]int)

  for {
    bytesRead, addr, err := c.ReadFrom(buffer)
    if err != nil {
      log.Printf("Packet read error: %v", err)
    }

    client := addr.String()
    i := seen[client]
    if i == 0 {
      seen[client] = 1
      log.Printf("Received packet from %v", addr)
    }

    _, err = c.WriteTo(buffer[:bytesRead], addr)
    if err != nil {
      log.Printf("Packet write error: %v", err)
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

  packetConn, err := net.ListenPacket("udp", port)
  if err != nil {
    log.Fatalf("Listen error: %v", err)
  }

  go handlePacketConn(packetConn)

  for {
    conn, err := listener.Accept()
    if err != nil {
      log.Fatalf("Accept error: %v", err)
    }

    go handleConn(conn)
  }
}
