package com.thaj.generator.fs2stream

import cats.effect.Effect
import fs2._
import fs2.async.mutable.Queue

import scala.concurrent.ExecutionContext.Implicits.global

@deprecated("" +
  "Layering streaming and usage of `join` gives us the same functionality, and it avoids infinite stream, potential locks and races")
trait Fs2PublisherSubscriber {
  def withQueue[F[_], A](stream: Stream[F, A], f: A => F[Unit], rate: Int)(implicit F: Effect[F]): Stream[F, Unit] = {
    val queue: Stream[F, Queue[F, A]] = Stream.eval(async.boundedQueue[F, A](4))

    queue.flatMap { q =>
      val enqueueStream = stream.to(q.enqueue)
      val dequeStream =  q.dequeue.evalMap(f)

      dequeStream.concurrently(enqueueStream)
    }
  }
}

object Fs2PublisherSubscriber extends Fs2PublisherSubscriber
