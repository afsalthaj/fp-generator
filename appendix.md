
# Appendix


-------

## Why not State Monad (scalaz.State) ?<a name = "statemonad?"></a>

How do we manage:
* Termination condition? - Nothing cohesive to handle it. It is S => (S, A) and not Option[(S, A)] though we could make it act like a terminating data generation code.
* Infinite generation? - Comparing `Stream` with `State Monad combinators`, which one do you think is the right way to go?
If we think we need both, why would we even want to form a state monad which doesn't encode `termination`, and then form a stream?
* Stack safety? - There is complexity to solve it using tail recursion. We don't need heap to solve this usecase.  
* A state with its combinators ineherently in recursive mode  and not in co-recursive mode + trampolining seems to be an overkill !

Once you form the `State` we still need to do something like this to generate data, giving no reasonable advantage of forming it in the first instance. 

```scala

val s = State.apply[Int, Int](t => ((t + 1), (t + 1)))

unfold(0)(x => Some(s.run(x)))

```

-----
