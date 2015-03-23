using System;
using System.Collections.Generic;
using System.Net.Sockets;
using System.Threading;
using System.Text;
using System.Net;
using System.Linq;
using System.Threading.Tasks;

namespace TestNetwork
{
	public static class SocketSelect
	{
		readonly static Dictionary<Socket, SocketSelectEventArgs> ReadEvents = new Dictionary<Socket, SocketSelectEventArgs>();
		readonly static Dictionary<Socket, SocketSelectEventArgs> WriteEvents = new Dictionary<Socket, SocketSelectEventArgs>();
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
				var checkRead = ReadEvents.Keys.ToList();
				checkRead.Add(ResetSocket);

				var checkWrite = WriteEvents.Keys.ToList();

				Socket.Select(checkRead, checkWrite, null, -1);

				foreach(Socket socket in checkRead)
				{
					if(socket == ResetSocket)
						continue;
					var eventArgs = ReadEvents[socket];
					ReadEvents.Remove(socket);
					ProcessEventArgs(socket, eventArgs);
				}

				foreach(Socket socket in checkWrite)
				{
					var eventArgs = WriteEvents[socket];
					WriteEvents.Remove(socket);
					ProcessEventArgs(socket, eventArgs);
				}
			}
		}

		static void ProcessEventArgs(Socket socket, SocketSelectEventArgs eventArgs)
		{
			switch(eventArgs.LastOperation)
			{
			case SocketAsyncOperation.Accept:
				AcceptSelect(socket, eventArgs);
				break;
			case SocketAsyncOperation.Connect:
				ConnectSelect(socket, eventArgs);
				break;
			case SocketAsyncOperation.Disconnect:
				DisconnectSelect(socket, eventArgs);
				break;
			case SocketAsyncOperation.Receive:
				ReceiveSelect(socket, eventArgs);
				break;
			case SocketAsyncOperation.ReceiveFrom:
				ReceiveFromSelect(socket, eventArgs);
				break;
			case SocketAsyncOperation.Send:
				SendSelect(socket, eventArgs);
				break;
			case SocketAsyncOperation.SendTo:
				SendToSelect(socket, eventArgs);
				break;
			case SocketAsyncOperation.None:
				break;
			default:
				throw new NotImplementedException(eventArgs.LastOperation.ToString());
			}
		}

		public static bool AcceptSelect(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			eventArgs.LastOperation = SocketAsyncOperation.Accept;

			bool willComplete = socket.Poll(0, SelectMode.SelectRead);
			if(willComplete)
			{
				eventArgs.AcceptSocket = socket.Accept();
				eventArgs.OnCompleted(socket, eventArgs);
			}
			else
			{
				ReadEvents[socket] = eventArgs;
				Reset();
			}

			return willComplete;
		}

		public static bool ConnectSelect(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			eventArgs.LastOperation = SocketAsyncOperation.Connect;

			if(!socket.Connected) socket.Connect(eventArgs.RemoteEndPoint);

			bool willComplete = socket.Poll(0, SelectMode.SelectWrite);
			if(willComplete)
			{
				eventArgs.OnCompleted(socket, eventArgs);
			}
			else
			{
				WriteEvents[socket] = eventArgs;
				Reset();
			}

			return willComplete;
		}

		public static bool DisconnectSelect(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			eventArgs.LastOperation = SocketAsyncOperation.Disconnect;

			if(socket.Connected) socket.Disconnect(eventArgs.DisconnectReuseSocket);

			bool willComplete = socket.Poll(0, SelectMode.SelectRead);
			if(willComplete)
			{
				eventArgs.OnCompleted(socket, eventArgs);
			}
			else
			{
				ReadEvents[socket] = eventArgs;
				Reset();
			}

			return willComplete;
		}

		public static bool ReceiveSelect(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			if(!socket.Connected) throw new SocketException((int)SocketError.NotConnected);
			eventArgs.LastOperation = SocketAsyncOperation.Receive;

			bool willComplete = socket.Poll(0, SelectMode.SelectRead);
			if(willComplete)
			{
				eventArgs.BytesTransferred = socket.Receive(eventArgs.Buffer, eventArgs.Offset, eventArgs.Count, eventArgs.SocketFlags);
				eventArgs.OnCompleted(socket, eventArgs);
			}
			else
			{
				ReadEvents[socket] = eventArgs;
				Reset();
			}

			return willComplete;
		}

		public static bool ReceiveFromSelect(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			eventArgs.LastOperation = SocketAsyncOperation.ReceiveFrom;

			bool willComplete = socket.Poll(0, SelectMode.SelectRead);
			if(willComplete)
			{
				EndPoint remoteEndPoint = eventArgs.RemoteEndPoint;
				eventArgs.BytesTransferred = socket.ReceiveFrom(eventArgs.Buffer, eventArgs.Offset, eventArgs.Count, eventArgs.SocketFlags, ref remoteEndPoint);
				eventArgs.RemoteEndPoint = remoteEndPoint;
				eventArgs.OnCompleted(socket, eventArgs);
			}
			else
			{
				ReadEvents[socket] = eventArgs;
				Reset();
			}

			return willComplete;
		}

		public static bool SendSelect(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			if(!socket.Connected) throw new SocketException((int)SocketError.NotConnected);
			eventArgs.LastOperation = SocketAsyncOperation.Send;

			bool willComplete = socket.Poll(0, SelectMode.SelectWrite);
			if(willComplete)
			{
				eventArgs.BytesTransferred = socket.Send(eventArgs.Buffer, eventArgs.Offset, eventArgs.Count, eventArgs.SocketFlags);
				eventArgs.OnCompleted(socket, eventArgs);
			}
			else
			{
				WriteEvents[socket] = eventArgs;
				Reset();
			}

			return willComplete;
		}

		public static bool SendToSelect(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			eventArgs.LastOperation = SocketAsyncOperation.SendTo;

			bool willComplete = socket.Poll(0, SelectMode.SelectWrite);
			if(willComplete)
			{
				eventArgs.BytesTransferred = socket.SendTo(eventArgs.Buffer, eventArgs.Offset, eventArgs.Count, eventArgs.SocketFlags, eventArgs.RemoteEndPoint);
				eventArgs.OnCompleted(socket, eventArgs);
			}
			else
			{
				WriteEvents[socket] = eventArgs;
				Reset();
			}

			return willComplete;
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

		public static Task<SocketSelectEventArgs> AcceptSelectTask(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			return Wrap(socket.AcceptSelect, eventArgs);
		}

		public static Task<SocketSelectEventArgs> ConnectSelectTask(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			return Wrap(socket.ConnectSelect, eventArgs);
		}

		public static Task<SocketSelectEventArgs> DisconnectSelectTask(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			return Wrap(socket.DisconnectSelect, eventArgs);
		}

		public static Task<SocketSelectEventArgs> ReceiveSelectTask(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			return Wrap(socket.ReceiveSelect, eventArgs);
		}

		public static Task<SocketSelectEventArgs> ReceiveFromSelectTask(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			return Wrap(socket.ReceiveFromSelect, eventArgs);
		}

		public static Task<SocketSelectEventArgs> SendSelectTask(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			return Wrap(socket.SendSelect, eventArgs);
		}

		public static Task<SocketSelectEventArgs> SendToSelectTask(this Socket socket, SocketSelectEventArgs eventArgs)
		{
			return Wrap(socket.SendToSelect, eventArgs);
		}
	}
}

