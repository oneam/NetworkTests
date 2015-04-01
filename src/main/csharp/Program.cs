using System;
using System.Net;
using System.Net.Sockets;
using System.Reactive.Linq;
using System.Reactive.Subjects;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Collections.Generic;
using System.Linq;

namespace TestNetwork
{
	class MainClass
	{
		const int Port = 4726;
		const int BufferSize = 65536;
		static readonly byte[] Message = Encoding.UTF8.GetBytes("Message\n");

		public static void Main(string[] args)
		{
			if(args == null) throw new ArgumentNullException("args");
			var shutdown = new ManualResetEvent(false);

			Observable.Interval(TimeSpan.FromSeconds(1)).Subscribe(DisplayCounters);

			foreach(int i in Enumerable.Range(0, 10))
			{
				new Thread(() => SyncLoop(i)).Start();
				new Thread(() => SelectLoop(i)).Start();
				RxLoop(i);
				AsyncLoop(i).ContinueWith(t =>
				{
					if(t.IsFaulted)
						Console.WriteLine(t.Exception);
				});
//				AsyncSelectLoop(i).ContinueWith(t =>
//				{
//					if(t.IsFaulted)
//						Console.WriteLine(t.Exception);
//				});
			}

			shutdown.WaitOne();
		}
		
		static void DisplayCounters(long i)
		{
			if(Counter.Counters.Count == 0)
				return;
			
			Console.WriteLine();
			long total = 0;
			foreach(var counter in Counter.Counters)
			{
				long count = counter.Reset();
				Console.WriteLine("{0}: {1}", counter.Name, count / Message.Length);
				total += count;
			}
			Console.WriteLine("Total: {0}", total / Message.Length);
		}

		static void RxLoop(int i)
		{
			var counter = new Counter(String.Format("Rx {0}", i));
			var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
			var buffer = new byte[BufferSize];

			var writeLoop = new Subject<SocketAsyncEventArgs>();
			writeLoop
				.Do(e => e.SetBuffer(Message, 0, Message.Length))
				.SelectMany(socket.SendRx)
				.Subscribe(writeLoop.OnNext, e => Console.WriteLine("Rx write failed: {0}", e));

			var readLoop = new Subject<SocketAsyncEventArgs>();
			readLoop
				.Do(e => e.SetBuffer(buffer, 0, BufferSize))
				.SelectMany(socket.ReceiveRx)
				.Do(e => counter.Add(e.BytesTransferred))
				.Finally(counter.Dispose)
				.Subscribe(readLoop.OnNext, e => Console.WriteLine("Rx failed: {0}", e));
			
			var connectEventArgs = new SocketAsyncEventArgs();
			connectEventArgs.RemoteEndPoint = new IPEndPoint(IPAddress.Loopback, Port);
			socket
				.ConnectRx(connectEventArgs)
				.Subscribe(e => {
					var readEventArgs = new SocketAsyncEventArgs();
					readLoop.OnNext(readEventArgs);

					var writeEventArgs = new SocketAsyncEventArgs();
					writeLoop.OnNext(writeEventArgs);
				}, e => Console.WriteLine("Rx connect failed: {0}", e));
		}

		static async Task AsyncLoop(int i)
		{
			var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
			var eventArgs = new SocketAsyncEventArgs();
			eventArgs.RemoteEndPoint = new IPEndPoint(IPAddress.Loopback, Port);

			await socket.ConnectTask(eventArgs);
			using(var counter = new Counter(String.Format("Async {0}", i)))
			{
				await Task.WhenAny(AsyncReceiveLoop(socket, counter), AsyncSendLoop(socket));
			}
		}

		static async Task AsyncReceiveLoop(Socket socket, Counter counter)
		{
			var eventArgs = new SocketAsyncEventArgs();
			var buffer = new byte[BufferSize];

			while(true)
			{
				eventArgs.SetBuffer(buffer, 0, BufferSize);
				await socket.ReceiveTask(eventArgs);
				counter.Add(eventArgs.BytesTransferred);
			}
		}	

		static async Task AsyncSendLoop(Socket socket)
		{
			var eventArgs = new SocketAsyncEventArgs();

			while(true)
			{
				eventArgs.SetBuffer(Message, 0, Message.Length);
				await socket.SendTask(eventArgs);
			}
		}	

		static async Task AsyncSelectLoop(int i)
		{
			var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);

			socket.Connect(new IPEndPoint(IPAddress.Loopback, Port));
			socket.Blocking = false;
			using(var counter = new Counter(String.Format("AsyncSelect {0}", i)))
			{
				await Task.WhenAny(AsyncSelectReceiveLoop(socket, counter), AsyncSelectSendLoop(socket));
			}
		}

		static async Task AsyncSelectReceiveLoop(Socket socket, Counter counter)
		{
			var eventArgs = new SocketSelectEventArgs();
			var buffer = new byte[BufferSize];

			while(true)
			{
				eventArgs.SetBuffer(buffer, 0, BufferSize);
				await socket.ReceiveSelectTask(eventArgs);
				counter.Add(eventArgs.BytesTransferred);
			}
		}	

		static async Task AsyncSelectSendLoop(Socket socket)
		{
			var eventArgs = new SocketSelectEventArgs();

			while(true)
			{
				eventArgs.SetBuffer(Message, 0, Message.Length);
				await socket.SendSelectTask(eventArgs);
			}
		}	

		static void SyncLoop(int i)
		{
			using(var counter = new Counter(String.Format("Sync {0}", i)))
			{
				var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
				var buffer = new byte[BufferSize];

				socket.Connect(new IPEndPoint(IPAddress.Loopback, Port));

				Thread send = new Thread(() =>
				{
					while(true)
					{
						socket.Send(Message);
					}
				});

				Thread receive = new Thread(() =>
				{
					while(true)
					{
						var bytesReceived = socket.Receive(buffer);
						counter.Add(bytesReceived);
					}
				});

				receive.Start();
				send.Start();

				receive.Join();
				send.Join();
			}
		}

		static void SelectLoop(int i)
		{
			using(var counter = new Counter(String.Format("Select {0}", i)))
			{
				var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
				var buffer = new byte[BufferSize];
				var checkRead = new List<Socket> { socket };
				var checkWrite = new List<Socket> { socket };

				socket.Connect(new IPEndPoint(IPAddress.Loopback, Port));

				// There is what appears to be a bug in Mono where this non-blocking Connect blocks indefinitely
				socket.Blocking = false;

				while(true)
				{
					SocketError sendError;
					var bytesSent = socket.Send(Message, 0, Message.Length, SocketFlags.None, out sendError);

					SocketError receiveError;
					var bytesRead = socket.Receive(buffer, 0, buffer.Length, SocketFlags.None, out receiveError);

					counter.Add(bytesRead);

					if(bytesRead + bytesSent <= 0)
					{
						checkRead.Clear();
						checkRead.Add(socket);

						checkWrite.Clear();
						checkWrite.Add(socket);

						Socket.Select(checkRead, checkWrite, null, -1);
					}
				}
			}
		}
	}					
}
