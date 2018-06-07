# fp-generator
A simple light weight FP abstraction for data generation requiring asynchronous & concurrent generation and processing of multiple instances of data, 
involving state, batching and processing under effects.

Feel free to skip straight into examples in README to get a quick understanding.

# Why an abstraction for a simple data generation? (Optional Read)
Data generation sounds trivial but many times we end up writing more than just data generation. A change in logic is more or less cumbersome in any application code/script that generates data. In simple terms, the abstraction allows you to focus only on the logic of a data generation and forget about the mechanical coding that is needed to make things work.

As a user, we need to specify only the `rule for data generation`, and the `processing function` that is to be done on each instance of data. 

* `rule for data generation` is `given a previous state, what is the new state and value, and the termination condition if any`.

* `processing function` is a function `f` that will be executed on each instance of data (or a batch of data, more on this below)

Sometimes we would like to specify the `rule for data generation` based on a single instance (given a single previous instance of x, how to get a single instance of y), but the `process function` may work only with batches (probably an external API function to send data to an external system such as Kafka, or eventhub). We may also intentionally prefer batch processes for performance reasons too. Batching a data is trivial to build using simple `scala.collection.Seq`. However, if we consider avoiding `out of memory exceptions` + `performance` + `concurrency`, we will end up relying on streams, and this along with batching with state transitions, and effects in functional programming can make things a bit more non-trivial. In short, we end up writing more code using libraries such as `fs2` than specifying the `rule for data generation` and `processing function` to get some trivial data generation and processing done. 

## Internals (Optional Read)
In fact, as you may guess, the core of the abstraction is nothing but a state transition function, **`f: S => Option(S, A)`** along with an initial state (zero) of type `S`, with a few primitives and combinators on its own, nicely combined with other combinators in **fs2 (with cats)**, allowing you to focus only on generation logic at the client site. While the generator function looks similar to the **state monad**, this one is more specific to our use case with a zero value and an optional next value, making the termination condition a first class citizen. It is worth noting that, internally, the **`generation of data` and `processing of generated data` are two decoupled processes allowing them to execute concurrently**. The `concurrency` bit is fully relied on `fs2`, but `fs2` being a library with a significant surface area, abstracting out the concurrency nuances of fs2 makes sense too.

To see the working usages, please refer to [examples](src/main/scala/com/thaj/generator/examples).

## Examples:
1) Specify a `rule for data generation` as a simple function, a `processing function` (prinln for demo purpose) and then call run!

```scala

  val generator = Generator.create(0) {
    s => 
      if (s > 100)
        Some (s + 1, s + 1)
      else 
        None
  }
  
  Generator.run[IO, Int, Int](generator){
    a => IO { println (a) }
  }.unsafeRunSync()
  
  // output
  1
  2
  3
  4
  5
  6
  7
  8
  9
  10

```

2) Specify multiple `rule for data generation`, the `processing function`, and then call run. This should generate all the instances of data each having its own termination condition or logic of generation interleaved with each other.

```scala

    val generator1 = Generator.create(0) {
      s => {
        (s < 100).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    val generator2 = Generator.create(2000) {
      s => {
        (s < 10000).option {
          val ss = s + 100
          (ss, ss)
        }
      }
    }

    val generator3 = Generator.create(100000) {
      s => {
        (s < 200000).option {
          val ss = s + 10000
          (ss, ss)
        }
      }
    }

    Generator.run[IO, Int, Int](generator1, generator2, generator3)(a => IO { println(a) }).unsafeRunSync()
    
    // output
    1
    110000
    2100
    120000
    2
    130000
    2200
    140000
    3
    150000
    2300
    160000
    4
    170000
    2400
    180000
    5
    190000
    2500
    200000
    6
    2600
    7
    2700
```

3) Specify a `rule of data generation`, specify a `batch size`, a `processing function` that operates on a batch, and then call run. Here, batching along with state management is handled with in the library. 

