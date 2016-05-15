use std::net::{TcpListener, TcpStream, SocketAddr};
use std::io::{Read, Write};
use std::thread;

fn handle_client(mut stream: TcpStream, addr: SocketAddr) {
  let mut buffer: [u8; 65536] = [0; 65536];

  loop {
    match stream.read(&mut buffer) {
      Ok(0) => {
            println!("{:?} disconnected", addr);
        break;
      },
      Err(err) => {
        println!("Read error: {:?}", err);
        break;
      },
      Ok(bytes_read) => {
        match stream.write(&buffer[..bytes_read]) {
          Ok(0) => {
            println!("{:?} disconnected", addr);
            break;
          },
          Err(err) => {
            println!("Write error: {:?}", err);
            break;
          },
          _ => {},
        }
      },
    }
  }
}

fn main() {
  let listener = TcpListener::bind("0.0.0.0:4726").unwrap();
  println!("Listening on {:?}", listener.local_addr().unwrap());

  loop {
    let (stream, addr) = listener.accept().unwrap();
    println!("{:?} connected", addr);
    thread::spawn(move|| { handle_client(stream, addr) });
  }
}
