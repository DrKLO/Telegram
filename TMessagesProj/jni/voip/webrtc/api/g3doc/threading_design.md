<!-- go/cmark -->
<!--* freshness: {owner: 'hta' reviewed: '2021-04-12'} *-->

# API Threading Design considerations

The header files in this directory form the API to the WebRTC library
that is intended for client applications' use.

This API is designed to be used on top of a multithreaded runtime.

The public API functions are designed to be called from a single thread*
(the "client thread"), and can do internal dispatching to the thread
where activity needs to happen. Those threads can be passed in by the
client, typically as arguments to factory constructors, or they can be
created by the library if factory constructors that don't take threads
are used.

Many of the functions are designed to be used in an asynchronous manner,
where a function is called to initiate an activity, and a callback will
be called when the activity is completed, or a handler function will
be called on an observer object when interesting events happen.

Note: Often, even functions that look like simple functions (such as
information query functions) will need to jump between threads to perform
their function - which means that things may happen on other threads
between calls; writing "increment(x); increment(x)" is not a safe
way to increment X by exactly two, since the increment function may have
jumped to another thread that already had a queue of things to handle,
causing large amounts of other activity to have intervened between
the two calls.

(*) The term "thread" is used here to denote any construct that guarantees
sequential execution - other names for such constructs are task runners
and sequenced task queues.

## Client threads and callbacks

At the moment, the API does not give any guarantee on which thread* the
callbacks and events are called on. So it's best to write all callback
and event handlers like this (pseudocode):
```
void ObserverClass::Handler(event) {
  if (!called_on_client_thread()) {
    dispatch_to_client_thread(bind(handler(event)));
    return;
  }
  // Process event, we're now on the right thread
}
```
In the future, the implementation may change to always call the callbacks
and event handlers on the client thread.

## Implementation considerations

The C++ classes that are part of the public API are also used to derive
classes that form part of the implementation.

This should not directly concern users of the API, but may matter if one
wants to look at how the WebRTC library is implemented, or for legacy code
that directly accesses internal APIs.

Many APIs are defined in terms of a "proxy object", which will do a blocking
dispatch of the function to another thread, and an "implementation object"
which will do the actual
work, but can only be created, invoked and destroyed on its "home thread".

Usually, the classes are named "xxxInterface" (in api/), "xxxProxy" and
"xxx" (not in api/). WebRTC users should only need to depend on the files
in api/. In many cases, the "xxxProxy" and "xxx" classes are subclasses
of "xxxInterface", but this property is an implementation feature only,
and should not be relied upon.

The threading properties of these internal APIs are NOT documented in
this note, and need to be understood by inspecting those classes.