```scala

    val generator = Generator.create(0) {
      s => {
        (s < 100).option {
          val ss = s + 2
          (ss, ss)
        }
      }
    }


    Generator.runBatch[IO, Int, Int](10, generator)(list => IO { println(list) }).unsafeRunSync()
    
    // output
    List(2, 4, 6, 8, 10, 12, 14, 16, 18, 20)
    List(22, 24, 26, 28, 30, 32, 34, 36, 38, 40)
    List(42, 44, 46, 48, 50, 52, 54, 56, 58, 60)
    List(62, 64, 66, 68, 70, 72, 74, 76, 78, 80)
    List(82, 84, 86, 88, 90, 92, 94, 96, 98, 100)
    
   
```
Note that when we used `run` instead of `runBatch` the function that has to be passed for processing the data changed from
`A => F[Unit]` to `List[A] => F[Unit]`

4) Specify multiple `rules of generation`, a `batch size`, a `processing function` that operates on a batch, and then call run. Here, batching along with state management is handled with in the library. 

```scala
    val generator1 = Generator.create(0) {
      s => {
        (s < 100).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    val generator2 = Generator.create(2000) {
      s => {
        (s < 10000).option {
          val ss = s + 100
          (ss, ss)
        }
      }
    }

    val generator3 = Generator.create(100000) {
      s => {
        (s < 200000).option {
          val ss = s + 10000
          (ss, ss)
        }
      }
    }

    Generator.runBatch[IO, Int, Int](10, generator1, generator2, generator3)(list => IO { println(list) }).unsafeRunSync()
    
    
    // output
    List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    List(110000, 120000, 130000, 140000, 150000, 160000, 170000, 180000, 190000, 200000)
    List(2100, 2200, 2300, 2400, 2500, 2600, 2700, 2800, 2900, 3000)
    List(11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
    List(3100, 3200, 3300, 3400, 3500, 3600, 3700, 3800, 3900, 4000)
    List(21, 22, 23, 24, 25, 26, 27, 28, 29, 30)
    List(4100, 4200, 4300, 4400, 4500, 4600, 4700, 4800, 4900, 5000)
    List(31, 32, 33, 34, 35, 36, 37, 38, 39, 40)
    List(5100, 5200, 5300, 5400, 5500, 5600, 5700, 5800, 5900, 6000)
    List(41, 42, 43, 44, 45, 46, 47, 48, 49, 50)
    List(6100, 6200, 6300, 6400, 6500, 6600, 6700, 6800, 6900, 7000)
    List(51, 52, 53, 54, 55, 56, 57, 58, 59, 60)
    List(7100, 7200, 7300, 7400, 7500, 7600, 7700, 7800, 7900, 8000)
    List(61, 62, 63, 64, 65, 66, 67, 68, 69, 70)
    List(8100, 8200, 8300, 8400, 8500, 8600, 8700, 8800, 8900, 9000)
    List(71, 72, 73, 74, 75, 76, 77, 78, 79, 80)
    List(9100, 9200, 9300, 9400, 9500, 9600, 9700, 9800, 9900, 10000)
    List(81, 82, 83, 84, 85, 86, 87, 88, 89, 90)
    List(91, 92, 93, 94, 95, 96, 97, 98, 99, 100)
    ...

```

## Compositionality
The generators can be composed because it is a monad. This allows us to have some of the instances of data generations dependent on each other.

Ex: To generate x's account transactions along with y's and z's account transactions, such that x's account balance is always
higher than y's account balance. More on this can be found in `GeneratorComposition` in examples folder.
in examples folder.

```scala

    val complexGen =
      for {
        t <- generator1
        y <- generator2.map(_ + t)
      } yield y
      
    Generator.run(generator1, generator2, complexGen)..
```

## Concurrency (for the curious minds)
Internally we use fs2 queues to emulate a pub-sub model, to have generation of data and processing of data as two separate processes.
Essentially you can consider this to be similar to queuing up your generated data to a concurrent hashmap and spinning `n` worker threads (thread pool here is ForkedJoin) 
that polls the queue and execute the processes asynchronously.

