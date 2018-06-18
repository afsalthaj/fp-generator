
# Appendix


-------

## Why not State Monad (scalaz.State) ?<a name = "statemonad?"></a>

Its a bit wordy :

* Termination condition? - Nothing cohesive to handle it. It is `S => (S, A)` and not `S => Option[(S, A)]` though we could make it act like a terminating data generation code.
* Infinite generation? - Comparing `Stream` with `State Monad combinators`, its good to go with streams as we get the power of combinators in fs2.Stream rather than pumping logic with separate data types and it's combinators. Since the core is fs2, adding `State monad` doesn't simplify anything. It just adds complexity.
* Stack safety? - There is complexity to solve it without heap. Inscalaz, there is code to trampoline `State`, and in `Cats` it is inherently trampolined. However, we don't want to send anything to heap to solve this usecase. Everything can be solved with a single stack frame at a time.
* A state with its combinators + trampolining + injecting terminations seemed to be an overkill.

Ready to take in if `State` monad instead of `Generator` monad if it provides something useful in our usecase, and feel free to raise issue/contribute.

-----

## fp-generator vs ScalaCheck ?

We **don't** need to use fp-generator if:

* we don't care granular control over behavior of data. In other words, we don't need to control the generation of A and B transactions separately.

* we don't care timing of each generation.
* generation code doesn't involve talking to external systems during processing, and associated back-pressure handling.
* concurrency and order of data isn't a concern at all

If all that we need is an infinite arbitrary instance of data with a few value compositons (ex: only printing to stdout or write to a file), then most probably [ScalaCheck](https://github.com/rickynils/scalacheck) is the way to go!


----------


## Integration with **Scalacheck**?  **fs2-scalacheck**? 
I tried and bumped into several loopy codes converting `Gen` to `fs2.Stream`.  
But then, why would we even want to do that?..

The entire point of fp-generator is to have [granular control over your data](https://github.com/afsalthaj/fp-generator/blob/master/datagen_why.md#fp-generator-vs-scalacheck-) along with other complexities mentioned in the docs. i.e, what exactly, when exactly, how many instances with each of them at what rate and how much dependent to each other. 

Note that, with `S => Option[(S, A)]` the seed of generator (`S`) is with in our control, and we define the next value and the termination condition unlike Scalacheck where `S` is `Random Number Generator` essentially!... However, we can continue to play around with it, but that was taking time for me.

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


## Performance of fs2 with scala Stream for simple stream usecases.

* fs2 (FP way) and scala.Stream(not really an FP way) work with different concepts apart from both being streams that don't eat the memory. However, we can compare `fs2` with `scala.Stream` for simple usecases.
* However, there could be a minor slowness when using fs2 for usecases that can be solved using scala.Stream.
* The questions with the below code was raised in gitter channel, and it was great to see quick responses. 
* However, we are a bit unlucky that, all the different ways of achieving the same result with fs2.Stream seem to be a bit slower than with scala.Stream. Bear in mind, this is not really a showstopper.


```scala


import cats.effect.{Effect, IO}
import fs2.Stream

object PerformanceComparisonOfFs2WithScalaStream {

  val f: Int => Option[Int] = s =>  if( s > 100000) None else Some(s + 1)

  // Too slower (of the order of 3705310384 ns)
  def asFs2StreamSlower[F[_], S](z: S)(f: S => Option[S])(implicit F: Effect[F]): Stream[F, S] =
    Stream.eval[F, Option[S]](F.delay(f(z))).unNone.flatMap(x => Stream.emit(x) ++ asFs2StreamSlower(x)(f))

  // A bit more faster (of the order of 2666544428 ns)
  def asFs2StreamFaster[F[_], S](z: S)(f: S => Option[S])(implicit F: Effect[F]): Stream[F, S] =
    Stream.emit(f(z)).flatMap(
      _.fold(Stream.empty.covaryAll[F, S])(
        o => Stream.emit(o) ++ asFs2StreamFaster[F, S](o)(f)))

  // More faster (of the order of 1635168702 ns)
  def runToStream[S](f: S => Option[S]): S => scala.Stream[S] = {
    z =>
      def run(z: S): scala.Stream[S] =
        f(z).fold(scala.Stream.empty[S]){ a => scala.Stream.cons(a, run(a)) }

      run(z)
  }


  def main(args: Array[String]): Unit = {

    import cats.implicits._
    
    // Faster
    runToStream(f)(0).traverse[IO, Unit] (a =>  IO { println(Thread.currentThread().getName + " " + a) }).unsafeRunSync()

    // A bit slower
    asFs2StreamFaster[IO, Int](0)(f).evalMap(a =>  IO { println(Thread.currentThread().getName + " " + a)}).compile.drain.unsafeRunSync()

    // Too slower
    asFs2StreamSlower[IO, Int](0)(f).evalMap(a =>  IO { println(Thread.currentThread().getName + " " + a)}).compile.drain.unsafeRunSync()
  }
}


```
