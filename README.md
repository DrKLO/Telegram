### Any **libtgvoip** or tgcalls **legacy\InstanceImplLegacy.cpp** voice voip call to Telegram Android 8.7.4 stuck on "exchnageinge keys " and disconnected after 20 secons**

### Very similar to Telegram Android 8.7.4 bug
### Any other clinets works good

### Steps to reproduce
For example:
**### Use Telegram X and call to Telegram Android 8.7.4.**

### **Can it be fixed in Telegram Android?**

Logs
```
update data saving mode, config 0, enabled 0, reqd by peer 0
Set remote endpoints, allowP2P=1, connectionMaxLayer=92
Adding endpoint: 91.108.13.3:599 91.108.17.40:599 91.108.9.20:599
Starting voip controller
trying bind to port 19585
Bound to local UDP port 19585
Receive thread starting
before create audio io
AEC: 1 NS: 1 AGC: 1
Socket 24 is ready to send
Send udp pings
Sending UDP ping
Audio initialization took 0.000446 seconds
Call state changed to 2
=== send thread exiting ===
Received UDP ping reply
Sending UDP ping
Received UDP ping reply
Received init ack
peer version from init ack 9
jitter: set min packet count 2
Sending public endpoints request to 91.108.9.20:599, 91.108.13.3:599, 91.108.17.40:599
Send udp pings
Sending UDP ping
Received UDP ping
Received init ack
Received UDP ping reply
Send udp pings
Sending UDP ping
Received UDP ping
Received init ack
Received UDP ping reply
Received init ack
Call state changed to 3
UDP ping reply count: 4.00
```

![image](https://user-images.githubusercontent.com/95701997/170686050-b971cea1-c8fa-4d4e-8cb6-14bc4fd3833b.png)


	sometimes it apears wtih 4 emoji on top

![image](https://user-images.githubusercontent.com/95701997/170875064-2e265885-79f9-41e3-bd2b-0cad2b5dca03.png)


InstanceImplLegacy.cpp - libtgvoip STOPPED WORK in 8.7.4
