using System;
using System.Collections.Generic;
using System.Threading;
using System.Dynamic;

namespace TestNetwork
{
	class Counter : IDisposable
	{
		public static List<Counter> Counters = new List<Counter>();

		public Counter(string name)
		{
			Name = name;
			lock(Counters)
			{
				Counters.Add(this);
			}
		}

		public string Name;
		public long Count;

		public long Increment()
		{
			return Interlocked.Increment(ref Count);
		}

		public long Add(long value)
		{
			return Interlocked.Add(ref Count, value);
		}

		public long Reset()
		{
			return Interlocked.Exchange(ref Count, 0L);
		}

		#region IDisposable implementation

		public void Dispose()
		{
			lock(Counters)
			{
				if(Counters.Contains(this))
				{
					Counters.Remove(this);
				}
			}
		}

		#endregion
	}
}

