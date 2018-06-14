
# Appendix


-------

## Why not State Monad (scalaz.State) ?<a name = "statemonad?"></a>

How do we manage:
* Termination condition? - Nothing cohesive to handle it. It is `S => (S, A)` and not `S => Option[(S, A)]` though we could make it act like a terminating data generation code.
* Infinite generation? - Comparing `Stream` with `State Monad combinators`, which one do you think is the right way to go?
If we are convinced we need Stream anyways (as the heavy lifting of concurrency, combinators, back-pressure, time delays etc is done by it), why would we even want to form a state monad without encoding of `termination` condition before forming a stream. It doesn't simplify anything, and adds complexity.
* Stack safety? - There is complexity to solve it using tail recursion. We don't need heap to solve this usecase.  
* A state with its combinators ineherently in recursive mode  and not in co-recursive mode + trampolining seems to be an overkill !

Once you form the `State` we still need to do something like this to generate data, giving no reasonable advantage of forming it in the first instance. 

```scala

val s = State.apply[Int, Int](t => ((t + 1), (t + 1)))

unfold(0)(x => Some(s.run(x)))

```

-----

## fp-generator vs ScalaCheck ?

Well, we don't need to use fp-generator if:

* we don't care granular control over behavior of data. In other words, we don't need to control the generation of A and B transactions separately, we want them to have a unified arbitrary behavior with a few value compositons.

* we don't care timing of each generation.
* generation code doesn't involve talking to external systems during processing, and associated back-pressure handling.
* concurrency and order of data isn't a concern at all

All that you care is arbitrary instances of a `case class` and printing it out to test your function/system, then most probably [ScalaCheck](https://github.com/rickynils/scalacheck) is the way to go!


----------


## Integration with **Scalacheck**?  **fs2-scalacheck**? 
Well I tried and bumped into several loopy codes converting `Gen` to `fs2.Stream`.  
But I have also got this question reverberating "why would I even want to..why would I event want to". 
May be for fun and no much value-add! 

The entire point of fp-generator is to have [granular control over your data](https://github.com/afsalthaj/fp-generator/blob/master/datagen_why.md#fp-generator-vs-scalacheck-) (what exactly, when exactly, how many instances with each of them at what rate and how dependent). Note that, with `S => Option[(S, A)]` the seed of generator (`S`) is with in our control, and we define the next value and the termination condition unlike Scalacheck where `S` is `Random Number Generator` essentially!... 
Well still not convinced? Feel free to try along with me and contribute.

## A few fs2 bits hiccups (Optional Read)
If you are looking at the implementation, we used explicit enqueue and dequeue instead of directly using publisher subscriber model in fs2.
The pub sub model in fs2 seems to be a bit flaky. The following code proposed in various blogs and documentations was in fact showing non-determinism.

```scala

  val number = new java.util.concurrent.atomic.AtomicLong(1)

  def withTopic[F[_]](stream: Stream[F, Int], f: Int => F[Unit])(implicit F: Effect[F]): Stream[F, Unit] = {
    val topicStream : Stream[F, Topic[F, Int]] = Stream.eval(fs2.async.topic[F, Int](0))

      topicStream.flatMap { topic =>
        val publisher: Stream[F, Unit] = stream.to(topic.publish)
        val subscriber: Stream[F, Unit] = topic.subscribe(10).evalMap[Unit](f)

        publisher.concurrently(subscriber)
      }
  }

    (0 to 1000).foreach {i =>
      println(s"i is $i")
      number.set(0)

     withTopic[IO](
        fs2.Stream.fromIterator[IO, Int]( List(110, 10).toIterator), int => IO {
          println(Thread.currentThread().getName + " " + int)
          number.getAndAdd(int)
        }
      ).compile.drain.unsafeToFuture()

      Thread.sleep(500)
      println(number.get())
      assert(number.get() == 120)
    }

```

As a quick fix to get things going, we used explicit enqueue and dequeue methods using fs2 itself and that made the app deterministic.
