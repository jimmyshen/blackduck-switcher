The Blackduck Switcher is project that I worked on over a 4-day period (2017 Thanksgiving holiday). It consists of the following parts:

* A Python GTK "server" application that runs on the host computer. It advertises a Bluetooth service that a single client can connect to over RFCOMM.
* An Android (Jelly Bean or higher) client application I targeted for my Samsung Galaxy Tab2.
* An RPC protocol over Bluetooth RFCOMM using Msgpack for serialization (TODO: asynchronous server->client notification of task changes).

Here are the various libraries and packages I relied on to build this:

- Pybluez (https://karulis.github.io/pybluez/) for Python Bluetooth communication
- Msgpack (https://msgpack.org) for on-wire serialization
- Jackson (http://wiki.fasterxml.com/JacksonHome) for Java serialization
- AutoValue (https://github.com/google/auto/tree/master/value) for easy Java value types
- AutoValue Parcelable extension (https://github.com/rharter/auto-value-parcel) so that my value types play well with Android
