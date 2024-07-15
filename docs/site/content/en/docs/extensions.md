---
title: Extensions
description: How to develop your own extensions
weight: 7
---

You have probably already read the [Custom steps and handlers quickstart](/docs/getting-started/quickstart8/) which shows how to create a simple component. It can get more tricky when the component embeds other components, though.

The build of scenario happens in two phases. In first phase the sequences, steps and components call method `prepareBuild()`. Most often that method uses the default (empty) implementation, but if your component (e.g. custom step) embeds another one (e.g. instance of a `Processor`) it should call its `prepareBuild()` method, too. The purpose of this method is mutatation of builders, for example adding extra steps to the scenario or registering handlers elsewhere. We'll see how to do that later on.

In the second phase `build(...)` is called. At this point the builders tree must not be mutated further as some components might be already built and the change could not be reflected; this method should validate the builder and return the component.

Mutations of the scenario can be position dependent (e.g. adding one step before or after current step). Each builder that needs to know its position therefore must override two methods, `setLocator(Locator)` and `copy(Locator)`. The former usually just sets a field storing the `Locator` and delegates the call to embedded components, the latter performs a deep copy of this builder, storing the provided `Locator` in the copy.

If you want to add some extra steps elsewhere in the scenario, you can implement the `prepareBuild()` method this way:

{{< card code=true header="**Java**" lang="Java" >}}
private Locator locator;

/* ... */

@Override
public void prepareBuild() {
   // insert custom step before this step in this sequence
   locator.sequence().insertBefore(locator)
      .step(new CustomStep(42));

   // insert custom step after this step in this sequence, using a builder
   locator.sequence().insertAfter(locator)
      .stepBuilder(new CustomStep.Builder().foo(42));

   // insert a new sequence 'foo' with single custom step to the scenario
   locator.scenario().sequence("foo")
      .step(new CustomStep(42));
}
{{< /card >}}

Note that when you insert any builders in the `prepareBuild()` methods it is possible that its prepare phase won't be executed (if inserting to already prepared sequence), though it *might be* (if inserting to sequence that is yet to be prepared). It's up to the calling code to make sure that the inserted component will be prepared.

As mentioned above, components often embed other components. To service-load a component, e.g. an `Action` you define these methods in the builder:

{{< card code=true header="**Java**" lang="Java" >}}
// This method is not different from regular fluent setter
// and it's useful for programmatic configuration.
public Builder onEvent(Action.Builder onEvent) {
    // you can ensure here that this is called only once
    this.onEvent = onEvent;
    return this;
}

// This is the service-loading method.
public ServiceLoadedBuilderProvider<Action.Builder> onEvent() {
    return new ServiceLoadedBuilderProvider<>(Action.Builder.class, locator, this::onEvent);
}
{{< /card >}}

The parser instantiates concrete implementation of the `Action.Builder`, calls its setters and then passes the builder to the consumer method referenced as `this::onEvent`. Note that the call to `ServiceLoadedBuilderProvider` constructor requires a `Locator` parameter, as the embedded Action can mutate the scenario later on.
