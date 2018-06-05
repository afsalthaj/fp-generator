package com.thaj.generator.fs2stream

import cats.effect.Effect
import fs2._
import fs2.async.mutable.Topic

import scala.concurrent.ExecutionContext.Implicits.global

trait Fs2PublisherSubscriber {
  def withTopic[F[_], A](stream: Stream[F, A], f: A => F[Unit])(implicit F: Effect[F]): Stream[F, Unit] = {
    def topicStream(head: A): Stream[F, Topic[F, A]] = Stream.eval(fs2.async.topic[F, A](head))
    stream.head.flatMap {
      topicStream(_).flatMap { topic =>
        val publisher: Stream[F, Unit] = stream.to(topic.publish)

        val subscriber: Stream[F, Unit] = topic.subscribe(10).flatMap(t => Stream.eval[F, Unit](f(t)))

        subscriber.concurrently(publisher)
      }
    }
  }
}

// Exists to decouple generation of data and processing data as two separate concurrent process
object Fs2PublisherSubscriber extends Fs2PublisherSubscriber