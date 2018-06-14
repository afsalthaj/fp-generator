package com.thaj.generator

import cats.data.{Func, Tuple2K}
import cats.effect.Effect
import com.thaj.generator.fs2stream.Fs2PublisherSubscriber
import scalaz.{EitherT, Monad, \/}
import scalaz.syntax.either._
import fs2.Stream

import scala.concurrent.duration._
import cats.implicits._
import scala.concurrent.ExecutionContext.Implicits.global

// A very simple generator service which may look similar to State monad, but more intuitive to use
// for any data generation that involves state.
trait Generator[S, A] { self =>
  def next: S => Option[(S, A)]

  def map[B](f: A => B): Generator[S, B] =
    Generator.create(self.next(_).map { case (s, a) => (s, f(a)) })

  def >>=[B](f: A => Generator[S, B]): Generator[S, B] =
    new Generator[S, B] {
      override def next: S => Option[(S, B)] = s => {
        self.next(s).flatMap{ case(st, v) => f(v).next(st) }
      }
    }

  def flatMap[B](f: A => Generator[S, B]): Generator[S, B] =
    >>=(f)

  def ap[B, C](b: Generator[S, B])(f: (A, B) => C): Generator[S, C] =
    self >>= (bb => b.map(cc => f(bb, cc)))

  def replicateM(n: Int): Generator[S, List[A]] =
    Generator.sequence[S, A](List.fill(n)(self))

  // Stateless compositions

  @deprecated("Use Generator.run(generator(f).withZero(0), generator(g).withZero(1)")
  def run[F[_]: Monad, E](zero: S)(sideEffect: A => F[E \/ Unit]): F[\/[E, Unit]] =
    Generator.unfoldM(zero)(self.next)(sideEffect)
}

object Generator { self =>
  def get[S]: Generator[S, S] =
    Generator.create(s => Some(s, s))

  def create[S, A](f: S => Option[(S, A)]): Generator[S, A] =
    new Generator[S, A] {
      def next: (S) => Option[(S, A)] = f
    }

  def point[S, A](a: A): Generator[S, A]  =
    create[S, A](s => Some(s, a))

  private[generator] def sequence[S, A](list: List[Generator[S, A]]): Generator[S, List[A]] =
    list.foldLeft(self.point[S, List[A]](Nil: List[A]))((acc, a) => a.ap(acc)(_ :: _))

  private[generator] def asFs2Stream[F[_], S, A](z: S, delay: Option[Int])(f: S => Option[(S, A)])(implicit F: Effect[F]): Stream[F, A] = {
    for {
      x <-  Stream.eval[F, Option[(S, A)]](
        F.liftIO {
          cats.effect.IO.sleep(delay.getOrElse(0).milliseconds)
        }.flatMap(_ => F.delay {
          f(z)
        }))
      stream <- x.fold( Stream.empty.covaryAll[F, A]) {
        case (state, value) =>
          Stream.eval[F, A](value.pure[F]) ++ asFs2Stream[F, S, A](state, delay)(f)
      }
    } yield stream
  }

  def runBatch[F[_]: Effect, S, A](n: Int, gens: GeneratorWithZero[S, A]*)(f: List[A] => F[Unit]): F[Unit] =
    Fs2PublisherSubscriber.withQueue[F, List[A]](
      gens.map(t => t.g.replicateM(n).withZero(t.zero).copy(delay = t.delay).asFs2Stream[F]).reduce(_ merge _), f
    ).compile.drain

  def run[F[_]: Effect, S, A](gens: GeneratorWithZero[S, A]*)(f: A => F[Unit]): F[Unit] =
    Fs2PublisherSubscriber.withQueue[F, A](
      gens.map(t => Generator.asFs2Stream[F, S, A](t.zero, t.delay)(t.g.next)).reduce(_ merge _), f
    ).compile.drain

  @deprecated
  private[generator] def unfoldM[F[_]: Monad, S, A, E](z: S)(f: S => Option[(S, A)])(sideEffect: A => F[E \/ Unit]): F[E \/ Unit] = {
    f(z).fold(
      scalaz.Applicative[F].pure(println("Finished sending the data!").right[E])
    ) {
      case (state, value) =>
        EitherT(sideEffect(value)).foldM(
         t => scalaz.Applicative[F].pure(t.left[Unit]),
          _ =>
            unfoldM[F, S, A, E](state)(f)(sideEffect)
        )
    }
  }

  implicit class GeneratorOps[S, A](g: Generator[S, A]) {
    def withZero(z: S): GeneratorWithZero[S, A] = GeneratorWithZero(g, z)
  }
}
