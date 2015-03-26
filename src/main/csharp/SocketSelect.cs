using System;
using System.Collections.Generic;
using System.Net.Sockets;
using System.Threading;
using System.Text;
using System.Net;
using System.Linq;
using System.Threading.Tasks;
using System.Collections.Concurrent;

namespace TestNetwork
{
	public static class SocketSelect
	{
		readonly static ConcurrentDictionary<Socket, SocketSelectEventArgs> ReadEvents = new ConcurrentDictionary<Socket, SocketSelectEventArgs>();
		readonly static ConcurrentDictionary<Socket, SocketSelectEventArgs> WriteEvents = new ConcurrentDictionary<Socket, SocketSelectEventArgs>();
		readonly static List<Socket> ReadChecks = new List<Socket>();
		readonly static List<Socket> WriteChecks = new List<Socket>();
		readonly static byte[] ResetMessage = Encoding.UTF8.GetBytes("reset");
		readonly static Thread IoThread;
		readonly static Socket ResetSocket;

		static SocketSelect()
		{
			ResetSocket = new Socket(SocketType.Dgram, ProtocolType.Udp);
			ResetSocket.Bind(new IPEndPoint(IPAddress.Loopback, 0));
			IoThread = new Thread(IoLoop);
			IoThread.Start();
		}

		static void Reset()
		{
			ResetSocket.SendTo(ResetMessage, ResetSocket.LocalEndPoint);
		}

		static void IoLoop()
		{
			while(true)
			{
				ReadChecks.Clear();
				ReadChecks.AddRange(ReadEvents.Keys);
				ReadChecks.Add(ResetSocket);

				WriteChecks.Clear();
				WriteChecks.AddRange(WriteEvents.Keys);

				Socket.Select(ReadChecks, WriteChecks, null, -1);

				foreach(Socket socket in ReadChecks)
				{
					if(socket == ResetSocket) continue;
					SocketSelectEventArgs eventArgs;
					if(!ReadEvents.TryRemove(socket, out eventArgs)) continue;
					ReceiveSelect(socket, eventArgs);
				}

				foreach(Socket socket in WriteChecks)
				{
					SocketSelectEventArgs eventArgs;
					if(!WriteEvents.TryRemove(socket, out eventArgs)) continue;
					SendSelect(socket, eventArgs);
				}
			}
		}

		public static bool ReceiveSelect(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			SocketError err;
			bool blocking = socket.Blocking;
			socket.Blocking = false;

			eventArgs.BytesTransferred = socket.Receive(eventArgs.Buffer, eventArgs.Offset, eventArgs.Count, eventArgs.SocketFlags, out err);

			socket.Blocking = blocking;
			eventArgs.Error = err;

			bool wouldBlock = err == SocketError.WouldBlock;
			if(wouldBlock)
			{
				ReadEvents[socket] = eventArgs;
				Reset();
			}
			else
			{
				eventArgs.OnCompleted(socket, eventArgs);
			}

			return wouldBlock;
		}

		public static bool SendSelect(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			SocketError err;
			bool blocking = socket.Blocking;
			socket.Blocking = false;

			eventArgs.BytesTransferred = socket.Send(eventArgs.Buffer, eventArgs.Offset, eventArgs.Count, eventArgs.SocketFlags, out err);

			socket.Blocking = blocking;
			eventArgs.Error = err;

			bool wouldBlock = err == SocketError.WouldBlock;
			if(wouldBlock)
			{
				WriteEvents[socket] = eventArgs;
				Reset();
			}
			else
			{
				eventArgs.OnCompleted(socket, eventArgs);
			}

			return wouldBlock;
		}

		public static Task<SocketSelectEventArgs> Wrap(Func<SocketSelectEventArgs, bool> action, SocketSelectEventArgs eventArgs)
		{
			var completionSource = new TaskCompletionSource<SocketSelectEventArgs>();

			EventHandler<SocketSelectEventArgs> onCompleted = null;
			onCompleted = (s, e) =>
			{
				eventArgs.Completed -= onCompleted;
				completionSource.SetResult(eventArgs);
			};
			eventArgs.Completed += onCompleted;
			action(eventArgs);

			return completionSource.Task;
		}

		public static Task<SocketSelectEventArgs> ReceiveSelectTask(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			return Wrap(socket.ReceiveSelect, eventArgs);
		}

		public static Task<SocketSelectEventArgs> SendSelectTask(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			return Wrap(socket.SendSelect, eventArgs);
		}
	}
}

