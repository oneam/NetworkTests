using System;
using System.Net;
using System.Net.Sockets;
using System.Reactive.Linq;
using System.Reactive.Subjects;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Collections.Generic;

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

			new Thread(SyncLoop).Start();
			new Thread(SelectLoop).Start();
			RxLoop();
			AsyncLoop().ContinueWith(t => { if(t.IsFaulted) Console.WriteLine(t.Exception); });
			AsyncSelectLoop().ContinueWith(t => { if(t.IsFaulted) Console.WriteLine(t.Exception); });

			shutdown.WaitOne();
		}
		
		static void DisplayCounters(long i)
		{
			if(Counter.Counters.Count == 0)
				return;
			
			Console.WriteLine();
			foreach(var counter in Counter.Counters)
			{
				long count = counter.Reset();
				Console.WriteLine("{0}: {1}", counter.Name, count / Message.Length);
			}
		}

		static void RxLoop()
		{
			var counter = new Counter("Rx");
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

		static async Task AsyncLoop()
		{
			using(var counter = new Counter("Async"))
			{
				var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
				var eventArgs = new SocketAsyncEventArgs();
				var buffer = new byte[BufferSize];
				eventArgs.RemoteEndPoint = new IPEndPoint(IPAddress.Loopback, Port);
				eventArgs.SetBuffer(buffer, 0, BufferSize);
				await socket.ConnectTask(eventArgs);

				while(true)
				{
					eventArgs.SetBuffer(Message, 0, Message.Length);
					await socket.SendTask(eventArgs);
					eventArgs.SetBuffer(buffer, 0, BufferSize);
					await socket.ReceiveTask(eventArgs);
					counter.Add(eventArgs.BytesTransferred);
				}
			}
		}

		static Task AsyncSelectLoop()
		{
			var counter = new Counter("AsyncSelect");
			var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
			var buffer = new byte[BufferSize];
			var completion = new TaskCompletionSource<object>();

			Action<SocketSelectEventArgs> writeLoop = null;
			writeLoop = e => 
			{
				e.SetBuffer(Message, 0, Message.Length);
				socket.SendSelectTask(e).ContinueWith(t => 
				{
					if(t.IsFaulted)
					{
						completion.SetException(t.Exception);
						return;
					}
					writeLoop(t.Result);
				});
			};

			Action<SocketSelectEventArgs> readLoop = null;
			readLoop = e => 
			{
				e.SetBuffer(buffer, 0, BufferSize);
				socket.ReceiveSelectTask(e).ContinueWith(t =>
				{
					if(t.IsFaulted)
					{
						completion.SetException(t.Exception);
						return;
					}
					counter.Add(t.Result.BytesTransferred);
					readLoop(t.Result);
				});
			};

			socket.Connect(new IPEndPoint(IPAddress.Loopback, Port));

			var readEventArgs = new SocketSelectEventArgs();
			readLoop(readEventArgs);

			var writeEventArgs = new SocketSelectEventArgs();
			writeLoop(writeEventArgs);

			return completion.Task.ContinueWith(t => counter.Dispose());
		}

		static void SyncLoop()
		{
			using(var counter = new Counter("Sync"))
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

		static void SelectLoop()
		{
			using(var counter = new Counter("Select"))
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
