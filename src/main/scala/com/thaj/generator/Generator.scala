package com.thaj.generator

import cats.effect.Effect
import com.thaj.generator.fs2stream.Fs2PublisherSubscriber

import scalaz.{-\/, EitherT, Monad, \/}
import scalaz.syntax.either._
import fs2.{Stream, async}
import fs2.async.mutable.Topic
import cats.implicits._
import scala.concurrent.ExecutionContext.Implicits.global

// A very simple generator service which may look similar to State monad, but more intuitive to use
// for any data generation that involves state.
// This enables the users of the library to focus only on data generation rules and not the
// mechanisms of batching batching (for processing data as a batch) and corresponding state management.
// Refer to examples on usage.
trait Generator[S, A] { self =>
  def zero: S
  def next: S => Option[(S, A)]
  // In this way you can start with a generator service for a single component and chain across
  def map[B](f: A => B): Generator[S, B] = {
    new Generator[S, B] {
      override def next: S => Option[(S,B)] =
        self.next(_).map { case (s, a) => (s, f(a)) }
      override def zero: S = self.zero
    }
  }

  def flatMap[B](f: A => Generator[S, B]): Generator[S, B] =
    >>=(f)

  def >>=[B](f: A => Generator[S, B]): Generator[S, B] =
    new Generator[S, B] {
      override def next: S => Option[(S, B)] = s => {
        self.next(s).flatMap{ case(st, v) => f(v).next(st) }
      }

      override def zero: S = self.zero
    }

  def map2[B, C](b: Generator[S, B])(f: (A, B) => C): Generator[S, C] =
    self >>= (bb => b.map(cc => f(bb, cc)))

  def asBatch(n: Int): Generator[S, List[A]] =
    Generator.sequence[S, A](List.fill(n)(self))

  @deprecated
  def run[F[_]: Monad, E](delay: Long)(sideEffect: A => F[E \/ Unit]): F[\/[E, Unit]] =
    Generator.unfoldM(self.zero)(delay)(this.next)(sideEffect)
}

object Generator { self =>
  def create[S, A](z: => S)(f: S => Option[(S, A)]) = new Generator[S, A] {
    override def next: (S) => Option[(S, A)] = f
    override def zero: S = z
  }

  private[generator] def sharedTopicStream[F[_]: Effect, A](initial: A): Stream[F, Topic[F, A]] =
    Stream.eval(async.topic[F, A](initial))

  def addPublisher[F[_], A](topic: Topic[F, A], value: A): Stream[F, Unit] =
    Stream.emit(value).covary[F].repeat.to(topic.publish)

  // TODO; get rid of dead code initialisation
  private[generator] def sequence[S, A](list: List[Generator[S, A]]): Generator[S, List[A]] = {
    object Err extends Exception("This is dead code, and won't be executed")
    list.foldLeft(create[S, List[A]](throw Err)(s => Some(s, List())))((acc, a) => a.map2(acc)(_ :: _))
  }

  private[generator] def asFs2Stream[F[_], S, A](z: S)(f: S => Option[(S, A)])(implicit F: Effect[F]): Stream[F, A] = {
    Stream.eval[F, Option[(S, A)]](F.delay { f(z) }).flatMap(_.fold(
      Stream.empty.covaryAll[F, A]
    ){
      case (state, value) =>
        Stream.eval[F, A](value.pure[F]) ++ asFs2Stream[F, S, A](state)(f)
    })
  }

  def runBatch[F[_]: Effect, S, A](n: Int, gens: Generator[S, A]*)(f: List[A] => F[Unit]): F[Unit] =
    Fs2PublisherSubscriber.withQueue[F, List[A]](
      gens.map(_.asBatch(n).asFs2Stream[F]).reduce(_ merge _), f
    ).compile.drain

  def run[F[_]: Effect, S, A](gens: Generator[S, A]*)(f: A => F[Unit]): F[Unit] =
    Fs2PublisherSubscriber.withQueue[F, A](
      gens.map(t => Generator.asFs2Stream[F, S, A](t.zero)(t.next)).reduce(_ merge _), f
    ).compile.drain

  @deprecated
  private[generator] def unfoldM[F[_]: Monad, S, A, E](z: S)(delay: Long)(f: S => Option[(S, A)])(sideEffect: A => F[E \/ Unit]): F[E \/ Unit] = {
    f(z).fold(
      scalaz.Applicative[F].pure(println("Finished sending the data!").right[E])
    ) {
      case (state, value) =>
        EitherT(sideEffect(value)).foldM(
         t => scalaz.Applicative[F].pure(t.left[Unit]),
          _ => {
            // Haha, who cares!
            Thread.sleep(delay)
            unfoldM[F, S, A, E](state)(delay)(f)(sideEffect)
          }
        )
    }
  }

  implicit class GeneratorOps[S, A](x: Generator[S, A]) {
    def asFs2Stream[F[_]: Effect]: Stream[F, A] = self.asFs2Stream[F, S, A](x.zero)(x.next)
  }
}
