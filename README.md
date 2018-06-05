# fp-generator -
A light weight functional programming abstraction to make your a bit more easy with recursive data generation involving state and batching.

# Why a library for data generation?
As a user, we need to specify only the `rule for data generation`, and the `processing function` that is to be done on each instance of data. 

* `rule for data generation` is `given a previous state, what is the new state and value, and the termination condition if any`.

* `processing function` is `f` that will be executed on each instance of data (or a batch of data, more on this below)

Sometimes, as a user, we would like to specify the `rule for data generation` based on a single instance (given a single previous instance of x, how to get a single instance of y), but the `process function works` only with batches. Batching a data that involves state is trivial to build using simple `scala List`. However, if we consider avoiding out of memory exceptions and performance/concurrency, we should end up relying on streams, and this along with effects in functional programming can make things a bit more non-trivial, not allowing the user to focus on the just the logic of generation, i.e, we end up writing more than just `rule for data generation` and `processing function`.

To see the usages, please refer to [examples](src/main/scala/com/thaj/generator/examples).

## This library doesn't solve universe. Instead it focusses on following usecases: 
1) User should be able to specify a `rule for data generation` as a simple function, a `processing function` that and then call run!
2) User should be able to specify multiple `rule for data generation`, the `processing function`, and then call run. This should generate all the instances of data having multiple rules (ex; different termination condition) interleaved with each other.
3) User should be able to specify one/multiple `rules of generation`, specify a `batch size`, a `processing function` that operates on a batch, and then call run. Here, batching along with state management is handled with in the library. 

Please refer to examples to have a better understanding.
