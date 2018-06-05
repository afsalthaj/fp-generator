# fp-generator
A functional programming abstraction to make your life easy with recursive data generation involving state and batching.

# Why a library for data generation?
As a user, we need to specify only the rule for data generation, and what is to be done with instance of data.
Sometimes, as a user, we specify the rule for data generation based on a single instance, but the process function works only with batches. Batching a data that involves state is trivial when we don't consider the performance and memory, however, if we memory and performance is an issue, we end up relying on streams, and this along with effects in functional programs can make things a little non-trivial, not allowing the user to focus on the logic of generation.

To see the usages, please refer to [examples](examples).

## This library doesn't solve universe. Instead it focusses on following usecases: 
1) User should be able to specify a rule of data gen (i.e, what is the next value given the previous value and state) as a simple function, a processing function that says what is to be done on each instance of data, and then call run!
2) User should be able to specify multiple rules of generation (what is the next value given the previous value and state), the processing function (function that should execute on each instance of data), and then call run. This should generate all the instances of data having multiple rules (ex; different termination condition) interleaved with each other.
3) User should be able to specify multiple rules of data gen (list of generators), a processing function and then call run!
5) User should be able to specify one/multiple rules of generation, specify a batch size, a processing function that operates on a batch, and then call run. Here, batching along with state management is something that we can abstract out allowing the user to focus only on one thing: Given a previous state, what is the new state and new value.
