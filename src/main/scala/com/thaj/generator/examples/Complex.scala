package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator

import scala.util.Random

object Complex {
  case class Swipes(id: String, swipeCount: Double, `type`: String, location: String, floor: Int)

  private val contractSwipes = Generator.create[Double, Double] {
    level =>
      if (level == 100)
        Some(0, 0)
      else {
        val state = Math.min(level + Math.abs(Random.nextGaussian() + 2), 100)
        Some(state, state)
      }
  }

  private val permSwipes = Generator.create[Double, Double] {
    level =>
      if (level == 100)
        Some(0, 0)
      else {
        val state = Math.min(level + Math.abs(Random.nextGaussian() + 3), 100)
        Some(state, state)
      }
  }

  private val dailyPassSwipes = Generator.create[Double, Double] {
    level =>
      if (level == 100)
        Some(0, 0)
      else{
        val state = Math.min(level + Math.abs(Random.nextGaussian() + 10), 100)
        Some(state, state)
      }
  }

  private val conctractSwipeInMelbourne =
    (0 to 21).map(floor => contractSwipes.map(tt => Swipes(floor+"recmel", tt, "ContractSwipe", "Melbourne Building", floor)))

  private val contractSwipesInSydney =
    (0 to 15).map(floor => contractSwipes.map(tt => Swipes(floor+"recsyd", tt + 1, "ContractSwipe", "Sydney Building", floor)))

  private val permSwipeInMelbourne =
    (0 to 21).map(id => permSwipes.map(tt => Swipes(id+"secnmel", tt, "PermSwipe", "Melbourne Building", id)))

  private val dailyPassSwipeInSydney =
    (0 to 15).map(id => dailyPassSwipes.map(tt => Swipes(id+"landsyd", tt, "DailyPassSwipe", "Sydney Bulding", id)))

  private val allSwipes =
    conctractSwipeInMelbourne.map(_.withZero(0).withDelay(3000)) ++
    contractSwipesInSydney.map(_.withZero(0).withDelay(3000)) ++
    permSwipeInMelbourne.map(_.withZero(0).withDelay(3500)) ++
    dailyPassSwipeInSydney.map(_.withZero(0).withDelay(1000))

  def main(args: Array[String]): Unit = {
    Generator.run[IO, Double, Swipes](allSwipes :_*){
      x => IO {println(x)}
    }.unsafeRunSync()
  }
}
