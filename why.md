# `Data Generation`

----

# This is not:

* This writeup isn't about managing Random types and its management and compositions.
* It isn't `scala-check` (that handles the logic of generation of data with in itself and provides functionality to control over the data, with nice combinators to compose the generation)


----
# This is:

- The talk will be in the world of `termination conditions`, `effects`, `state of data points`, `concurrency`, `delays`, `backpressure`, `batching` and `compositions`. Essentially, why these concepts are there and how it can be handled. 
- In nutshell, we are going to abstract out `these mechanical side of things` while you generate and process some data. 

---



## Let us define a few simple problems and try to solve each one of them, to understand what are we trying to achieve!


----

## Problem 1


Generate a data set representing volume of waste (as integers) such that volume increases by a value of 2.

Let's be _pragmatic_ and _quickly_ solve the _problem_

```scala
  def generator() = {
    def go(n: Int): List[Int] = 
      if (n >= 100)
        Nil
      else
        n :: go(n + 2)

    go(0)
  }

```

_solved_ ! 


-----

## Bigger termination condition?

Until it reaches 9000000, instead of 100?


```scala
  def generator() = {
    def go(n: Int): List[Int] = 
      if (n > 9000000)
        Nil
      else
        n :: go(n + 1)
        
    go(0)
  }

```

_Stack overflow exception !_

The solution is always stack safe recursion, but we will get there later. 

Before that, let's replace `List` with `scala.Stream`


-----------

## Replace `List` with `scala.Stream`?

```scala
   def generator = {
      def go(n: Int): Stream[Int] =
        if (n > 9000000)
          Stream.Empty
        else
          Stream.cons(n, go(n + 1))
          
      go(0)
    }


```

`generator.foreach(println)` runs fine. That worked!


----

## Let's generalise?

We need this function for all our generations.

```scala
   def generator[A](f: A => A) = {
      def go(n: A): Stream[A] =
        if(???)
         Stream.Empty 
        else
         Stream.cons(n, go(f(n)))
         
      go(???)
    }


```

We need a zero value and a termination condition! 


----

## Zero value?

```scala
   def generator[A](z: A) (f: A => A) = {
      def go(n: A): Stream[A] =
        if(terminationCondition?)
         Stream.Empty 
        else
         Stream.cons(n, go(f(n)))
         
      go(z)
    }

generator(0){ _ + 1 }.take(100).foreach(println)

```

-------


## Termination condition?

Let's accept another fn `g: A => Boolean` ? Well that's redundant. It can be nicely encoded using `Option[A]`


```scala

  def generator[A](z: A)(f: A => Option[A]) = {
    def go(n: Option[A]): Stream[A] =
      if (n.isEmpty)
        Stream.Empty
      else 
        Stream.cons(n, go(f(n)))
         
    go(z)
  }
    

```

---------


## A quick clean up

```scala

def generator[A](z: A)(f: A => Option[A]): Stream[A] =
  f(z) match {
    case Some(h) => Stream.cons(h, generator(h)(f))
    case None => Stream.Empty
  }
    
```

```scala

generator(0) { t => if (t > 29) None else Some(t + 1) }
// works! 

```

---------

## Corecursion

As a side note, we used corecursion, which is a bit different to using `scan` or `foldLeft` that we might think of when solving similar problems. It produces data and doesn't consume to a termination condition.



---------
## Let's separate `State` from `Value`

The generation depends on a previous state, and `State` type can different from `Value`

```scala

def generator[A](z: S)(f: A => Option[(S, A)]): Stream[A] =
  f(z) match {
    case Some(h) => Stream.cons(h, generator(h)(f))
    case None => Stream.Empty
  }
  
generator(0) { 
  t => if (t > 29) 
    None 
  else 
    Some((t + 1), (t + 1))
 }
```

That solved finite/infinite data generation to a certain extent.

------

## Why not State Monad (scalaz.State) ?

How do we manage:

* Infinite generation? - Well it doesn't accumulate the results
* Termination condition? - Nothing cohesive to handle it. It is S => (S, A)
* Stack safety? - There is complexity to solve it using tail recursion.
* We don't need heap to solve this usecase.  
* A state with its combinators ineherently in recursive mode  and not in co-recursive mode + trampolining seems to be an overkill !

More on why state monad not used here, in next slide!

-----


## State doesn't always mean State monad.

Once you form the `State` we still need to do something like this to generate data, giving no reasonable advantage of forming it in the first instance. 

```scala

val s = State.apply[Int, Int](t => ((t + 1), (t + 1)))

unfold(0)(x => Some(s.run(x)))

```

Well, we need to revisit this statement

----


## What if we need to process generated data?
Let's assume that we are doing a println on each data that we generate!

