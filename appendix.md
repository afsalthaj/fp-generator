
# Appendix


-------

## Why not State Monad (scalaz.State) ?<a name = "statemonad?"></a>

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
