<!-- go/cmark -->
<!--* freshness: {owner: 'hta' reviewed: '2021-04-12'} *-->

# The WebRTC API

The public API of the WebRTC library consists of the api/ directory and
its subdirectories. No other files should be depended on by webrtc users.

Before starting to code against the API, it is important to understand
some basic concepts, such as:

* Memory management, including webrtc's reference counted objects
* [Thread management](threading_design.md)

## Using WebRTC through the PeerConnection class

The
[PeerConnectionInterface](https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/api/peer_connection_interface.h?q=webrtc::PeerConnectionInterface)
class is the recommended way to use the WebRTC library.

It is closely modeled after the Javascript API documented in the [WebRTC
specification](https://w3c.github.io/webrtc-pc/).

PeerConnections are created using the [PeerConnectionFactoryInterface](https://source.chromium.org/search?q=webrtc::PeerConnectionFactoryInterface).

There are two levels of customization available:

*   Pass a PeerConnectionFactoryDependencies object to the function that creates
    a PeerConnectionFactory. This object defines factories for a lot of internal
    objects inside the PeerConnection, so that users can override them.
    All PeerConnections using this interface will have the same options.
*   Pass a PeerConnectionInterface::RTCConfiguration object to the
    CreatePeerConnectionOrError() function on the
    PeerConnectionFactoryInterface. These customizations will apply only to a
    single PeerConnection.

Most functions on the PeerConnection interface are asynchronous, and take a
callback that is executed when the function is finished. The callbacks are
mostly called on the thread that is passed as the "signaling thread" field of
the PeerConnectionFactoryDependencies, or the thread that called
PeerConnectionFactory::CreatePeerConnectionOrError() if no thread is given.

See each class' module documentation for details.

## Using WebRTC components without the PeerConnection class

This needs to be done carefully, and in consultation with the WebRTC team. There
are non-obvious dependencies between many of the components.