```scala
  def generateAndProcess = {
    def go(n: Int): Unit = 
      if (n > 100) println() else println(go(n + 1))
        
    go(0)
  }
```

That was easy! Sad that we didn't use the previous generic code. 
PS: we lost the control of data, and all possible compositions that we can do with the data. But yea! We solved the immediate problem.


----

## Problem 2
Consider we are generating data that represents volume of waste dumped in certain regions in Australia.

The generated data set should have region1 and region2, where rate of increase of waste in region1 is 2% and that of region2 is 3%. The region2 starts with a waste level of 20.0.


----

## Finding the easiest solution

Before we solve Problem 2, let's generalise the previous bit of code that generates some data and process it (stdout, to be specific)

```scala
  def generateAndProcess[A, S](z: S)(f: S => Option[(A, S)]): Unit = {
    f(z) match {
      case Some((a, ss)) => println(a); generateAndProcess(ss)(f)
      case None          => println()
    }
  }

```

This looks similar to our initial data generation code, although it returns a horrible `unit` and not `Stream[A]`.


------

## Solving usecase 2

```scala
generateAndProcess(0.0){
  t => if (t == 100) None else Some((t+2, t+2)) 
}

generateAndProcess(0.0){
  t => if (t == 100) None else Some((t+3, t+3))
}

```

That works. However,
waste generation in region 2 waited for region 1 to finish dumping :D

-----

## Generalisation

```scala
  def generateAndProcess[A, S](z: S)(f: S => Option[(A, S)])(process: A => Unit): Unit = {
    f(z) match {
      case Some((a, ss)) => process(a); generateAndProcess(ss)(f)
      case None          => ()
    }
  }

```

That looks good enough it seems, but note that, it returns `unit` making the referential transparency in question, and secondly it isn't following `separation of concerns` principle. It `generates` and it also `processes`. 

----

## Problem 3

In problem 2, we generated two data sets separately. How about giving some dependency?

Let add the output of first generation to the second generation, resulting in a few numbers that are definitely greater than the first one, and print them together.


----

## Solution 

That sounds like scala-check `Gen` and we need that kind of compositonality.
To solve above problem, the previous code shouldn't return `unit` or any `side effect`.

```scala
def generator[S, A](z: S)(f: S => Option[(S, A)]): Stream[A] =
  f(z) match {
    case Some((ss, v)) => Stream.cons(v, generator(ss)(f))
    case None => Stream.Empty
  } 
  
  stream.foreach(process) // still returning unit after processing..hmm we lost the power of stream data types as soon it resulted in some effects. Let's get back to it after solving the above problem.

```

We removed `process` function from the previous function resulting in our old solution. 

----

## Solution continued

```scala
val r = generator(0)(t => { val s = Math.abs(Random.nextGaussian() + t) ; Some(s.round.toInt, s.round.toInt) })

// First generator
val gen1 = r.take(10)

// Second generator
val gen2 = r.map(t => t + 10).take(10)

(r.take(10) ++ r.map(t => t + 10).take(10))
.foreach(println)

```
That solved problems to a great extent. We have `generator` function that deals with `state ` transitions and we compose them with `Stream` and processed each data point. 



-----

## Let's take a break. So what did we learn?
At this point, we are convinced with following points.

- Streaming is a right approach. It is lazy and gels well with co-recursion.
- A Stream return instead of a unit return resulted in easier composition.

With this, we have a nice data generation function that handles `state`, `compositions` and `terminations`.


-----

## Wait ! There are more problems to solve

There are more problems in our pipeline to solve:
-  **Concurrency**: How about multiple instances of `S => Option[(S, A)]` in parallel?
-  **Batching** of data.
-  **Time Delay** of data.
-  **Backpressure**: We are sending it to Kafka, or EventHub or some streaming services.
-  **Effects**: Remember, we hammered the problem using `stream.foreach(println)` instead of using a better data type. We finished our `life` early with returning `Unit`!


-------

## PROBLEM 4 (Concurrency)
In problem 3, we generated two datasets that are dependent on each other. However, the entire point of giving dependency is that at a point in time, the number from second generator is greater than the one generated by the first generator. This means the two numbers should be printed to stdout **asynchronously**. 

---------

## Let us solve it using `scala.concurrent.Future`.

The quickest solution to `problem 4` is using Future. This is more or less `Fire and Forget`. However, we still expect, for instance,  the `first number` from `first generator` and `first number` from `second generator` to `stdout` almost at the same time.


---------


```scala
val gen1 = r.take(10).map(t => "generator1" + ": " + t)
val gen2 = r.map(t => t + 10).take(10).map(t => "generator2" + ": " + t)

// (gen1 ++ gen2).take(20).foreach(println) gives synchronised sequential output.

(gen1 ++ gen2).take(20).foreach(t => scala.concurrent.Future{ println(t + " Datetime: " + java.time.Instant.now) } )


```

