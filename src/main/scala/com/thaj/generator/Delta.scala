package com.thaj.generator

import Delta.{Changed, Deleted, DeltaOp, New}
import scalaz.\&/
import scalaz.\&/.{Both, That, This}
import scalaz.{Name => _, _}
import Scalaz._
import shapeless.labelled.FieldType

case class Bla(someValue: String)
case class MajorResource1(bla: Bla, name: String)
case class MajorResource2(bla1: Bla,bla2: Bla, name: String)

case class Exchange(
 name: String,
 list1: List[MajorResource1],
 list2: List[MajorResource2],
)

trait HasName[A] {
  def name(a: A): String
}

object HasName {
  def apply[A](implicit ev: HasName[A]): HasName[A] = ev

  implicit val majorResourceName1: HasName[MajorResource1] = _.name
  implicit val majorResourceName2: HasName[MajorResource2] = _.name
  implicit val intName: HasName[Int] = _.toString
}

//
trait Delta[A] {
  def delta: A \&/ A => DeltaOp[A]
}

object Delta {
  def apply[A](implicit ev: Delta[A]): Delta[A] = ev

  case class UpdateInfo(key: String, previous: String, newValue: String)

  object UpdateInfo {
    implicit val updateInfo: Show[UpdateInfo] =
      Show.shows[UpdateInfo](a =>
        s"{ \n field: ${a.key}, \n previous: ${a.previous}, \n new: ${a.newValue} \n }\n"
      )
  }

  type Meta = List[UpdateInfo]

  object Meta {
    def apply(key: String, oldV: String, newV: String): Meta =
      List(UpdateInfo(key, oldV, newV))

    def mapKeys(f: String => String, ma: Meta): Meta =
      ma.map { u => u.copy(key =f(u.key)) }

    def empty: Meta = Nil
  }

  implicit class MetaOps(self: Meta) {
    def mapKeys(f: String => String): Meta =
      Meta.mapKeys(f, self)
  }

  trait DeltaOp[A] { self =>
    def fold[C](
      ifNew: A => C,
      ifChanged: (A, A, Delta.Meta) => C,
      ifUnchanged: A => C,
      ifDeleted: A => C
    ): C =
      this match {
        case New(a) => ifNew(a)
        case Changed(a, b, m) => ifChanged(a, b, m)
        case Unchanged(a) => ifUnchanged(a)
        case Deleted(a) => ifDeleted(a)
      }

    def isNew: Boolean = fold(_ => true, (_, _, _) => false, _ => false, _ => false)
    def isChanged: Boolean = fold(_ => false, (_, _, _) => true, _ => false, _ => false)
    def isUnchanged: Boolean = fold(_ => false, (_, _, _) => false, _ => true, _ => false)
    def isDeleted: Boolean = fold(_ => false, (_, _, _) => false, _ => false, _ => true)
  }

  case class New[A](a: A) extends DeltaOp[A]
  case class Changed[A](a: A, b: A, meta: Meta) extends DeltaOp[A]
  case class Unchanged[A](a: A) extends DeltaOp[A]
  case class Deleted[A](a: A) extends DeltaOp[A]

  def createInstance[A](f: (A, A) => Changed[A] \/ Unchanged[A]): Delta[A] = new Delta[A] {
    override def delta: A \&/ A => DeltaOp[A] = {
      case Both(a, b) => f(a, b) match  {
        case \/-(r) => Unchanged(r.a)
        case -\/(r) => Changed(r.a, r.b, r.meta)
      }
      case This(a) => Deleted(a)
      case That(b) => New(b)
    }
  }

  implicit val deltaMajorResource1: Delta[MajorResource1] =
    Delta.createInstance((a, b) =>
      if (a.bla.someValue == b.bla.someValue)
        Unchanged(a).right[Changed[MajorResource1]]
      else
        Changed(a, b, Meta.apply("bla", a.bla.someValue, b.bla.someValue)).left[Unchanged[MajorResource1]])

  private def asThese[A](a: List[A], b: List[A])(implicit N: HasName[A]): List[A \&/ A] = {
    val left =
     a.map(aa => b.find(bb =>
       N.name(bb) == N.name(aa)) match {
       case None => This(aa)
       case Some(bb) => Both(aa, bb)
     })

    val right =
      b.filterNot(b => a.exists(aa => N.name(aa) == N.name(b) )).map(That(_))

    left ++ right
  }

  implicit class DeltaOps[A](a: List[A])(implicit M: Delta[A], N: HasName[A]) {
    def diffWith(b: List[A]): List[DeltaOp[A]] =
      asThese[A](a, b).map(M.delta)
  }
}

import shapeless._
object Main extends App {

  case class Bla(name: String, value: String)

  case class DamnThing(b: Bla)

  val s = Bla("1", "2")
  val s2 = Bla("1", "3")


  val ss = DamnThing(s)
  val ss2 = DamnThing(s2)

  import Delta._

  val r1 = LabelledGeneric[DamnThing].to(ss)
  val r2 = LabelledGeneric[DamnThing].to(ss2)

 val r: List[DeltaOp[MajorResource1]] = List(MajorResource1(???, ???), MajorResource1(???, ???)).diffWith(
   List(MajorResource1(???, ???), MajorResource1(???, ???))
 )
  /*
    object diffPoly extends LowPriorityInstances1 {
      implicit def xWithProduct[K <: Symbol, V, V1K <: Symbol, V1V, R <: HList](
       implicit W: Witness.Aux[K],
       WV1K: Witness.Aux[V1K],
       LB: LabelledGeneric.Aux[V, R]
      ): Case.Aux[(FieldType[K, V], FieldType[K, V]), Delta.Meta] =
        at{ case (a, b) => if (a == b) Map.empty else Map(W.value.name -> ((a: V).toString -> (b: V).toString)) }
    }

    trait LowPriorityInstances1 extends Poly1 {
      implicit def diffAllLabelledTypes[K <: Symbol, V](
           implicit W: Witness.Aux[K]
         ): Case.Aux[(FieldType[K, V], FieldType[K, V]), Delta.Meta] =
        at{ case (a, b) => if (a == b) Map.empty else Map(W.value.name -> ((a: V).toString -> (b: V).toString)) }
    }


   object FoldPo extends Poly2 {
      implicit val foldAllMeta: Case.Aux[Delta.Meta, Delta.Meta, Delta.Meta] =
        at(_ |+| _)
    }
  */

  // .foldLeft(Map.empty[String, (String, String)])(FoldPo)
  //println(r1.zip(r2).map(diffPoly))
}

//

//
