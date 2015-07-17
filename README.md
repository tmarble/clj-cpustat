# clj-cpustat

A cross-platform cpu statistics gathering library.

Add this dependency to your project.
````
[net.info9/clj-cpustat "0.2.0"]
````

Require it as:
````
(ns playground
  (:require [cpustat.core :as cpustat]))
````

And have fun:
````
=> (cpustat/info)
...
=> (cpustat/start-cpustat 5 #(println %))
...
=> (cpustat/stop-cpustat)
````

## API Documentation

Check out the the [API docs here](http://tmarble.github.io/clj-cpustat/doc/)

## Platform Requirements

* Linux
  * Please ensure you have **mpstat** installed (in the [**systat**](https://packages.debian.org/jessie/sysstat) package)
* Mac
  * Currently the system tool **iostat** is used to give *average* CPU usage (see below for going further...)
* Windows
  * Alas Windows is currently not supported :(

## Notes

There are many, many features not implemented.

The big challenge is that several solutions depend on
native, third party libraries and often required elevated permissions.
It is essential that any proposals for elaborating performance
counters (on non Linux platforms) have a clear install/setup
procedure and do not expose unnecessary security risks.

Code that interacts with the system can sometimes
lack a certain elegance... Suggestions on improvements
are welcome!

* Per processor stats for Mac OS X
  * Requires [third party libraries](http://superuser.com/questions/27954/command-to-get-usage-per-cpu-core) and elevated privilege
* Stats for Windows
  * There doesn't seem to be even a basic CPU performance logging tool which
    doesn't require GUI configuration and/or elevated privilege. Here are
    some (maybe) helpful references:
  * https://technet.microsoft.com/en-us/library/cc788038.aspx
  * http://www.instantfundas.com/2012/03/how-to-record-cpu-and-memory-usage-over.html
  * http://stackoverflow.com/questions/9097067/get-cpu-usage-from-windows-command-prompt
* TODO: Target ClojureScript
  * The code should be written using *.cljc features
  * And supported on popular JavaScript server implementations (e.g. node)

## Copyright and license

Copyright © 2015 Tom Marble

Licensed under the [MIT](http://opensource.org/licenses/MIT) [LICENSE](LICENSE)
