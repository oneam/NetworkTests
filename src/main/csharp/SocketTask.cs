using System;
using System.Threading.Tasks;
using System.Net.Sockets;

namespace TestNetwork
{
	public static class SocketTask
	{
		public static Task<SocketAsyncEventArgs> Wrap(Func<SocketAsyncEventArgs, bool> action, SocketAsyncEventArgs eventArgs)
		{
			var completionSource = new TaskCompletionSource<SocketAsyncEventArgs>();

			EventHandler<SocketAsyncEventArgs> onCompleted = null;
			onCompleted = (s, e) =>
			{
				eventArgs.Completed -= onCompleted;
				if(eventArgs.SocketError == SocketError.Success)
				{
					completionSource.SetResult(eventArgs);
				}
				else
				{
					completionSource.SetException(new SocketException((int)eventArgs.SocketError));
				}
			};
			eventArgs.Completed += onCompleted;
			action(eventArgs);

			return completionSource.Task;
		}

		public static Task<SocketAsyncEventArgs> AcceptTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.AcceptAsync, eventArgs);
		}

		public static Task<SocketAsyncEventArgs> ConnectTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.ConnectAsync, eventArgs);
		}

		public static Task<SocketAsyncEventArgs> DisconnectTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.DisconnectAsync, eventArgs);
		}

		public static Task<SocketAsyncEventArgs> ReceiveTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.ReceiveAsync, eventArgs);
		}

		public static Task<SocketAsyncEventArgs> ReceiveFromTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.ReceiveFromAsync, eventArgs);
		}

		public static Task<SocketAsyncEventArgs> ReceiveMessageFromTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.ReceiveMessageFromAsync, eventArgs);
		}

		public static Task<SocketAsyncEventArgs> SendTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.SendAsync, eventArgs);
		}

		public static Task<SocketAsyncEventArgs> SendPacketsTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.SendPacketsAsync, eventArgs);
		}

		public static Task<SocketAsyncEventArgs> SendToTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.SendToAsync, eventArgs);
		}
	}
}

