# `Data Generation`

----

# This is not:

This writeup isn't about managing Random types and its compositions/ `scala-check`.

----
# This is:

- World of `termination conditions`, `effects`, `state of data points`, `concurrency`, `delays`, `backpressure`, `batching` and `compositions`. 
- In nutshell, we are going to abstract out `the mechanical side of things` while you generate and process some data. 

---



## Let us define a few simple problems and try to solve each one of them, to understand what we are trying to achieve!


----

## Problem 1


Generate a set of account balance (as integers) such that balance always increases by a value of 2 (compared to previous value).

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


## A quick clean up <a name = "simpledatagen"></a>

```scala

def generator[A](z: A)(f: A => Option[A]): Stream[A] =
  f(z) match {
    case Some(h) => Stream.cons(h, generator(h)(f))
    case None    => Stream.Empty
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

The generation depends on a previous state, and `State` type can be different from `Value` type.

```scala

def generator[A](z: S)(f: A => Option[(S, A)]): Stream[A] =
  f(z) match {
    case Some(h) => Stream.cons(h, generator(h)(f))
    case None    => Stream.Empty
  }
  
generator(0) { t => if (t > 29) None else Some((t + 1), (t + 1)) }
```

That solved finite/infinite data generation to a certain extent. 
[PS: Why not State Monad?](#statemonad?)

-----



# Processing generated data.
   --  Data generation is not just generation. It is generation  and processing



-----


## Problem 2
Consider we are generating data that represents account balance of 2 individuals, A and B.

The rate of increase of A's account balance is 2% and that of B is 3%. A's account balance starts with a balance of 0 and that of B is 10.


----

## Finding the easiest solution <a name = "returningunit"></a>

Let's define a function for generating and printing (processing) the account balance.

```scala
  def generateAndProcess[A, S](z: S)(f: S => Option[(A, S)])(process: A => Unit): Unit = {
    f(z) match {
      case Some((a, ss)) => process(a); generateAndProcess(ss)(f)
      case None          => ()
    }
  }

```

This looks similar to our [previous code](#simpledatagen), although it returns a horrible `unit` and not `Stream[A]`, but this works.


------

## Solving usecase 2

```scala
generateAndProcess(0.0){
  t => if (t == 100) None else Some((t+2, t+2)) 
}(println)

generateAndProcess(10.0){
  t => if (t == 100) None else Some((t+3, t+3))
}(println)

```

That works. However, the account transactions of B started after A finished. 

----


## Problem 3

In problem 2, we generated two account transactions (changes in account balance) separately. How about giving some dependency?


Make sure that the balance of `B` has some values greater than that of `A`.


----

## Solution 

To solve above problem, the previous code shouldn't return `unit` or any `side effect`. Because returning `Unit` prevents composition.

```scala
def generator[S, A](z: S)(f: S => Option[(S, A)]): Stream[A] =
  f(z) match {
    case Some((ss, v)) => Stream.cons(v, generator(ss)(f))
    case None          => Stream.Empty
  } 
  
  stream.foreach(process) // still returning unit after processing..hmm we lost the power of stream data types as soon it resulted in some effects. Let's get back to it after solving the above problem.

```

We removed `process` function from the previous function resulting in our [old solution](#simpledatagen). 

----


```scala
val r = generator(0)(t => { val s = t + 1) ; Some(s.round.toInt, s.round.toInt) })

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
-  **Concurrency**: How about multiple instances of `S => Option[(S, A)]` concurrently?
-  **Batching** of data.
-  **Delay**.
-  **Backpressure** when sending data to external systems.
-  **Effects**: Remember, we hammered the problem using `stream.foreach(println)` instead of using a better data type. We finished our `life` early with returning `Unit`!


-------

## Problem 4 (Concurrency)
In problem 3, we generated account transactions of A and B that are dependent on each other. However, the entire point of giving dependency is that at a point in time, the account balance of B is greater than that of A.

---------

## Let us solve it using `scala.concurrent.Future`.

The quickest solution to `problem 4` is using Future. This is more or less `Fire and Forget`. However, we still expect, at a point in time, we have balance for both B and A, such that `B > A`.

---------


```scala
val r = generator(0)(t => { val s = t + 1) ; Some(s.round.toInt, s.round.toInt) })
val gen1 = r.take(10).map(t => "A" + ": " + t)
val gen2 = r.map(t => t + 10).take(10).map(t => "B" + ": " + t)

// (gen1 ++ gen2).take(20).foreach(println) gives synchronised sequential output.

(gen1 ++ gen2).take(20).foreach(t => scala.concurrent.Future{ println(t + " Datetime: " + java.time.Instant.now) } )


```

------


Not ideal, however, we somehow managed to get some values of `B` and `A` almost at the same time, such that, B > A.

