# fp-generator

[![Build Status](https://travis-ci.com/afsalthaj/fp-generator.svg?branch=master)](https://travis-ci.com/afsalthaj/fp-generator)

A simple light weight FP abstraction for data generation and processing with granular control of its behavior, managing state, batching, time delays, concurrency and backpressure.

In short, this abstraction allows you to forget about the mechanical coding that is required for any generation and processing application.

Why this abstraction? Get more information [here](docs/datagen_why.md)

To see the working usages, please refer to [examples](src/main/scala/com/thaj/generator/examples).

## Examples:

### Simple Generator

Specify a `rule for data generation` as a simple function and a `processing function` (println for demo purpose) and then call run!

```scala

  val generator = GeneratorLogic.create {
    s => 
      if (s < 10)
        Some (s + 1, s + 1)
      else 
        None
  }
  
  Generator.run[IO, Int, Int](generator.withZero(0)){
    a => IO { println (a) }
  }.unsafeRunSync()
  
  // output
  1
  2
  3
  ...

```

### Multiple Generators

Specify multiple `rule for data generation`, the `processing function`, and then call run. This should generate all the instances of data each having its own termination condition or logic of generation interleaved with each other.

```scala

    val generator1 = GeneratorLogic.create {
      s => {
        (s < 100).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }.withZero(0)

    val generator2 = GeneratorLogic.create {
      s => {
        (s < 10000).option {
          val ss = s + 100
          (ss, ss)
        }
      }
    }.withZero(2000)

    val generator3 = GeneratorLogic.create {
      s => {
        (s < 200000).option {
          val ss = s + 10000
          (ss, ss)
        }
      }
    }.withZero(100000)

    Generator.run[IO, Int, Int](generator1, generator2, generator3)(a => IO { println(a) }).unsafeRunSync()
    
    // output
    1
    110000
    2100
    120000
    2
    ...
    6
    2600
    7
    2700
```

### Batching

Specify a `rule of data generation`, specify a `batch size`, a `processing function` that operates on a batch, and then call run. Here, batching along with state management is handled with in the library. 

```scala

    val generator = GeneratorLogic.create {
      s => {
        (s < 100).option {
          val ss = s + 2
          (ss, ss)
        }
      }
    }


    Generator.runBatch[IO, Int, Int](10, generator.withZero(0))(list => IO { println(list) }).unsafeRunSync()
    
    // output
    List(2, 4, 6, 8, 10, 12, 14, 16, 18, 20)
    List(22, 24, 26, 28, 30, 32, 34, 36, 38, 40)
    ...
    List(82, 84, 86, 88, 90, 92, 94, 96, 98, 100)
    
   
```
PS: Note that when we using `runBatch` instead of `run` the processing function is `List[A] => F[Unit]` instead of `A => F[Unit]`. Ex: You will be using `sendEvents` function of eventhub instead of `sendEvent` in Azure SDK.


### Batching with multiple generators
Specify multiple `rules of generation`, a `batch size`, a `processing function` that operates on a batch, and then call run. Here, batching along with state management is handled with in the library. 

```scala
    val generator1 = GeneratorLogic.create {
      s => {
        (s < 100).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }.withZero(0)

    val generator2 = GeneratorLogic.create {
      s => {
        (s < 10000).option {
          val ss = s + 100
          (ss, ss)
        }
      }
    }.withZero(2000)

    val generator3 = GeneratorLogic.create {
      s => {
        (s < 200000).option {
          val ss = s + 10000
          (ss, ss)
        }
      }
    }.withZero(100000)

    Generator.runBatch[IO, Int, Int](10, generator1, generator2, generator3)(list => IO { println(list) }).unsafeRunSync()
    
    
    // output
    List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    List(110000, 120000, 130000, 140000, 150000, 160000, 170000, 180000, 190000, 200000)
    List(2100, 2200, 2300, 2400, 2500, 2600, 2700, 2800, 2900, 3000)
    ...
    List(4100, 4200, 4300, 4400, 4500, 4600, 4700, 4800, 4900, 5000)
    ...
    List(71, 72, 73, 74, 75, 76, 77, 78, 79, 80)
    List(9100, 9200, 9300, 9400, 9500, 9600, 9700, 9800, 9900, 10000)
    ...
    List(91, 92, 93, 94, 95, 96, 97, 98, 99, 100)
    ...

```
Note that, the batch doesn't mix in values from different generators (unlike the method of using grouped in scala stream or fs2.groupAdjacent). The batch makes sure that it batches values in the same context. 
### Composability
Example: generate x's account transactions along with y's  account transactions, such that x's account balance is always
higher than that of y's.

Refer to `GeneratorComposition` in examples folder to get insights on compositionality, and how it can be used in your usecase.


```scala

    val complexGen =
      for {
        t <- generator1
        y <- generator2.map(_ + t)
      } yield y
      
    Generator.run(generator1, generator2, complexGen)..
```

### Concurrency & Time delays
Ex: The number of account transactions per day for person x is less than person y's, such that y shouldn't wait during the delays in x.

```scala

    val generator1: Generator[Int, Int] =  GeneratorLogic.create {
      s => {
        (s < 1000).option {
          val ss = s + 100
          (ss, ss)
        }
      }
    }.withZero(100)


    // Incorporating time delay in generator2
    val generator2: Generator[Int, Int] =  GeneratorLogic.create {
      s => {
        (s < 10).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }.withZero(1).withDelay(1000)

    val generator3: Generator[Int, Int] =  GeneratorLogic.create {
      s => {
        (s < 4000).option {
          val ss = s + 10
          (ss, ss)
        }
      }
    }.withZero(2000)

    // Thread.sleep(100) for generator2 results in the some of the results of generator2 to be the last ones to stdout
    Generator.run[IO, Int, Int](generator1, generator2, generator3)(a => IO { println(a) }).unsafeRunSync()

// Output
// The output of generator2 is slow, however you can see that it isn't blocking other generators+process in execution.

scala-execution-context-global-12 2010
scala-execution-context-global-13 2020
.....
scala-execution-context-global-16 1000  
....
scala-execution-context-global-17 2
...
scala-execution-context-global-17 3290
...  
...
scala-execution-context-global-11 4000
scala-execution-context-global-16 5
...
scala-execution-context-global-18 9
scala-execution-context-global-16 10

```

The time delays and concurrency works fine when you use `runBatch`. In batch runs with `delay = t`, there will be a delay
of `t seconds` between every batch generation/processing.

### Back pressure
The processing of data involves sending it to external system such as eventhubs, kafka or other streaming services. 
Hence with a fire and forget mechanism, we might be sending either too much or too less data depending on the load of target system, leading to intermittent errors and data loss. 

Internally, the backpressure is handled using async queue such that data generation depends on how fast the data is being processed. Again, we reuse fs2's capability as much as we can and refrain from low level concurrency primitives. 

## More
Take a look at the source code which is a few lines of code, to find more functionalities that you may find useful.

## Potential questions
[Appendix](docs/appendix.md)

## TODO
More tests and laws !
