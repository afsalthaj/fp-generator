package com.thaj.generator

import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}
import shapeless.labelled.FieldType
import cats.evidence.{As, Is}
import scalaz.{Name => _, _}
import Scalaz._
import com.thaj.generator.Delta.Meta
import Delta._


trait FindMeta[A] {
  def findChangedParams(a: A, b: A): Delta.Meta
}

object FindMeta extends LowPriorityInstances0 {
  def apply[A](implicit ev: FindMeta[A]): FindMeta[A] = ev

  def toKey(raw: String): String = {
    val str = new StringBuilder
    var lastLower = false
    raw.foreach { c =>
      if (c.isLower) {
        lastLower = true
        str += c
      } else {
        if (lastLower) str += '-'
        lastLower = false
        str += c.toLower
      }
    }
    str.toString
  }
}

trait LowPriorityInstances0 extends LowPriorityInstances1 {
  implicit def hNilNamer: FindMeta[HNil] =
    new FindMeta[HNil] {
      def findChangedParams(a: HNil, b: HNil): Delta.Meta = Delta.Meta.empty
    }

  // Needn't rely unused implicits warnings by compiler; its a lie
  implicit def hListThatCanHaveFOfGOfAnyVal[F[_], G[_], A, B, K <: Symbol, T <: HList](
    implicit
    witness: Witness.Aux[K],
    IsList: A Is F[G[B]],
    IsAnyVal: B As AnyVal,
    D: Lazy[FindMeta[T]],
  ): FindMeta[FieldType[K, A] :: T] =
    new FindMeta[FieldType[K, A] :: T] {
      override def findChangedParams(a: FieldType[K, A] :: T, b: FieldType[K, A] :: T): Delta.Meta =
        (if (a.head == b.head)
          Meta.empty
        else
          Meta(witness.value.name, (a.head: A).toString, (b.head: A).toString)) ++ D.value.findChangedParams(a.tail, b.tail)
    }

  implicit def namerA[A, R <: HList](implicit E: LabelledGeneric.Aux[A, R], D: FindMeta[R]): FindMeta[A] = {
    new FindMeta[A] {
      override def findChangedParams(a: A, b: A): Meta = D.findChangedParams(E.to(a), E.to(b))
    }
  }
}


trait LowPriorityInstances1 extends LowPriorityInstances2 {
  implicit def hListWithSimpleAnyVal[A, K <: Symbol, T <: HList](
    implicit
    witness: Witness.Aux[K],
    IsAnyVal: A As AnyVal,
    D: Lazy[FindMeta[T]],
  ): FindMeta[FieldType[K, A] :: T] =
    (a, b) =>
      (if (a == b)
        Meta.empty
      else
        Meta(witness.value.name, (a.head: A).toString, (b.head: A).toString)) ++ D.value.findChangedParams(a.tail, b.tail)
}


trait LowPriorityInstances2 extends LowPriorityInstances3 {
  implicit def hLIstWithFofHListInsideIt[A, K <: Symbol, H, InnerT <: HList, T <: HList](
     implicit
     witness: Witness.Aux[K],
     IsList: H As List[A],
     eachH: LabelledGeneric.Aux[A, InnerT],
     D: Lazy[FindMeta[T]],
     E: Lazy[FindMeta[InnerT]]
  ): FindMeta[FieldType[K, H] :: T] =
    new FindMeta[FieldType[K, H] :: T] {
      override def findChangedParams(a: FieldType[K, H] :: T, b: FieldType[K, H] :: T) : Delta.Meta = {
        val r: Seq[(A, A)] = a.head.asInstanceOf[List[A]].flatMap(aa => b.head.asInstanceOf[List[A]].map(bb => (aa, bb)))
        val r2 = { r.map {case (aa, bb) => E.value.findChangedParams(eachH.to(aa), eachH.to(bb))} }
        r2.reduce(_ ++ _).mapKeys(t => witness.value.name + "." +t ) ++ D.value.findChangedParams(a.tail, b.tail)
      }
    }
}

trait LowPriorityInstances3 extends LowPriorityInstances4 {
  implicit def hListNamerWithHListInsideOfInsideOf[K <: Symbol, H, InnerT <: HList, T <: HList](
     implicit
     witness: Witness.Aux[K],
     eachH: LabelledGeneric.Aux[H, InnerT],
     D: Lazy[FindMeta[T]],
     E: Lazy[FindMeta[InnerT]]
  ): FindMeta[FieldType[K, H] :: T] =
    new FindMeta[FieldType[K, H] :: T] {
      override def findChangedParams(a: FieldType[K, H] :: T, b: FieldType[K, H] :: T) : Delta.Meta = {
       E.value.findChangedParams(eachH.to(a.head.asInstanceOf[H]),
         eachH.to(b.head.asInstanceOf[H])).mapKeys(t => witness.value.name + "." +t) ++
         D.value.findChangedParams(a.tail, b.tail)
      }
    }
}

trait LowPriorityInstances4 {
  implicit def simpleHList[A, K <: Symbol, H, T <: HList](
       implicit
       witness: Witness.Aux[K],
       D: Lazy[FindMeta[T]]
  ): FindMeta[FieldType[K, H] :: T] =
    new FindMeta[FieldType[K, H] :: T] {
      override def findChangedParams(a: FieldType[K, H] :: T, b: FieldType[K, H] :: T) : Delta.Meta =
        (if (a.head == b.head)
          Meta.empty
        else
          Meta(witness.value.name, (a.head: H).toString, (b.head: H).toString)) ++ D.value.findChangedParams(a.tail, b.tail)
    }
}

object BlaXX extends App {
  case class Afsal(dla: String)
  case class OuterAfsal(afsal: Afsal)
  case class OuterAfsalList(listAfsal: List[OuterAfsal], x1: String, x2: String)

  val afsal1 = Afsal("")
  val afsal2 = Afsal("sdasd")
  val afsal3 = Afsal("afsal")

  val o1 = OuterAfsal(afsal1)
  val o2 = OuterAfsal(afsal2)
  val o3 = OuterAfsal(afsal3)


  val o4 = OuterAfsalList(List(o1, o2), "1", "a")
  val o5 = OuterAfsalList(List(o3), "2", "b")

  println(FindMeta[OuterAfsalList].findChangedParams(o4, o5).map(_.shows))
  // Map(listAfsal.afsal.dla -> (,afsal), x1 -> (1,2), x2 -> (a,b))

}