Using TwitterServer from Java
=============================

It's possible to use :doc:`TwitterServer's features <Features>` in Java by
extending the abstract class `AbstractTwitterServer` (a Java-friendly version
of the `TwitterServer` trait).

In the following example, we define a `JavaServer` that simply prints a log
message while initializing.

.. includecode:: code/JavaServer.java
   :language: java

Main Method
-----------

The primary difference between Scala's `TwitterServer` and Java's `AbstractTwitterServer`
is that in Java you have to explicitly define a `main` method in a separate class (i.e.,
an inner class `JavaServer$Main`) and manually launch the server as shown below.

.. includecode:: code/JavaServer.java#main
   :language: java

Lifecycle Methods
-----------------

In order to make the API more Java-friendly, the `AbstractTwitterServer` provides
methods that handle application lifecycle. So, you can easily override them in the
concrete instance instead of calling the high-order functions `premain`, `postmain`,
etc., which is still possible but quite painful to do in Java. For example, the
following code shows how to override the `onInit` method, which will be executed
on application initialization.

.. includecode:: code/JavaServer.java#oninit
   :language: java

Here is the full list of the lifecycle methods provided by `AbstractTwitterServer`:

- `onInit` - called prior to application initialization
- `preMain` - called before the `main` method
- `postMain` - called after the `main` method
- `onExit` - called prior to application exiting

Within an `AbstractTwitterSever`, it's also possible to define a custom `main` method
that will be called automatically after the `preMain` method and before the `postMain`
method. To do so, simply override an instance method `void main() throws Throwable`
with no args.

Reusing Scala Traits
--------------------

`AbstractTwitterServer`'s behaviour may be customized by mixing in Scala traits.
The usage pattern is similar to how `TwitterServer` does it but with one restriction:
it's not possible to define/use Scala traits in Java, so we have to do it Scala.

To reuse Scala traits in Java (via trait mixing), in a separate Scala file define
an `abstract` Scala class (e.g., `StackServer`) that extends all the traits you
want to reuse.

.. includecode:: code/StackServer.scala
   :language: scala

Now you can extend this abstract class in Java and inherit the behaviour defined
by Scala traits.

.. includecode:: code/JavaStackServer.java
   :language: java
