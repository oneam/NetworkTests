using System;
using System.Net;
using System.Net.Sockets;
using System.Reactive.Concurrency;
using System.Reactive.Linq;
using System.Reactive.Subjects;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.Collections.Generic;

namespace TestNetwork
{
	class MainClass
	{
		const int BufferSize = 1048576;
		const string Value = "value";

		public static void Main(string[] args)
		{
			var shutdown = new ManualResetEvent(false);

			Observable.Interval(TimeSpan.FromSeconds(1)).Subscribe(DisplayCounters);

			Task.Factory.StartNew(SyncLoop, TaskCreationOptions.LongRunning);
			RxLoop();
			AsyncLoop().ContinueWith(t =>
			{
				if(t.IsFaulted)
					Console.WriteLine(t.Exception);
			});

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
				Console.WriteLine("{0}: {1}", counter.Name, count);
			}
		}

		static void RxLoop()
		{
			var counter = new Counter("Rx");
			var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
			var eventArgs = new SocketAsyncEventArgs();
			var buffer = new byte[BufferSize];
			var valueLength = Encoding.UTF8.GetBytes(Value, 0, Value.Length, buffer, 0);
			eventArgs.RemoteEndPoint = new IPEndPoint(IPAddress.Loopback, 4726);
			eventArgs.SetBuffer(buffer, 0, BufferSize);
			var loop = new Subject<SocketAsyncEventArgs>();
			loop
				.Do(e => e.SetBuffer(0, valueLength))
				.SelectMany(socket.SendRx)
				.Do(e => e.SetBuffer(0, BufferSize))
				.SelectMany(socket.ReceiveRx)
				.Do(e => counter.Increment())
				.Finally(counter.Dispose)
				.Subscribe(loop.OnNext, e => Console.WriteLine("Rx failed: {0}", e));
			
			socket
				.ConnectRx(eventArgs)
				.Subscribe(loop.OnNext, loop.OnError);
		}

		static async Task AsyncLoop()
		{
			using(var counter = new Counter("Async"))
			{
				var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
				var eventArgs = new SocketAsyncEventArgs();
				var buffer = new byte[BufferSize];
				var valueLength = Encoding.UTF8.GetBytes(Value, 0, Value.Length, buffer, 0);
				eventArgs.RemoteEndPoint = new IPEndPoint(IPAddress.Loopback, 4726);
				eventArgs.SetBuffer(buffer, 0, BufferSize);
				await socket.ConnectTask(eventArgs);

				while(true)
				{
					eventArgs.SetBuffer(0, valueLength);
					await socket.SendTask(eventArgs);
					eventArgs.SetBuffer(0, BufferSize);
					await socket.ReceiveTask(eventArgs);
					counter.Increment();
				}
			}
		}

		static void SyncLoop()
		{
			using(var counter = new Counter("Sync"))
			{
				var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
				var buffer = new byte[BufferSize];
				var valueLength = Encoding.UTF8.GetBytes(Value, 0, Value.Length, buffer, 0);

				socket.Connect(new IPEndPoint(IPAddress.Loopback, 4726));

				while(true)
				{
					socket.Send(buffer, valueLength, SocketFlags.None);
					socket.Receive(buffer);
					counter.Increment();
				}
			}
		}
	}					
}
