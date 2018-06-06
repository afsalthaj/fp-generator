# fp-generator
A light weight functional programming abstraction for better handling of data generation usecases that may involve state and batching, along with less memory loads.

# Why an abstraction for data generation?
As a user, we need to specify only the `rule for data generation`, and the `processing function` that is to be done on each instance of data. 

* `rule for data generation` is `given a previous state, what is the new state and value, and the termination condition if any`.

* `processing function` is a function `f` that will be executed on each instance of data (or a batch of data, more on this below)

Sometimes we would like to specify the `rule for data generation` based on a single instance (given a single previous instance of x, how to get a single instance of y), but the `process function` may work only with batches (probably an external API function to send data to an external system such as Kafka, or eventhub). We may also intentionally prefer batch processes for performance reasons too. Batching a data is trivial to build using simple `scala.collection.Seq`. However, if we consider avoiding `out of memory exceptions` + `performance/concurrency`, we will end up relying on streams, and this along with state transitions and batching within the state world, and effects in functional programming can make things a bit more non-trivial. In short, we end up writing more code than specifying the `rule for data generation` and `processing function` to get some trivial data generation and processing done. 

In fact, as you may guess, the core of the abstraction is nothing but a state transition function, `f: S => Option(S, A)` along with an initial state (zero) of type `S`, with a few primitives and combinators on its own, nicely combined with other combinators in fs2 (with cats), allowing you to focus only on generation logic at the client site. While the generator looks similar to state monad, this one is more specific to our use case with a zero value and the fact that next value is always optional, making the termination condition a first class citizen. Internally, the generation of data and processing of generated data are two decoupled processes allowing them to execute concurrently.

To see the usages, please refer to [examples](src/main/scala/com/thaj/generator/examples).

## The abstraction aims at the following:
1) Specify a `rule for data generation` as a simple function, a `processing function` (using `println` here for demo purpose. In real this could be sending data to external systems in an effect IO)  and then call run!

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

All without performance/memory issues.
