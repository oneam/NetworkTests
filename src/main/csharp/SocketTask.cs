using System;
using System.Threading.Tasks;
using System.Net.Sockets;

namespace TestNetwork
{
	public static class SocketTask
	{
		public static Task<SocketAsyncEventArgs> Wrap(Socket socket, SocketAsyncEventArgs eventArgs, Func<SocketAsyncEventArgs, bool> action)
		{
			var completionSource = new TaskCompletionSource<SocketAsyncEventArgs>();

			EventHandler<SocketAsyncEventArgs> onCompleted = null;
			onCompleted = (s, e) =>
			{
				eventArgs.Completed -= onCompleted;
				Complete(eventArgs, completionSource);
			};
			eventArgs.Completed += onCompleted;
			if(!action(eventArgs))
			{
				Complete(eventArgs, completionSource);
			}

			return completionSource.Task;
		}

		static void Complete(SocketAsyncEventArgs eventArgs, TaskCompletionSource<SocketAsyncEventArgs> completionSource)
		{
			if(eventArgs.SocketError == SocketError.Success)
			{
				completionSource.SetResult(eventArgs);
			}
			else
			{
				completionSource.SetException(new SocketException((int)eventArgs.SocketError));
			}
		}

		public static Task<SocketAsyncEventArgs> AcceptTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.AcceptAsync);
		}

		public static Task<SocketAsyncEventArgs> ConnectTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.ConnectAsync);
		}

		public static Task<SocketAsyncEventArgs> DisconnectTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.DisconnectAsync);
		}

		public static Task<SocketAsyncEventArgs> ReceiveTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.ReceiveAsync);
		}

		public static Task<SocketAsyncEventArgs> ReceiveFromTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.ReceiveFromAsync);
		}

		public static Task<SocketAsyncEventArgs> ReceiveMessageFromTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.ReceiveMessageFromAsync);
		}

		public static Task<SocketAsyncEventArgs> SendTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.SendAsync);
		}

		public static Task<SocketAsyncEventArgs> SendPacketsTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.SendPacketsAsync);
		}

		public static Task<SocketAsyncEventArgs> SendToTask(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.SendToAsync);
		}
	}
}

