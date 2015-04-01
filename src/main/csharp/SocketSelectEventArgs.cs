using System;
using System.Net;
using System.Net.Sockets;
using System.Threading.Tasks;

namespace TestNetwork
{
	public class SocketSelectEventArgs : EventArgs
	{
		public int BytesTransferred { get; internal set; }
		public SocketFlags SocketFlags { get; set; }
		public byte[] Buffer { get; internal set; }
		public int Offset { get; internal set; }
		public int Count { get; internal set; }
		public SocketError SocketError { get; internal set; }

		public event EventHandler<SocketSelectEventArgs> Completed;

		internal void OnCompleted(object socket, SocketSelectEventArgs args)
		{
			if(Completed != null)
			{
				Completed(socket, args);
			}
		}

		public void SetBuffer(byte[] buffer, int offset, int count)
		{
			Buffer = buffer;
			Offset = offset;
			Count = count;
		}

		public void SetBuffer(int offset, int count)
		{
			Offset = offset;
			Count = count;
		}
	}
}