Also, each generator is independent of each other in its execution (unless we composed them with each other). 
This is done by converting each generator to a `fs2.Stream` internally, and then reducing the list of streams
by merging them together. This implies, incorporating a `Thread.sleep` in one of the generator's next function
doesn't block other instances of data generations. In that way, we have granular control over the timing of data gen.
At the end of the day, we need nice looking graphs!. Let's see this in action.


```scala

 // Our generator is as simple as specifying a zero val and the state changes
    val generator1: Generator[Int, Int] =  Generator.create(100) {
      s => {
        (s < 1000).option {
          val ss = s + 100
          (ss, ss)
        }
      }
    }


    val generator2: Generator[Int, Int] =  Generator.create(1) {
      s => {
        Thread.sleep(100)
        (s < 10).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    val generator3: Generator[Int, Int] =  Generator.create(2000) {
      s => {
        (s < 4000).option {
          val ss = s + 10
          (ss, ss)
        }
      }
    }

    // Thread.sleep(100) for generator2 results in the some of the results of generator2 to be the last ones to stdout
    Generator.run[IO, Int, Int](generator1, generator2, generator3)(a => IO { println(a) }).unsafeRunSync()

// Output
// The output of generator2 is slow, however you can see that it isn't blocking other generators+process in execution.

/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/bin/java "-javaagent:/Applications/IntelliJ IDEA CE.app/Contents/lib/idea_rt.jar=51343:/Applications/IntelliJ IDEA CE.app/Contents/bin" -Dfile.encoding=UTF-8 -classpath /Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/charsets.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/deploy.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/cldrdata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/dnsns.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/jaccess.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/jfxrt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/localedata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/nashorn.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/sunec.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/sunjce_provider.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/sunpkcs11.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/ext/zipfs.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/javaws.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/jce.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/jfr.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/jfxswt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/jsse.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/management-agent.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/plugin.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/resources.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/lib/rt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/lib/ant-javafx.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/lib/dt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/lib/javafx-mx.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/lib/jconsole.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/lib/packager.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/lib/sa-jdi.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/lib/tools.jar:/Users/d879936/github/fp-generator/target/scala-2.12/classes:/Users/d879936/.sbt/boot/scala-2.12.4/lib/scala-library.jar:/Users/d879936/.ivy2/cache/org.scalaz/scalaz-scalacheck-binding_2.12/bundles/scalaz-scalacheck-binding_2.12-7.2.7.jar:/Users/d879936/.ivy2/cache/org.scalaz/scalaz-iteratee_2.12/bundles/scalaz-iteratee_2.12-7.2.7.jar:/Users/d879936/.ivy2/cache/org.scalaz/scalaz-effect_2.12/bundles/scalaz-effect_2.12-7.2.7.jar:/Users/d879936/.ivy2/cache/org.scalaz/scalaz-core_2.12/bundles/scalaz-core_2.12-7.2.7.jar:/Users/d879936/.ivy2/cache/org.scalaz/scalaz-concurrent_2.12/bundles/scalaz-concurrent_2.12-7.2.7.jar:/Users/d879936/.ivy2/cache/org.scalacheck/scalacheck_2.12/jars/scalacheck_2.12-1.12.6.jar:/Users/d879936/.ivy2/cache/org.scala-sbt/test-interface/jars/test-interface-1.0.jar:/Users/d879936/.ivy2/cache/co.fs2/fs2-core_2.12/jars/fs2-core_2.12-0.10.4.jar:/Users/d879936/.ivy2/cache/co.fs2/fs2-io_2.12/jars/fs2-io_2.12-0.10.4.jar:/Users/d879936/.sbt/boot/scala-2.12.4/lib/scala-reflect.jar:/Users/d879936/.ivy2/cache/org.typelevel/cats-core_2.12/jars/cats-core_2.12-1.1.0.jar:/Users/d879936/.ivy2/cache/org.typelevel/cats-effect_2.12/jars/cats-effect_2.12-0.10.jar:/Users/d879936/.ivy2/cache/org.typelevel/cats-kernel_2.12/jars/cats-kernel_2.12-1.1.0.jar:/Users/d879936/.ivy2/cache/org.typelevel/cats-macros_2.12/jars/cats-macros_2.12-1.1.0.jar:/Users/d879936/.ivy2/cache/org.typelevel/machinist_2.12/jars/machinist_2.12-0.6.2.jar com.thaj.generator.examples.MultipleGeneratorSimpleProcess
scala-execution-context-global-12 2010
scala-execution-context-global-13 2020
scala-execution-context-global-12 2030
scala-execution-context-global-18 200
scala-execution-context-global-13 2040
scala-execution-context-global-13 300
scala-execution-context-global-13 2050
scala-execution-context-global-16 400
scala-execution-context-global-18 2060
scala-execution-context-global-13 500
scala-execution-context-global-12 2070
scala-execution-context-global-18 600
scala-execution-context-global-12 2080
scala-execution-context-global-13 700
scala-execution-context-global-13 2090
scala-execution-context-global-12 800
scala-execution-context-global-11 2100
scala-execution-context-global-12 900
scala-execution-context-global-11 2110
scala-execution-context-global-16 1000  
....
scala-execution-context-global-13 2480  
...
scala-execution-context-global-16 2530
scala-execution-context-global-17 2
scala-execution-context-global-13 2540
scala-execution-context-global-11 2550
scala-execution-context-global-17 2560
scala-execution-context-global-17 2570  
..
scala-execution-context-global-11 3260
scala-execution-context-global-15 3270
scala-execution-context-global-13 3280
scala-execution-context-global-16 3
scala-execution-context-global-17 3290
scala-execution-context-global-17 3300
scala-execution-context-global-11 3310
scala-execution-context-global-15 3320  
...  
...
scala-execution-context-global-13 3390
scala-execution-context-global-15 3400  
...
scala-execution-context-global-11 3430
scala-execution-context-global-17 3440  
...  
...  
..
scala-execution-context-global-18 3660
scala-execution-context-global-18 4  
...
scala-execution-context-global-11 3710  
...  
...
scala-execution-context-global-16 3980
scala-execution-context-global-16 3990
scala-execution-context-global-11 4000
scala-execution-context-global-16 5
scala-execution-context-global-13 6
scala-execution-context-global-12 7
scala-execution-context-global-15 8
scala-execution-context-global-18 9
scala-execution-context-global-16 10

```

