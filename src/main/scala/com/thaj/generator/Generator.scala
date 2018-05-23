package com.thaj.generator

import scalaz.{-\/, EitherT, Monad, \/}
import scalaz.syntax.either._
import scalaz.syntax.monad._

// A very simple generator service which may look similar to State monad, but more intuitive to use
// for any data generation with nextValue as an optional state and value,
// and the initial value of state being a primary object in the algebra.
// Disappeared from scalaz from a very old version.
trait Zero[State] {
  def zero: State
}

trait Generator[State, A] extends Zero[State] { self =>
  def next: State => Option[(State, A)]
  // In this way you can start with a generator service for a single component and chain across
  def map[B](f: A => B): Generator[State, B] = {
    new Generator[State, B] {
      override def zero: State = self.zero
      override def next: State => Option[(State,B)] = s => {
        for {
          ss <- self.next(s)
        } yield (ss._1, f(ss._2))
      }
    }
  }

  def flatMap[B](f: A => Generator[State, B]): Generator[State, B] =
    new Generator[State, B] {
      override def zero: State = self.zero
      override def next: State => Option[(State, B)] = s => {
        val ss: Option[(State, A)] = self.next(s)
        ss.flatMap(t => f(t._2).next(t._1))
      }
    }

  def map2[B, C](b: Generator[State, B])(f: (A, B) => C): Generator[State, C] =
    self.flatMap(bb => b.map(cc => f(bb, cc)))

  // Ex: GeneratorService[A, B].run(1000)(a => sendEventHub(a).map(_ => log("sent data"))(identity)
  def run[F[_]: Monad, E](delay: Long)(sideEffect: A => F[E \/ Unit])(f: State => A): F[\/[E, Unit]] =
    Generator.unfoldM(this.zero)(delay)(this.next, f)(sideEffect)
}

object Generator {
  def apply[A, B](implicit instance: Generator[A, B]): Generator[A, B] =
    instance

  def unit[State : Zero, A](a: => A): Generator[State, A] =
    new Generator[State, A] {
      override def zero: State = implicitly[Zero[State]].zero
      override def next: State => Option[(State, A)] = s => Some((s, a))
    }

  // multiple generator services of S -> A , can be converted to S -> List[A] allowing you
  // to do things like batch insert.
  def sequence[S : Zero, A](x: List[Generator[S, A]]): Generator[S, List[A]] =
  x.foldLeft(Generator.unit[S, List[A]](List[A]()))((acc, a) => a.map2(acc)(_ :: _))

  // A seamless finite/infinite data gen
  private def unfoldM[S, A, E, F[_]: Monad](z: S)(delay: Long)(f: S => Option[(S, A)], g: S => A)(sideEffect: A => F[E \/ Unit]): F[E \/ Unit] = {
    EitherT(sideEffect(g(z))).foldM(
      _.left[Unit].pure[F],
      _ => f(z) match {
        case Some((n, _)) =>
          // Throttle the generation and side effecty things!
          Thread.sleep(delay)
          unfoldM[S, A, E, F](n)(delay)(f, g)(sideEffect)
        case None =>
          println("Finished sending the data!").right[E].pure[F]
      }
    )
  }
}
