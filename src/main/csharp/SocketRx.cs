using System;
using System.Net.Sockets;
using System.Reactive.Disposables;
using System.Reactive.Linq;
using System.Reactive;
using System.Threading.Tasks;

namespace TestNetwork
{
	public static class SocketRx
	{
		public static IObservable<SocketAsyncEventArgs> Wrap(Socket socket, SocketAsyncEventArgs eventArgs, Func<SocketAsyncEventArgs, bool> action)
		{
			return Observable.Create<SocketAsyncEventArgs>(observer =>
				{
					EventHandler<SocketAsyncEventArgs> onCompleted = null;
					onCompleted = (s, e) =>
					{
						eventArgs.Completed -= onCompleted;
						Completed(eventArgs, observer);
					};
					eventArgs.Completed += onCompleted;
					if(!action(eventArgs))
					{
						Completed(eventArgs, observer);
					}
					return Disposable.Empty;
				});
		}

		private static void Completed(SocketAsyncEventArgs eventArgs, IObserver<SocketAsyncEventArgs> observer)
		{
			if(eventArgs.SocketError == SocketError.Success)
			{
				observer.OnNext(eventArgs);
				observer.OnCompleted();
			}
			else
			{
				observer.OnError(new SocketException((int)eventArgs.SocketError));
			}
		}

		public static IObservable<SocketAsyncEventArgs> AcceptRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.AcceptAsync);
		}

		public static IObservable<SocketAsyncEventArgs> ConnectRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.ConnectAsync);
		}

		public static IObservable<SocketAsyncEventArgs> DisconnectRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.DisconnectAsync);
		}

		public static IObservable<SocketAsyncEventArgs> ReceiveRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.ReceiveAsync);
		}

		public static IObservable<SocketAsyncEventArgs> ReceiveFromRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.ReceiveFromAsync);
		}

		public static IObservable<SocketAsyncEventArgs> ReceiveMessageFromRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.ReceiveMessageFromAsync);
		}

		public static IObservable<SocketAsyncEventArgs> SendRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.SendAsync);
		}

		public static IObservable<SocketAsyncEventArgs> SendPacketsRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.SendPacketsAsync);
		}

		public static IObservable<SocketAsyncEventArgs> SendToRx(this Socket socket, SocketAsyncEventArgs eventArgs)
		{
			return Wrap(socket, eventArgs, socket.SendToAsync);
		}
	}
}