------


We managed to get some concurrency such that the value from gen2 is greater than gen1 in a given second, with differences of the order of  `milliseconds`

```
generator1: 2 Datetime: 2018-06-12T00:52:19Z
generator1: 4 Datetime: 2018-06-12T00:52:19Z
generator2: 12 Datetime: 2018-06-12T00:52:19Z


```

--------------

## Problem 5 (Batching)
Assume that you need to batch the data that is being produced before doing a funneling to stdout. 


--------

```scala
val gen1 = r.take(10).map(t => "generator1" + ": " + t)
val gen2 = r.map(t => t + 10).take(10).map(t => "generator2" + ": " + t)

(gen1 ++ gen2).take(20).grouped(5).foreach(t => scala.concurrent.Future{ println(t.toList + " Datetime: " + java.time.Instant.now) } )


```
That was easy. 



--------


## Problem 6 (Time Delays)
Assume that in problem 5, you were generating account balances of 2 people.

Generate some data such that rate of change of account balance of person 1 is greater than that of person 2. Both account transaction data should be simultaneous, or in other words, we still want it to be asynchronous. (Needn't batch the data for now)



---------

## Solution (trying...)

```scala
val r = generator(0)(t => { val s = t ; Some(s, s) })
val gen1 = r.take(10).map(t => "generator1" + ": " + t)
val gen2 = r.map(t => t + 10).take(10).map(t => "generator2" + ": " + t)


(gen1 ++  gen2.map {t => { Thread.sleep(4000); t } }
).take(20).foreach(t => 
   Future{ println(t + " Datetime: " + java.time.Instant.now) } )


```

-----------

## `++` ? Before we run the above solution
Consider the first part of the above solutoon:

```scala
val s = (gen1 ++ gen2.map {t => {Thread.sleep(4000); t} })
      
println(s)   

// Stream(generator1: 0, ?)  
// It printed after 4 seconds? We thought streams are lazy?

```

It took 4 seconds to print out `Stream(generator1: 0, ?)`. Change `++` to `#:::` so that only `head` of `gen1` is evaluated and not `head` of `gen2`, and that fixes it.


----------------


Now, see the below results of our solution.

```scala

// With the knowledge of #:::, our solution is

val r = generator(0)(t => { val s = t ; Some(s, s) })
val gen1 = r.take(3).map(t => "generator1" + ": " + t)
val gen2 = r.map(t => t + 10).take(3).map(t => "generator2" + ": " + t)

 (gen1.map{t => {Thread.sleep(1000); t} } #:::
    gen2.map {t => {Thread.sleep(1500); t} }).take(20).foreach(t =>
      Future { 
        println(t + " Datetime: " + java.time.Instant.now + 
          "Thread: " + Thread.currentThread().getName) 
      }
 ) 


```

--------------

## Result? Back into synchronised world

```
generator1: 1 Datetime: 2018-06-12T04:37:46.370ZThread: scala-execution-context-global-11
generator1: 2 Datetime: 2018-06-12T04:37:47.372ZThread: scala-execution-context-global-11
generator2: 11 Datetime: 2018-06-12T04:37:48.878ZThread: scala-execution-context-global-11
generator2: 12 Datetime: 2018-06-12T04:37:50.382ZThread: scala-execution-context-global-11

```
 In between the delays of Generator 1, Generator 2 should run. That didn't happen, instead generator 2 waited !!  

PS: Converting `gen` to `par collections (gen1.par)`  won't work either as it would lead to stdout of `all` right after `max` of all the delays, which is  4 seconds - that's wrong!


-----------
## Reason?
Although processing of each data point is made `effectful` with `Future`, the data generation is still sequential. Hence, the processing of data "waits" and once it gets the data, it doesn't need to switch threads because the allocated thread has become free. We should  make the `data generation` with that potentially supports `concurrency`!

--------

## More problems?
Trying to define all problems and solutions is not practical here. However we need to atleast think about more problems before we convince ourselves that some engineering is required now.

* Assume that you solved the above problem, try and resolve how that is applicable when you need to `batch` the data?
* Assume you have solved the concurrency issues along with batching, try and resolve the problem of `back-pressure` - your target streaming service tells you "Please slow down your generation + processing, because I am overloaded !"


-----

## We bumped into following problems essentially!
* Concurrently processing data generated by  two instances of generator (account transaction of two individuals) was ok, but it will get bloated once we have a dozen individuals.
* Incorporating delays to control the generation per instance affected concurrency because we failed to encode `Future` in Stream[A]. 
* Handling batching along with handling `state+concurrency+delays` will lead to more mechanical code - more low level concurrency primitives.
* This along with solving back pressure  leads to more and more mechanical code (Ex: generate and push the data to concurrent queue, and the dequeuing it and executing it in controlled number of threads)


-----

## Plus more problems

* scala.Stream didn't solve anything much. Ideally, we should be able to encode concurrency effect in the data generation side and we couldn't do it easily.
* Processing scala.Stream changed the return type of `Stream` to `Unit` affecting compositonality. We need to encode the entire program in just streams.
* We used wrong primitives scala.Stream and scala.concurrent.Future [Future](https://gist.github.com/afsalthaj/ddfe60c06fb60eec864e0e4364f1911a))


------------






# fs2!!!



-----------

## Stream realising through effects - concurrency is encoded with in stream!

We found it difficult to encode the fact the every element in the data generation stream may get realised through an effect - essentially a concurrency Effect `F` (let's say future). 

With `fs2.Stream[F, A]`, we get it! Every element `A` in the stream may realise an effect `F`. 


----

## Easier way to run two instances of generations in parallel

We found it difficult to make run the second instance of data generation while the first one was waiting. We can easily get around it using `fs2.stream1.merge(fs2.stream2)`.

The account transaction of person 1 generates can now be concurrently generated with that of person 2. With this, incorporating delays is no more an issue to concurrency.


-----


## No more unit return type

It was unfortunate, that we bumped into `unit`, when generated data of Stream[A] was processed using some side effecting `f`. With fs2, both generation and processing of data is with in `Stream` data type allowing more scalability by being able to compose further.i.e, `Stream[F, A] => Stream[F, Unit]`


-----

## Batching is easy now

with fs2, batching is made more easy and intuitive. Instead of `Stream[A].grouped` returning an `Iterator`, just play with `Stream[F, List[A]]` and the side effecting processing function is then `List[A] => Unit`


-----

## Get rid of Future

With fs2, we can rely on better concurrency effects. Most probably we will use `IO`.
Conversions to `IO` is easy and we should avoid `Future` whenever possible.



----

## Back-pressure

With pub sub model of fs2, or `join` method, or `async.boundedQueue` we handle the back pressure for free ! Essentially queue up `Stream[F, A]` to an async.Queue in fs2, and then`dequeue` it and run it as `Stream[F, Unit]` (processing is essentially send the data concurrently (`F`) to a sink to return `Unit`.
```scala
val dataGen = Stream[F, A] or Stream[F, List[A]]
val process: A => F[Unit] or List[A] => F[Unit]
val queue = async.boundedQueue(5)
val enqueue = dataGen.map(queue.enqueue)
val deqeuue = queue.dequeue.evalMap(process)
enqueue.concurrently(dequeue) // resulting in executing gen & processing in 5 threads to finish and dequeue the next
```
In fact, `fs2` comes with `join` method that abstracts the above behavior for us - luckily!


-------


## Well, mm, Do I need to write fs2 for a data gen?

**No**.. Although fs2 is a good abstraction, we still need to handle quite a bit there. Hence, we should try to make our refrain ourselves from writing too much mechanical code even with fs2 by abstracting it out once and for all.

For this, I have tried to write an abstraction that hides complexities of using it to handle all of fore-mentioned problems. 

https://github.com/afsalthaj/fp-generator

----

## fp-generator internals
* Defined a new abstraction called **`Generator[S, A]`**, which is a lawful monad.
* **`Generator[S, A]`** is internally converted to its batched version through `asBatch` to get **`Generator[S, List[A]]`**. This can be converted to **`fs2.Stream`** through **`generator.asFs2Stream`** (Though users of the abstraction never need to do this)


-------

## fp-generator internals

* **`Generator[S, A]`** is internally converted to **`fs2.Stream[F, A]`** to generate data in parallel, and after processing it using `A => F[Unit]` or `List[A] => F[Unit]`, we get **`fs2.Stream[F, Unit]`**.
* When using fp-generator, we get a **datagen** with
   - State
   - Concurrency
   - Batching
   - Delays
   - Back pressure..

* Usage of fp-generator abstraction won't have anything to do with `fs2.Stream` or any concurrency primitives.

----

## Well, the whole problem is now as easy as!

```scala
 val generator = Generator.create[Int, Int] {
      s =>
        (s < 10).option {
          val ss = s + 100
          (ss, ss)
        }
    }
    
 val generator1 = generator.withZero(0).withDelay(1000)
 val generator2 = generator.map(_ + 1).withZero(100).withDelay(3000)

Generator.run[IO, Int, Int](generator1, generator2)(a => IO(println(a)).unsafeRunSync()

// To batch it, with a size of 10, it is as easy as
Generator.runBatch[IO, Int, Int](10, generator1, generator2)(list => IO(println(lst)).unsafeRunSync

```


-------


Visit https://github.com/afsalthaj/fp-generator for more examples

Thank you!