## A few fs2 bits hiccups (Optional Read)
If you are looking at the implementation, we used explicit enqueue and dequeue instead of directly using publisher subscriber model in fs2.
The pub sub model in fs2 seems to be a bit flaky. The following code proposed in various blogs and documentations was in fact showing non-determinism.

```scala

  val number = new java.util.concurrent.atomic.AtomicLong(1)

  def withTopic[F[_]](stream: Stream[F, Int], f: Int => F[Unit])(implicit F: Effect[F]): Stream[F, Unit] = {
    val topicStream : Stream[F, Topic[F, Int]] = Stream.eval(fs2.async.topic[F, Int](0))

      topicStream.flatMap { topic =>
        val publisher: Stream[F, Unit] = stream.to(topic.publish)
        val subscriber: Stream[F, Unit] = topic.subscribe(10).evalMap[Unit](f)

        publisher.concurrently(subscriber)
      }
  }

    (0 to 1000).foreach {i =>
      println(s"i is $i")
      number.set(0)

     withTopic[IO](
        fs2.Stream.fromIterator[IO, Int]( List(110, 10).toIterator), int => IO {
          println(Thread.currentThread().getName + " " + int)
          number.getAndAdd(int)
        }
      ).compile.drain.unsafeToFuture()

      Thread.sleep(500)
      println(number.get())
      assert(number.get() == 120)
    }

```

As a quick fix to get things going, we used explicit enqueue and dequeue methods using fs2 itself and that made the app deterministic.

## TODO

The delay in data generation can be currently incorporated using `Thread.sleep` with in the generator function. This implies the sleep time is used by other threads of data generation and data processing. While this is not an idiomatic way to handle time delays, we are working on accepting delay as a parameter to generator.
