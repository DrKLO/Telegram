This directory has the following layout:
- base/task/: public APIs for posting tasks and managing task queues.
- base/task/thread_pool/: implementation of the ThreadPool.
- base/task/sequence_manager/: implementation of the SequenceManager.

Apart from embedders explicitly managing a ThreadPoolInstance and/or
SequenceManager instance(s) for their process/threads, the vast majority of
users should only need APIs in base/task/.

Documentation:

* [Threading and tasks](/docs/threading_and_tasks.md)
* [Callbacks](/docs/callback.md)
* [Vision for future API changes](https://docs.google.com/document/d/1pySz2xeJ6kLlbzDnS2jqAC1F8T_6pLEV8pgaMfURXAw/edit)