```
A: 2 Datetime: 2018-06-12T00:52:19Z
A: 4 Datetime: 2018-06-12T00:52:19Z
B: 12 Datetime: 2018-06-12T00:52:19Z


```

--------------

## Problem 5 (Batching)
Assume that you need to batch the data to optimise the processing of data. Ex: Send the account balances to Kafka as a batch of 5. 


--------

```scala
val r = generator(0)(t => { val s = t + 1) ; Some(s, s })
val gen1 = r.take(10).map(t => "A" + ": " + t)
val gen2 = r.map(t => t + 10).take(10).map(t => "B" + ": " + t)

(gen1 ++ gen2).take(20).grouped(5).foreach(t => scala.concurrent.Future{ println(t.toList + " Datetime: " + java.time.Instant.now) } )


```
That was easy. 



--------


## Problem 6 (Time Delays)

Generate some data such that rate of number of transactions of `A` is less than that of `B`. 

Both account transaction data should be simultaneous, or in other words, we still want it to be asynchronous. (Needn't batch the data for now)



---------

## Solution (trying...)

```scala
val r = generator(0)(t => { 
  val s = t + 1
  Some(s, s) 
})

val gen1 = r.take(10).map { 
  t => "A" + ": " + t 
}

val gen2 = r.map(t => t + 10).map { 
  t => "B" + ": " + t
}

(gen1 ++  gen2.map {t => { Thread.sleep(4000); t } }
).foreach(t => 
   Future{ println(t + " Datetime: " + java.time.Instant.now) } )


```

-----------

## `++` ? Before we run the above solution
Consider the first part of the above solutoon:

```scala
val s = (gen1 ++ gen2.map {
  t => {
    Thread.sleep(4000)
    t
  }
})
      
println(s)   

// Stream(generator1: 0, ?)  
// It printed after 4 seconds? We thought streams are lazy?

```

It took 4 seconds to print out `Stream(generator1: 0, ?)`. Change `++` to `#:::` so that only `head` of `gen1` is evaluated and not `head` of `gen2`!


----------------


Now, see the below results of our solution.

```scala

// With the knowledge of #:::, our solution is

val r = generator(0)(t => { 
  val s = t + 1 
  Some(s, s) 
})

val gen1 = r.take(3).map { 
  t => "A" + ": " + t 
}

val gen2 = r.map(t => t + 10).take(3).map { 
  t => "B" + ": " + t 
}

 (gen1.map{t => {Thread.sleep(1000); t} } #:::
    gen2.map {t => {Thread.sleep(1500); t} }).foreach(t =>
      Future { 
        println(t + " Datetime: " + java.time.Instant.now + 
          "Thread: " + Thread.currentThread().getName) 
      }
 ) 


```

--------------

## Result? Back into synchronised world :(

```
A: 1 Datetime: 2018-06-12T04:37:46.370Z Thread: scala-execution-context-global-11
A: 2 Datetime: 2018-06-12T04:37:47.372Z Thread: scala-execution-context-global-11
B: 11 Datetime: 2018-06-12T04:37:48.878Z Thread: scala-execution-context-global-11
B: 12 Datetime: 2018-06-12T04:37:50.382Z Thread: scala-execution-context-global-11

```
Ideally,  the delays of transactions of A shouldn't block B's transactions.

PS: Converting `gen` to `par collections (gen1.par)`  won't work either as it would lead to stdout of atomic emission of all data after `max` of all the delays, which is  4 seconds - that's wrong too!


-----------
## Reason?
Although **processing** of each data point is made `effectful` with `Future`, the **data generation** is still sequential. We should  make the **`data generation`** concuerrent too.

--------

## More problems?
Trying to define all problems and solutions is not practical here. However we need to atleast think about more problems before we convince ourselves that some engineering is required now.

* Assume that you solved the above problem of concurrency, try and resolve how that is applicable when you need to `batch` the data? (Delays between batch?, delays between each instance of batch?)
* Assume you have solved the concurrency issues along with batching, try and resolve the problem of `back-pressure` -i.e, the destination service (eg: Kafka/Eventhub etc) directs you "Please slow down your generation + processing, because I am overloaded !" or "Please increase your speed, I am free!"


-----

## We bumped into following problems essentially!
* Concurrently **processing** account transaction of two individuals was ok, but it will get bloated once we have a dozen individuals.
* Incorporating **delays** to control the generation per instance affected concurrency. 
* Handling **batching** along with handling `state + concurrency + delays` will lead to more mechanical code with potential low level concurrency primitives.
* This along with solving back pressure  leads to more and more mechanical code.


-----

## Plus more problems

* **scala.Stream** didn't solve anything much. Ideally, we should be able to encode concurrency effect in the data generation side that could solve the concurrency issue.
* Processing **scala.Stream** of data resulted in return type of `Unit` affecting compositonality. We need to encode the entire program with just `streams`.
* We used scala.concurrent.Future [Future](https://gist.github.com/afsalthaj/ddfe60c06fb60eec864e0e4364f1911a)) which adds up to the issues of `scala.Stream`


------------






# fs2!!!



-----------

## Stream realising through effects - concurrency is encoded with in stream!

We found it difficult to encode the fact the every element in the data generation stream may get realised through an effect - essentially a concurrency Effect `F` (let's say future). 

With `fs2.Stream[F, A]`, we get this encoding for free. It says, every element `A` in the stream may realise in a `Future` (or with any `F`) effect.


----

## Easier way to run two instances of generations concurrently!

We found it difficult to make generate and process A's and B's transactions together without waiting for each other. We can easily get around this problem using

```scala
fs2.streamofA.merge(fs2.streamofB)
```

The account transaction of person 1 generates can now be concurrently generated with that of person 2. With this, incorporating delays is no more an issue to concurrency.


-----


## No more unit return type

It was unfortunate, that we bumped into [return type of _Unit_](#returningunit) after processing the generated data. 

With fs2, both generation and processing of data is with in `Stream` data type allowing more scalability by being able to compose further.i.e, `Stream[F, A] => Stream[F, Unit]`


-----

## Batching is easy now

* with fs2, batching is made more easy and intuitive. 
* Instead of `Stream[A].grouped` returning an `Iterator`, just play with `Stream[F, List[A]]` and the side effecting processing function is then `List[A] => Unit`.


-----

## Get rid of Future

* We can easily get rid of the [eagerness of Future](https://gist.github.com/afsalthaj/ddfe60c06fb60eec864e0e4364f1911a#file-timetoscrapfuture-scala).
* With fs2, we can rely on better concurrency effects. Most probably we will use `IO`.
* Conversions to `IO` is easy and we should avoid `Future` whenever possible.



----

## Back-pressure

With pub sub model of fs2, or `join` method, or `async.boundedQueue` we handle the back pressure for free ! 
```scala
val dataGen = Stream[F, A] // Stream[F, List[A]] if batched
// Send data to streaming service
val process: A => F[Unit]  //  List[A] => F[Unit] if batched
val queue = async.boundedQueue(5)..
val enqueue: Stream[F, A] = dataGen.map(queue.enqueue)..
val deqeuue: Stream[F, Unit] = queue.dequeue.evalMap(process)..
enqueue.concurrently(dequeue) // resulting in executing gen & processing in 5 threads to finish and dequeue the next
```
In fact, `fs2` comes with `join` method that abstracts the above behavior for us - more abstraction from low level concurrency primitives avoiding data loss, locks and race!


-------


## But.. do we need to write a fs2 pgm for a simple data gen?

**No**.. 
We should try to make our application code look as simple as possible with a few lines.

For this, I have tried to write an abstraction that comes with a new data type `Generator` which works with `fs2` under the hood.

https://github.com/afsalthaj/fp-generator

----

## fp-generator internals (generation)
* Defined a new abstraction called **`Generator[S, A]`**, which is a lawful monad.
* **`Generator[S, A]`** is internally converted to its batched version through `asBatch` to get **`Generator[S, List[A]]`**.
* **`Generator[S, A]`** (or `Generator[A, List[A]]`) is internally converted to **`fs2.Stream[F, A]`** to generate data.
* Processing the generated data using `A => F[Unit]` or `List[A] => F[Unit]` results in  **`fs2.Stream[F, Unit]`**.
-------

## We achieved?

* We achieved data gen 
   - with _granular control over the value and behavior of data points_
   - with _State_
   - with _Concurrency_
   - with _Batching_
   - with _Delays_
   - with _Back pressure_
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
    
 val accountofA = generator.withZero(0).withDelay(1000)
 val accountofB = generator.map(_ + 1).withZero(100).withDelay(3000)

Generator.run[IO, Int, Int](accountofA, accountofB)(a => IO(println(a)).unsafeRunSync()

// or To batch it, with a size of 10, it is as easy as
Generator.runBatch[IO, Int, Int](10, generator1, generator2)(list => IO(println(lst)).unsafeRunSync

```


-------

## fp-generator vs ScalaCheck ?

Well, we don't need to use fp-generator if:

* we don't care granular control over behavior of data. In other words, we don't need to control the generation of A and B transactions separately, we want them to have a unified arbitrary behavior with a few value compositons.

* we don't care timing of each generation.
* generation code doesn't involve talking to external systems during processing, and associated back-pressure handling.
* concurrency and order of data isn't a concern at all

All that you care is arbitrary instances of a `case class` and printing it out to test your function/system, then most probably [ScalaCheck](https://github.com/rickynils/scalacheck) is the way to go!


---------

Visit https://github.com/afsalthaj/fp-generator for more examples

Thank you!



------


