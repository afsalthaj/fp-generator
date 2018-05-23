package com.thaj.generator

import scalaz.{EitherT, Monad, \/}
import scalaz.syntax.either._
import scalaz.syntax.monad._

// A very simple generator service which may look similar to State monad, but more intuitive to use
// for any data generation with nextValue as an optional state and value,
// and the initial value of state being a primary object in the algebra.
// Sorry if a library exists - learning it is equivalent in time to building this!
trait Generator[S, A] { self =>
  def next: S => Option[(S, A)]
  // In this way you can start with a generator service for a single component and chain across
  def map[B](f: A => B): Generator[S, B] = {
    new Generator[S, B] {
      override def next: S => Option[(S,B)] =
        self.next(_).map { case (s, a) => (s, f(a)) }
    }
  }

  def flatMap[B](f: A => Generator[S, B]): Generator[S, B] =
    new Generator[S, B] {
      override def next: S => Option[(S, B)] = s => {
        val ss: Option[(S, A)] = self.next(s)
        ss.flatMap(t => f(t._2).next(t._1))
      }
    }

  def map2[B, C](b: Generator[S, B])(f: (A, B) => C): Generator[S, C] =
    self.flatMap(bb => b.map(cc => f(bb, cc)))

  // Ex: GeneratorService[A, B].run(1000)(a => sendEventHub(a).map(_ => log("sent data"))(identity)
  def run[F[_]: Monad, E](delay: Long)(sideEffect: A => F[E \/ Unit])(implicit m: Zero[S]): F[\/[E, Unit]] =
    Generator.unfoldM(m.zero)(delay)(this.next)(sideEffect)
}

object Generator {
  def apply[A, B](implicit instance: Generator[A, B]): Generator[A, B] =
    instance

  def unit[State, A](a: => A): Generator[State, A] =
    new Generator[State, A] {
      override def next: State => Option[(State, A)] =
        s => Some((s, a))
    }

  // multiple generator services of S -> A , can be converted to S -> List[A] allowing you
  // to do things like batch insert.
  def sequence[S, A](list: List[Generator[S, A]]): Generator[S, List[A]] =
    list.foldLeft(Generator.unit[S, List[A]](List[A]()))((acc, a) => a.map2(acc)(_ :: _))

  // A seamless finite/infinite data gen
  private def unfoldM[F[_]: Monad, S, A, E](z: S)(delay: Long)(f: S => Option[(S, A)])(sideEffect: A => F[E \/ Unit]): F[E \/ Unit] = {
    f(z).fold(
      println("Finished sending the data!").right[E].pure[F]
    ) {
      case (state, value) =>
        EitherT(sideEffect(value)).foldM(
          _.left[Unit].pure[F],
          _ => {
            // Haha, who cares!
            Thread.sleep(delay)
            unfoldM[F, S, A, E](state)(delay)(f)(sideEffect)
          }
        )
    }
  }
}
