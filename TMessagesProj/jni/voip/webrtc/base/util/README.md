# What is this
Contains a written down set of principles and other information on //base/util.
Please add to it!

## About //base/util:

This directory is meant to house common utilities that can be shared across the
whole Chromium codebase. //base is similar, but due to the way //base grew over
time, it has not been well organized to allow for fine-grained ownership. Also,
there is a mixture of commonly useful utility code and extremely subtle code
with performance and security implications. The combination of the two lead to
a small number of //base OWNERS enforcing a very high quality bar over a
diverse bag of code. The goal of //base/util is to avoid both these issues.
Over time, functionality in //base proper will sorted into //base/util and
one of two things will occur:

   1. //base becomes empty and subdirs in //base/util get moved up to //base
   2. A distinct "core" //base module is distilled out.

We will iterate on this purpose as we add more functionality into this
directory.

## Structure of //base/util:

  * No files other than README.md, and OWNERS are allowed in the top-level
    directory.
  * Subdirectories should be named for a class of functionality; it should
    be relatively easy to browse the directory and find a utility.
  * There is no top-level BUILD.gn. Subdirectories will have their own and
    are allowed to reference other subdirectories.

## Responsibilities of //base/util/OWNERS

  * Creating new categories.
  * Helping dedupe and organize code being added.
  * Ensuring the directory is somewhat browseable.

It is specifically NOT the OWNERS job to gatekeep what is a "good pattern"
for Chromium code. Obviously they can still object, but the objection
should be considered similar to any other Chromium dev objection.

There will be cases when a utility is deemed to be more negative than
positive after it has been landed. The //base/util/OWNERS may aide in
coordinating and tracking removal, but responsibility for actually
deleting the code and its uses falls to the *category* OWNERS.

## Guidelines for adding code to //base/util/{category}

  * You will be added to an OWNERS file under //base/util/category and be
    responsible for maintaining your addition.
  * A //base/util/OWNER must approve of the location of your code.
  * Code must be needed in at least 2 places in Chrome and have no "higher
    layered" directory (eg "//ui/base, //media/base, etc.), that could
    facilitate sharing.
  * Code must have unittests, and coverage should be > 95%.
  * Code must have clear usage documentation for all the APIs.
  * Public APIs must be in `::util` namespace. All implementation details
    should be in `::util::internal`. Macros, which are not namespaceable,
    are permitted but should be used sparingly and cause a small pang of guilt.
  * Implementation and expected usage must be understandable by another OWNER
    in your subdirectory; if creating a new subdirectory you must find a
    co-OWNER.
  * New subdirectories should have their own BUILD.gn files.


## Why not just put new utilities in //base/FooDir?

At some point, //base/util directories could get moved back into //base.
Until then, //base/util will

  1. make a distinct separation between "common useful utility code" from the
     "extremely subtle code with performance and security implications" that //base
     also houses.
  2. remove //base/OWNERS as a bottleneck.

The boundary is still a work-in-progress, but should clear itself up with time
after some of the more obviously "utility-esque" classes are moved.


## How does this differ from //components
Both //components and //base/util contain subdirectories that are (a) intended
for reuse. In addition, //components imposes no global layering in Chromium, so
a subdirectory placed in //components can be used from most-to-all layers in the
codebase, subject to the dependencies that that subdirectory itself holds.

In spite of these similarities, there are *conceptual* differences: //components
contains things are closer to full features or subsystems (eg autofill, heap
profiler, cloud devices, visited link tracker) that are not really intended for
large scale reuse.

There is some overlap and at some point it will become a judgment call, but
in general, //components are a better fit if the code in question is a feature,
module, or subsystem.  //base/util is better if it is a more narrow construct
such as a data structure, coding primitive, etc.


## Why not the "Rule-of-3?"

The [Rule-of-3](https://en.wikipedia.org/wiki/Rule_of_three_%28computer_programming%29)
is a simple guidance on when it makes sense to extract common functionality out
versus duplicating it. It has commonly been used in //base as a way of measuring
"how general" is this functionality.

Unfortunately, there are reasons for wanting to share code beyond just
cleanliness. For example, if you need to guarantee exact behavior across
two modules, duplication is not proper even if there will ever only be 2
users.

Furthermore, there is a chicken-and-egg problem that prevents incremental
adoption of a utility. For example, someone introduces ThingerDoer in
//foo/bar. Later, ThingerDoer is wanted in //foo/qux, but this still fails
the rule-of-3 test, MyDoer is created. When //foo/waldo wants it, it's
completely a game of chance whether or not the CL author manages to find
both classes, wants to spend the time to determine if they've diverged,
and then tries to abstract it.

As such, the rule-of-3 runs contrary to the goal of sharing that
//base/util is designed to facilitate.

## Tips
  * if doing a mass-move of code, look at `//tools/git/mass-rename.py`
