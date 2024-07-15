---
title: Architecture
description: Deep dive into the Hyperfoil architecture
weight: 9
---

While we have already explained [basic concepts](/docs/overview/concepts/) in the benchmark and [last quickstart](/docs/getting-started/quickstart1/) shows how to create a custom steps or handlers here we will show how Hyperfoil internally works and give you better idea how to create non-trivial extensions.

## Building the scenario

The road from a YAML file to executing the benchmark starts with creating the _builder tree_. Either the CLI or controller presents this file to the parser (mostly classes from the `io.hyperfoil.core.parser` package) which reads it token-by-token and invoke methods on the `io.hyperfoil.api.config.BenchmarkBuilder` instance. Some parts of the parser are hard-coded, but most of them use reflection - that's why you don't need to write the parser yourselves.

Generally speaking each mapping (`foo: bar`) results in invoking method `foo()` on the builder; if this method accepts an argument (`bar`) the return value could be ignored - the method mutates the builder and that is all. Other methods do not accept any arguments and return another builder instance - the YAML subtree is then applies to this builder. The builder tree then roughly maps to the YAML tree in the original file.

When the YAML is fully read we execute the first phase of building the benchmark itself. We recursively (depth-first) call `prepareBuild()` methods on the tree; these methods are allowed to invoke further mutations on the builder tree, such as adding other [steps and sequences](/docs/overview/concepts/#scenario). An empty (default) implementation of this method is perfectly fine if you don't need anything complex, but if your extension delegates to children extensions it should recursively call the method on children builders. Make sure to iterate through a shallow copy of any collection of children as the children can mutate its parent, failing the iteration.

> Hyperfoil does not track new components created in `prepareBuild()` and therefore it won't call `prepareBuild()` on these if the part of builder tree was already processed - the code creating new components must call the method.

When the first phase completes we invoke the `build()` method on the builder tree. As the build method is invoked recursively again we end up with the _benchmark tree_ that again mirrors the builder tree. The mapping between builders and benchmark components doesn't need to be 1:1, e.g. `StepBuilder` can return several steps, builder can return wrapped components etc. However no mutations are allowed in this phase.

So we end up with the `io.hyperfoil.api.config.Benchmark` instance that holds this tree. It is important to make sure that this is **immutable** and **serializable** as it will be sent over the wire from controller to agents. This means that the components must not reference the builders. A common oversight is using a lambda that uses one of builder's fields - while you only need the actual value of the field the lambda would capture a reference to the builder; this is quite easy to fix by assigning the field value to a local var, though.

## Creating the sessions

Hyperfoil tries to minimize allocations during the benchmark as while Java garbage collector is a good friend of every developer it has a negative effect on the real-time properties of the program. You should use collectors that minimize pause times (such as Shenandoah or ZGC) rather than those that maximize throughput. This is why we allocate all what we could need ahead.

Before the benchmark starts each agent creates all the sessions (based on the `maxSessions` property in case of open-model phases). When `Session.reserve()` is called it calls `reserve()` method on all steps that implement `ResourceUtilizer` interface. In this method the step must call `Session.declareResource()` on all the resources it uses.

Note: in previous versions of Hyperfoil it was necessary to also explicitly declare that the step can write into a session variable, and recursively call the `ResourceUtilizer.reserve()` method on all children components. Since version 0.16 Hyperfoil discovers all `ResourceUtilizer` implementors in the scenario tree; the recursive invocation is no longer necessary.

## Scenario execution

Session execution starts with:

- one instance of each sequence declared in the `initialSequences` list

- one instance of the first sequence in `orderedSequences` list

- one instance of the first sequence in the implicit list of sequences (right under the `scenario: ` declaration - this is mutually exclusive to the options above)

The session keeps a list of active sequence instances, each with an index of the step that should execute next. There can be several instances of the same sequence, up to its concurrency (the number in brackets next to sequence name). Whenever the session is notified (using `Session.proceed()` which schedules `Session.call()` invocation in its executor) it goes through all active instances and calls `Step.invoke()` on the current step.

There are two possible results from the `invoke()` method that returns a boolean:

- `true`: This means that step has been successfuly executed, it's complete and the sequence can continue with the next step (which it will, immediately) or complete if this was the last step.
- `false`: We say that the step was _blocked_ - it cannot execute immediately. It could be either because the step is short of resources (e.g. `httpRequest` cannot get any available connection from the pool) but most often this is because the purpose of this step is to wait for certain condition: variable being set/reaching certain value, request being completed etc. The sequence will **not** progress towards next step and this step's `invoke()` method will be called again (when the `Session.proceed()` is called). An important property is that if the step returns `false` the step must not have any side-effects - it must not fire a request, set a session variable, simply it was a no-op. If the step acquired a resource from a pool it should return it prior to returning `false`.

## Session variables

The session contains a map of variables the scenario uses. The keys are usually strings but this is not mandated; some steps may e.g. choose to use an unique object as the key. The values in the map are wrapper objects that hold a boolean flag whether this variable is set and the value itself. To avoid boxing and unboxing there's a different wrapper for integers and other objects - it's up to step to check the wrapper type and convert the value if necessary.

The map is not manipulated directly - a builder for a step that should work with variable `foo` should call `SessionFactory.readAccess("foo")`, `SessionFactory.intAccess("foo")` or `SessionFactory.objectAccess("foo")` in its `build()` method and pass the received `Access` to the step it creates. The step then operates exclusively using `Access` methods. And example of this can be found in the [getting started: custom steps](/docs/getting-started/quickstart8/) guide.

### Sequence-scoped access

In quickstarts there are examples of _sequence-scoped access_ - variables with `[.]` suffix, e.g. `unset: myVar[.]`. This is used when the variable holds an array of variables (wrappers) created using `ObjectVar.newArray()` or `IntVar.newArray()` in the `ResourcesUtilizer.reserve()` method - a common pattern would be

{{< card code=true header="**Java**" lang="Java" >}}
@Override
public void reserve(Session session) {
    if (!var.isSet(session)) {
        var.setObject(session, ObjectVar.newArray(session, concurrency));
    }
}
{{< /card >}}

The array is often created in a non-conurrent sequence that starts several concurrent instances of another sequence - the `var` would be used with the simple access (without the `[.]` suffix) in the original sequence, and with `[.]` in the concurrent sequences. Each of the concurrent sequences would get a different `SequenceInstance.index()` and with sequence-scoped access these would work on the variable on this position in the array. The step does not need to be tailored specifically to work on sequence-scoped variables; when creating the `Access` instance using `SessionFactory.objectAccess()` the presence of the suffix is automatically checked and the returned `Access` will relay the operations to the slot in the array.

## Session resources

If a step (or a set of cooperating steps) needs to keep some internal state that is not available to users through arbitrary identifiers as session variables these can use concept called _session resources_. It is again an immutable map of objects in the session (while the map itself is immutable the values are meant to be mutated).

To make the code type-safe you start with the `Resource` implementation and matching `ResourceKey`:

{{< card code=true header="**Java**" lang="Java" >}}
public class FooResource implements Session.Resource {
    /* ... */
}

public class FooResourceKey implements Session.ResourceKey<FooResource> {}
{{< /card >}}

The resource key does not need any methods - it is just an unique marker object that will serve as the key in a map. If the resource will be used exclusively in this very step (action, processor...) you can implement the `ResourceKey` in there and use `this` when calling `session.getResource()`:

{{< card code=true header="**Java**" lang="Java" >}}
public class FooStep implements Step, ResourceUtilizer,
        SessionResourceKey<FooStep.FooResource> {
    /* ... */
}
{{< /card >}}

Both session variables and session resources are declared in the `reserve()` method and retrieved (and mutated) in the business method (`invoke()` in the case of a step):

{{< card code=true header="**Java**" lang="Java" >}}
public class FooStep implements Step, ResourceUtilizer {
    private final FooResourceKey resourceKey; // set in constructor

    @Override
    public void reserve(Session session) {
        // we are using supplier rather than creating instance directly because
        // if this sequence is concurrent we will create N resources, the state
        // of concurrent sequences will be isolated by default.
        session.declareResource(resourceKey, FooResource::new);
    }

    @Override
    public void invoke(Session session) {
        FooResource resource = session.getResource(resourceKey);
        /* work on the resource */
    }
}
{{< /card >}}

## Component adapters

There are several types of extension components: steps, actions and processors get extra attention but it is possible to use other interfaces as well. Actions are the simplest of these: these do not require any input (but the session) and do always execute without blocking. Therefore it is possible to use an action on any place where a step or processor would fit. When loading the component by name Hyperfoil automatically wraps the action into an adapter to the target component type.

## Thread-local, agent-local and global data

Besides session variables Hyperfoil offers 3 more levels of memory. Neither of those is limited to the currently executing phase: this data is not reset until the run completes.

First level is the thread-local memory: since each session runs using single executor it is possible to share some data between sessions using the same executor without any need for synchronization. Currently this model supports shared counters (see [addToSharedCounter](/docs/reference/steps/step_addToSharedCounter) and [getSharedCounter](docs/steps/step_getSharedCounter)) and shared map-like objects (see [pushSharedMap](/docs/reference/steps/step_pushSharedMap) and [pullSharedMap](/docs/reference/steps/step_pushSharedMap)). The latter keeps a pool of maps for each executor; when the map is pulled to a session it is removed from the pool, and it's up to the user to return it back. This is useful e.g. for simulating stateful virtual users when we don't want to modify one user concurrently in multiple sessions.

Second level is the agent-local memory. This is intended for caching data that needs to be initialized once and then used throughout the test; the initializing phase should invoke [publishAgentData](/docs/reference/steps/step_publishAgentData). It's up to you to make sure that when this is read using [readAgentData](/docs/reference/steps/step_readAgentData) the data is already available - usually the reading phase should be ordered after the publishing phase using [startAfterStrict](/userguide/benchmark/phases.html) property. Agent data are identified using keys (names); the data for each key can be published only once and cannot be updated afterwards. This limitation is imposed to minimize synchronization of executors.

Third level is the global mem ory. Again this could be used for distributing initialization data but also for gathering data from other agents and threads. The idea is that each thread or agent produces a reduce-able object; Hyperfoil then combines these objects on the agent level (in arbitrary order), sends it to the controller where data from all agents are combined again and the result is distributed back to all agents. As with the agent data you should strictly order the producing and consuming phases, otherwise the data might not be available yet and the run would fail.
Currently Hyperfoil does not provide any general-use steps/actions to work with global data; you should implement [GlobalData.Element](https://github.com/Hyperfoil/Hyperfoil/blob/master/api/src/main/java/io/hyperfoil/api/session/GlobalData.java#L29-L36) in an extension and provide steps to create & publish instances of these.
