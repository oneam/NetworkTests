use std::io::{Read, Write};
use std::net::TcpStream;
use std::sync::mpsc::{Sender, Receiver, channel};
use std::thread;
use std::time::{Instant, Duration};

const MESSAGE: &'static str = "message\n";

fn write_loop(mut stream: TcpStream) {
  let bytes = MESSAGE.as_bytes();
  let local_addr = stream.local_addr().unwrap();

  loop {
    match stream.write(bytes) {
      Ok(0) => {
        println!("Stream closed {:?}", local_addr);
        break;
      },
      Err(err) => {
        println!("Client {:?} failed {:?}", local_addr, err);
        break;
      },
      _ => {},
    }
  }
}

fn read_loop(mut stream: TcpStream, count_tx: Sender<usize>) {
  let mut buffer: [u8; 65536] = [0; 65536];
  let local_addr = stream.local_addr().unwrap();

  loop {
    match stream.read(&mut buffer) {
      Ok(0) => {
        println!("Stream closed {:?}", local_addr);
        break;
      },
      Err(err) => {
        println!("Client {:?} failed {:?}", local_addr, err);
        break;
      },
      Ok(num_bytes) => { count_tx.send(num_bytes).unwrap(); },
    }
  }
}

fn start_client(count_tx: &Sender<usize>) {
  let stream = TcpStream::connect("127.0.0.1:4726").unwrap();
  println!("Connected on {:?}", stream.local_addr().unwrap());

  let read_stream = stream.try_clone().unwrap();
  let read_count_tx = count_tx.clone();
  thread::spawn(move|| { read_loop(read_stream, read_count_tx) });

  let write_stream = stream.try_clone().unwrap();
  thread::spawn(move|| { write_loop(write_stream) });
}

fn count_loop(count_rx: Receiver<usize>) {
  let message_len = MESSAGE.as_bytes().len();
  let mut total: usize = 0;
  let mut last_update = Instant::now();
  let one_second = Duration::from_secs(1);

  loop {
    match count_rx.recv() {
      Ok(count) => {
        total += count;
        let now = Instant::now();
        let since_update = now.duration_since(last_update);
        if since_update > one_second {
          println!("Rate {:?} messages/s", total / message_len);
          total = 0;
          last_update = now;
        }
      },
      Err(err) => {
        println!("Counter failed {:?}", err);
        break;
      },
    }
  }
}

fn main() {
  let (count_tx, count_rx) = channel();
  thread::spawn(move|| { count_loop(count_rx) });

  for _ in 1..10 {
    start_client(&count_tx);
  }

  loop {
    thread::park();
  }
}
