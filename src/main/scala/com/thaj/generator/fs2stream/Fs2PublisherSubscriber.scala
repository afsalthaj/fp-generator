package com.thaj.generator.fs2stream

import cats.effect.{Effect, IO}
import fs2._
import fs2.async.mutable.{Queue, Topic}

import scala.concurrent.ExecutionContext.Implicits.global

trait Fs2PublisherSubscriber {
  def enqueueData[F[_], A](q: Queue[F, A], streamData: Stream[F, A])(implicit F: Effect[F]): Stream[F, Unit] =
    streamData.to(q.enqueue)

  def dequeueData[F[_], A](q: Queue[F, A])(implicit F: Effect[F]): Stream[F, A] = q.dequeue

  def withQueue[F[_], A](stream: Stream[F, A], f: A => F[Unit])(implicit F: Effect[F]): Stream[F, Unit] = {
    val queue: Stream[F, Queue[F, A]] = Stream.eval(async.circularBuffer[F, A](100))

    queue.flatMap { q =>
      val enqueueStream = enqueueData(q, stream)
      val dequeStream =   dequeueData(q).evalMap(f)

      dequeStream.concurrently(enqueueStream)
    }
  }
}

object Fs2PublisherSubscriber extends Fs2PublisherSubscriber