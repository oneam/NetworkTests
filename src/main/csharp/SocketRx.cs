using System;
using System.Net.Sockets;
using System.Reactive.Disposables;
using System.Reactive.Linq;

namespace TestNetwork
{
	public static class SocketRx
	{
		public static IObservable<SocketAsyncEventArgs> Wrap(Func<SocketAsyncEventArgs, bool> action, SocketAsyncEventArgs eventArgs)
		{
			return Observable.Create<SocketAsyncEventArgs>(observer =>
				{
					EventHandler<SocketAsyncEventArgs> onCompleted = null;
					onCompleted = (s, e) =>
					{
						eventArgs.Completed -= onCompleted;
						if(eventArgs.SocketError == SocketError.Success)
						{
							observer.OnNext(eventArgs);
							observer.OnCompleted();
						}
						else
						{
							observer.OnError(new SocketException((int)eventArgs.SocketError));
						}
					};
					eventArgs.Completed += onCompleted;
					action(eventArgs);
					return Disposable.Empty;
				});
		}

		public static IObservable<SocketAsyncEventArgs> AcceptRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.AcceptAsync, eventArgs);
		}

		public static IObservable<SocketAsyncEventArgs> ConnectRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.ConnectAsync, eventArgs);
		}

		public static IObservable<SocketAsyncEventArgs> DisconnectRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.DisconnectAsync, eventArgs);
		}

		public static IObservable<SocketAsyncEventArgs> ReceiveRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.ReceiveAsync, eventArgs);
		}

		public static IObservable<SocketAsyncEventArgs> ReceiveFromRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.ReceiveFromAsync, eventArgs);
		}

		public static IObservable<SocketAsyncEventArgs> ReceiveMessageFromRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.ReceiveMessageFromAsync, eventArgs);
		}

		public static IObservable<SocketAsyncEventArgs> SendRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.SendAsync, eventArgs);
		}

		public static IObservable<SocketAsyncEventArgs> SendPacketsRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.SendPacketsAsync, eventArgs);
		}

		public static IObservable<SocketAsyncEventArgs> SendToRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket.SendToAsync, eventArgs);
		}
	}
}

